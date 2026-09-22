package com.mcclabs.mook.feature.discover

import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.domain.model.Language

data class DiscoverUiState(
    val profiles: List<DiscoverProfile> = emptyList(),

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
    /** True once the user hits the free limit; the grid shows the upgrade sheet. */
    val showLimitSheet: Boolean = false,
    /** Keeps rapid taps from making the locally undoable pass diverge from the server's last pass. */
    val isSwipeInFlight: Boolean = false,
    /** The locally displayed last pass is eligible to be restored through `rewind`. */
    val hasRewindablePass: Boolean = false,
    val isRewinding: Boolean = false,
    val isBoosting: Boolean = false,
    /** Set after a successful activation so the user receives an immediate confirmation. */
    val boostUntilMillis: Long? = null,
) {
    /** Paid plan limits apply immediately rather than treating Economy/Standard as Free. */
    val canSwipe: Boolean
        get() = !isSwipeInFlight && (entitlement.limits.dailyLikes == null || swipesUsedToday < entitlement.limits.dailyLikes)

    val canUseRewind: Boolean
        get() = hasRewindablePass && !isRewinding && entitlement.limits.rewindsPerDay != 0

    val canUseBoost: Boolean
        get() = !isBoosting && entitlement.limits.boostsPerMonth > 0

    /** True when the grid has nothing to show and is not mid-load — drives the empty state. */
    val isEmpty: Boolean
        get() = profiles.isEmpty() && !isLoading && !isRefreshing && error == null
}
