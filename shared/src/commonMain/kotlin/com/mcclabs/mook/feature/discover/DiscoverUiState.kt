package com.mcclabs.mook.feature.discover

import com.mcclabs.mook.domain.billing.BillingConfig
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.repository.BoostSummary

data class DiscoverUiState(
    val profiles: List<DiscoverProfile> = emptyList(),
    /** IDs liked directly from the grid; kept visible until refresh/re-entry. */
    val likedProfileIds: Set<String> = emptySet(),

    /** First page (or a filter change) is loading — the grid is replaced by a spinner. */
    val isLoading: Boolean = false,
    /** A further page is loading — a footer spinner appears under the grid. */
    val isLoadingMore: Boolean = false,
    /** Pull-to-refresh in flight; the existing grid stays on screen underneath. */
    val isRefreshing: Boolean = false,
    /** No further pages for the current filters; stops the grid from asking again. */
    val endReached: Boolean = false,
    val error: String? = null,

    // ── Filter chips ────────────────────────────────────────────────────────
    /** The room the user is currently in, or `null` for the language-independent room. */
    val roomLanguage: Language? = null,
    /** True when the active room is the "everyone" room, which has no [Language] entry. */
    val isLanguageIndependentRoom: Boolean = false,
    val ageRangeStart: Int = 18,
    val ageRangeEnd: Int = 35,
    /** Whether the age-range bottom sheet is open. */
    val showAgeSheet: Boolean = false,

    // Safety requirement for Google Play (Reporting & Blocking)
    val showReportDialog: Boolean = false,
    val selectedReportReason: String? = null,
    val showBlockConfirmDialog: Boolean = false,
    val selectedProfileToReportOrBlock: String? = null,

    /** Whether the user has seen the "this person liked you" overlay. */
    val hasSeenLikedMeTutorial: Boolean = false,
    /** The profile the "liked you" overlay is currently explaining, if any. */
    val likedMeTutorialProfileId: String? = null,

    // ── Monetization: free daily like limit ─────────────────────────────────
    /** Live RevenueCat entitlement, used only for fast UX gates; Functions remain authoritative. */
    val entitlement: EntitlementState = EntitlementState(),
    /** Server-reported likes used today, seeded from Firestore on load. */
    val swipesUsedToday: Int = 0,
    /** Verified, server-granted bonus likes used today. */
    val rewardedLikesToday: Int = 0,
    /** Lifetime successful likes, used to enforce the ad onboarding grace period. */
    val likesEver: Int = 0,
    /**
     * [FeatureGate][com.mcclabs.mook.domain.billing.FeatureGate] bir [GateDecision.LimitReached]
     * ürettiğinde set edilir; Limit Sheet açıktır ve hangi metni/aksiyonları göstereceğine bu
     * değere bakarak karar verir (bkz. Gereksinim 1.5). `null` iken sheet kapalıdır.
     */
    val limitReason: LimitReason? = null,
    /** [limitReason] bir adil kullanım tavanıysa (yükseltme çözmez) `true` — bkz. Gereksinim 1.5. */
    val limitIsFairUseCap: Boolean = false,
    /** Keeps rapid taps from making the locally undoable pass diverge from the server's last pass. */
    val isSwipeInFlight: Boolean = false,
    /** The locally displayed last pass is eligible to be restored through `rewind`. */
    val hasRewindablePass: Boolean = false,
    val isRewinding: Boolean = false,
    val isBoosting: Boolean = false,
    /** Set after a successful activation so the user receives an immediate confirmation. */
    val boostUntilMillis: Long? = null,
    /**
     * Gereksinim 2.12 (Faz 4): tek seferlik (one-shot) özet -- 30 dakikalık Boost penceresi
     * kapandığında `DiscoverViewModel.scheduleBoostCompletionCheck` tarafından doldurulur
     * ve "Boost bitti! Profilin X kişiye fazladan gösterildi" özet diyaloğunu tetikler.
     * `onBoostSummaryDismissed()` ile `null`a döner.
     */
    val boostSummary: BoostSummary? = null,
) {
    /** Geriye dönük uyumluluk: mevcut arayüz kodu bu boolean'ı okumaya devam edebilir. */
    val showLimitSheet: Boolean
        get() = limitReason != null

    /** Paid plan limits apply immediately rather than treating Economy/Standard as Free. */
    val effectiveDailyLikeLimit: Int?
        get() = BillingConfig.effectiveDailyLikeLimit(entitlement.limits, entitlement.tier, rewardedLikesToday)

    // Gereksinim 2.5: Economy artık Free ile aynı ödüllü-reklam bonus hakkına sahip —
    // bkz. BillingConfig.canEarnRewardedLikeBonus KDoc'u.
    val canEarnRewardedLike: Boolean
        get() = BillingConfig.canEarnRewardedLikeBonus(entitlement.tier, rewardedLikesToday)

    val canSwipe: Boolean
        get() {
            val limit = effectiveDailyLikeLimit
            return !isSwipeInFlight && (limit == null || swipesUsedToday < limit)
        }

    val canUseRewind: Boolean
        get() = hasRewindablePass && !isRewinding && entitlement.limits.rewindsPerDay != 0

    val canUseBoost: Boolean
        get() = !isBoosting && entitlement.limits.boostsPerMonth > 0

    /** True when the grid has nothing to show and is not mid-load — drives the empty state. */
    val isEmpty: Boolean
        get() = profiles.isEmpty() && !isLoading && !isRefreshing && error == null
}
