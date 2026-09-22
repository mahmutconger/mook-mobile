package com.mcclabs.mook.domain.billing

enum class Feature { LIKE, MESSAGE, NEW_CHAT, ROOM_SWITCH, PROFILE_VISIT, LIKED_ME, REWIND, BOOST }
enum class AdPlacement { LIKE_INTERSTITIAL, PROFILE_VISIT_INTERSTITIAL, LIKES_REWARDED, ROOM_REWARDED, LIKED_ME_REWARDED }

data class UsageSnapshot(
    val likes: Int = 0,
    val messages: Int = 0,
    val newChats: Int = 0,
    val roomSwitches: Int = 0,
    val likedMeUnlocks: Int = 0,
    val rewinds: Int = 0,
    val boosts: Int = 0,
)

sealed interface GateDecision {
    data object Allowed : GateDecision
    data class AdFirst(val placement: AdPlacement) : GateDecision
    data class LimitReached(val rewarded: AdPlacement?, val upgradeTo: Tier) : GateDecision
}

/** Client-side UX prediction only; Cloud Functions remain the authoritative enforcement point. */
class FeatureGate {
    fun decide(feature: Feature, state: EntitlementState, usage: UsageSnapshot, showInterstitial: Boolean): GateDecision {
        val limits = state.limits
        val exhausted = when (feature) {
            Feature.LIKE -> limits.dailyLikes != null && usage.likes >= limits.dailyLikes
            Feature.MESSAGE -> limits.dailyMessages != null && usage.messages >= limits.dailyMessages
            Feature.NEW_CHAT -> limits.dailyNewChats != null && usage.newChats >= limits.dailyNewChats
            Feature.ROOM_SWITCH -> limits.roomSwitchesPerDay != null && usage.roomSwitches >= limits.roomSwitchesPerDay
            Feature.LIKED_ME -> limits.likedMeUnlocksPerDay != null && usage.likedMeUnlocks >= limits.likedMeUnlocksPerDay
            Feature.REWIND -> limits.rewindsPerDay != null && usage.rewinds >= limits.rewindsPerDay
            Feature.BOOST -> limits.boostsPerMonth == 0 || usage.boosts >= limits.boostsPerMonth
            Feature.PROFILE_VISIT -> false
        }
        if (exhausted) {
            val rewarded = when (feature) {
                Feature.LIKE -> AdPlacement.LIKES_REWARDED
                Feature.ROOM_SWITCH -> AdPlacement.ROOM_REWARDED
                Feature.LIKED_ME -> AdPlacement.LIKED_ME_REWARDED
                else -> null
            }
            return GateDecision.LimitReached(rewarded, if (state.tier == Tier.FREE) Tier.STANDARD else Tier.PREMIUM)
        }
        if (state.limits.showsAds && showInterstitial) {
            return when (feature) {
                Feature.LIKE -> GateDecision.AdFirst(AdPlacement.LIKE_INTERSTITIAL)
                Feature.PROFILE_VISIT -> GateDecision.AdFirst(AdPlacement.PROFILE_VISIT_INTERSTITIAL)
                else -> GateDecision.Allowed
            }
        }
        return GateDecision.Allowed
    }
}
