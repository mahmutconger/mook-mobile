package com.mcclabs.mook.domain.billing

import kotlinx.coroutines.flow.StateFlow

/** Server-aligned subscription tiers. The order is intentional and used for entitlement fallback. */
enum class Tier { FREE, ECONOMY, STANDARD, PREMIUM }

data class PlanLimits(
    val dailyLikes: Int?,
    val dailyMessages: Int?,
    val dailyNewChats: Int?,
    val roomSlots: Int?,
    val roomSwitchesPerDay: Int?,
    val showsAds: Boolean,
    val freeRoam: Boolean,
    val incognito: Boolean,
    val likedMeUnlocksPerDay: Int?,
    val rewindsPerDay: Int?,
    val boostsPerMonth: Int,
)

/** Mirrors `config/plans` and remains safe when Remote Config/Firestore is unavailable. */
object DefaultPlanLimits {
    val byTier = mapOf(
        Tier.FREE to PlanLimits(10, 50, 3, 1, 1, true, false, false, 0, 0, 0),
        Tier.ECONOMY to PlanLimits(30, 200, 10, 3, 3, false, false, false, 0, 0, 0),
        Tier.STANDARD to PlanLimits(100, null, null, 5, null, false, false, false, 5, 3, 1),
        Tier.PREMIUM to PlanLimits(null, null, null, null, null, false, true, true, null, null, 4),
    )
}

data class EntitlementState(
    val tier: Tier = Tier.FREE,
    val expiresAtMillis: Long? = null,
    val willRenew: Boolean = false,
    val inTrial: Boolean = false,
    val limits: PlanLimits = DefaultPlanLimits.byTier.getValue(Tier.FREE),
)

data class PlanPackage(
    val identifier: String,
    val tier: Tier,
    val period: Period,
    val localizedPrice: String,
    val isRecommended: Boolean = false,
)

enum class Period { MONTHLY, YEARLY }

data class PaywallOffer(val packages: List<PlanPackage>)

sealed interface PurchaseOutcome {
    data object Success : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data object Pending : PurchaseOutcome
    data class Error(val message: String) : PurchaseOutcome
}

/**
 * Provider-neutral billing boundary. Android maps this to RevenueCat; iOS intentionally uses
 * the no-op implementation until the iOS commerce launch is explicitly scoped.
 */
interface SubscriptionRepository {
    val state: StateFlow<EntitlementState>
    suspend fun offerings(): Result<PaywallOffer>
    suspend fun purchase(plan: PlanPackage): PurchaseOutcome
    suspend fun restore(): PurchaseOutcome
    suspend fun logIn(uid: String)
    suspend fun logOut()
    suspend fun refresh()
}
