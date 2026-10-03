package com.mcclabs.mook.ads

import com.mcclabs.mook.domain.billing.AdConfigRepository
import com.mcclabs.mook.domain.billing.RewardType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mcclabs.mook.domain.billing.LikeInterstitialGateway
import com.mcclabs.mook.domain.analytics.AnalyticsRepository

@Composable
actual fun RewardedAdButton(rewardType: RewardType, enabled: Boolean, onRewardConfirmed: () -> Unit) = Unit

@Composable
actual fun AdPrivacyOptionsEntry() = Unit


@Composable
actual fun NativeAdFeedCard(isFree: Boolean, modifier: Modifier, placement: String) = Unit

/** iOS ticari akışı henüz devreye alınmadığından no-op sağlayıcı. */
actual fun createLikeInterstitialGateway(
    analyticsRepository: AnalyticsRepository,
    adConfigRepository: AdConfigRepository,
): LikeInterstitialGateway = object : LikeInterstitialGateway {
    override suspend fun attemptShowAfterTransition(isFree: Boolean, likesEver: Int) = LikeInterstitialAttempt()
    override fun recordAction(isFree: Boolean, attempt: LikeInterstitialAttempt, succeeded: Boolean) = Unit
    override suspend fun showProfileVisitInterstitial(isFree: Boolean) = false
}
actual fun onDiscoveryAdsVisible(isFree: Boolean) = Unit
actual fun onDiscoveryAdsHidden() = Unit
