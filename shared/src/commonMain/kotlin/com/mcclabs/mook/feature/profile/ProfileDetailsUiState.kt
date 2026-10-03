package com.mcclabs.mook.feature.profile

import com.mcclabs.mook.domain.billing.BillingConfig
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.model.DiscoverProfile

data class ProfileDetailsUiState(
    val profile: DiscoverProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** True when the viewed profile is the signed-in user's own — drives Settings vs Report UI. */
    val isOwnProfile: Boolean = false,
    /** Whether the report-profile dialog is visible. */
    val showReportDialog: Boolean = false,
    /** The report reason the user has selected, before submitting. */
    val selectedReportReason: String? = null,
    /** Whether the block-user confirmation dialog is visible. */
    val showBlockConfirmDialog: Boolean = false,
    /** True if there is a mutual match with this profile. Used to show/hide the message button. */
    val isMatched: Boolean = false,

    // ── Like / pass ─────────────────────────────────────────────────────────
    /**
     * True once the user has liked or passed on this profile in this session, or had already
     * done so before opening it. Replaces the action row with a quiet confirmation so the
     * same person cannot be liked twice.
     */
    val isActedOn: Boolean = false,
    /** A like or pass write is in flight; both buttons are disabled meanwhile. */
    val isActionInFlight: Boolean = false,
    /** Live entitlement; Economy and Standard must not be treated as the Free tier. */
    val entitlement: EntitlementState = EntitlementState(),
    /** Likes used today, read from the server when the profile loads. */
    val swipesUsedToday: Int = 0,
    /** Verified bonus likes used today, read from the same server usage record. */
    val rewardedLikesToday: Int = 0,
    /** Lifetime successful likes, used for the interstitial onboarding grace period. */
    val likesEver: Int = 0,
    /**
     * [FeatureGate][com.mcclabs.mook.domain.billing.FeatureGate] bir `LimitReached` kararı
     * ürettiğinde set edilir; Limit Sheet açıktır (bkz. Gereksinim 1.5). `null` iken kapalıdır.
     */
    val limitReason: LimitReason? = null,
    /** [limitReason] bir adil kullanım tavanıysa (yükseltme çözmez) `true` — bkz. Gereksinim 1.5. */
    val limitIsFairUseCap: Boolean = false,
) {
    /** Geriye dönük uyumluluk: mevcut arayüz kodu bu boolean'ı okumaya devam edebilir. */
    val showLimitDialog: Boolean
        get() = limitReason != null

    /** Uses the active tier's limit; a null plan limit is subject only to the server fair-use cap. */
    val effectiveDailyLikeLimit: Int?
        get() = BillingConfig.effectiveDailyLikeLimit(entitlement.limits, entitlement.tier, rewardedLikesToday)

    // Gereksinim 2.5: Economy artık Free ile aynı ödüllü-reklam bonus hakkına sahip —
    // bkz. BillingConfig.canEarnRewardedLikeBonus KDoc'u.
    val canEarnRewardedLike: Boolean
        get() = BillingConfig.canEarnRewardedLikeBonus(entitlement.tier, rewardedLikesToday)

    val canSwipe: Boolean
        get() {
            val limit = effectiveDailyLikeLimit
            return limit == null || swipesUsedToday < limit
        }

    /** Whether to offer the like/pass row: someone else's profile, not yet acted on. */
    val canAct: Boolean
        get() = !isOwnProfile && !isActedOn && !isMatched
}
