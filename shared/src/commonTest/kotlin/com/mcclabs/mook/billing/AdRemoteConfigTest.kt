package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.AdRemoteConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdRemoteConfigTest {

    private fun parse(booleans: Map<String, Boolean> = emptyMap(), longs: Map<String, Long> = emptyMap()) =
        AdRemoteConfig.fromRemote(boolean = { booleans[it] }, long = { longs[it] })

    @Test
    fun missingValuesFallBackToCurrentProductRules() {
        assertEquals(AdRemoteConfig.DEFAULT, parse())
        assertTrue(AdRemoteConfig.DEFAULT.isInterstitialActive)
        assertTrue(AdRemoteConfig.DEFAULT.isNativeActive)
        assertTrue(AdRemoteConfig.DEFAULT.isRewardedActive)
    }

    @Test
    fun globalKillSwitchDisablesEveryFormat() {
        val config = parse(booleans = mapOf(AdRemoteConfig.Keys.ADS_ENABLED to false))
        assertFalse(config.isInterstitialActive)
        assertFalse(config.isNativeActive)
        assertFalse(config.isRewardedActive)
        assertFalse(config.allowsRewardedAd(shownToday = 0))
    }

    @Test
    fun rewardedFrequencyLimitIsEnforcedAndZeroDisables() {
        val config = parse(longs = mapOf(AdRemoteConfig.Keys.REWARDED_AD_FREQUENCY_LIMIT to 2))
        assertTrue(config.allowsRewardedAd(shownToday = 1))
        assertFalse(config.allowsRewardedAd(shownToday = 2))
        assertFalse(parse(longs = mapOf(AdRemoteConfig.Keys.REWARDED_AD_FREQUENCY_LIMIT to 0)).isRewardedActive)
    }

    @Test
    fun invalidConsoleValuesAreClampedToSafeRanges() {
        val config = parse(
            longs = mapOf(
                AdRemoteConfig.Keys.INTERSTITIAL_MIN_INTERVAL_SECONDS to 1,
                AdRemoteConfig.Keys.INTERSTITIAL_DAILY_CAP to 10_000,
                AdRemoteConfig.Keys.INTERSTITIAL_ACTIONS_BETWEEN_ADS to 0,
                AdRemoteConfig.Keys.NATIVE_AD_CARD_INTERVAL to 1,
                AdRemoteConfig.Keys.REWARDED_AD_FREQUENCY_LIMIT to -5,
            ),
        )
        assertEquals(30, config.interstitialMinIntervalSeconds)
        assertEquals(50, config.interstitialDailyCap)
        assertEquals(1, config.interstitialActionsBetweenAds)
        assertEquals(4, config.nativeAdCardInterval)
        assertEquals(0, config.rewardedAdFrequencyLimit)
    }

    @Test
    fun interstitialPolicyUsesRemoteCadence() {
        val config = parse(longs = mapOf(AdRemoteConfig.Keys.INTERSTITIAL_ACTIONS_BETWEEN_ADS to 5))
        val policy = config.interstitialPolicy()
        val dayAgo = 0L
        val now = 2L * 24 * 60 * 60 * 1000
        assertFalse(policy.canShow(now, dayAgo, likesEver = 10, actionsSinceLastAd = 4, lastAdAtMillis = null, sessionAds = 0, dailyAds = 0))
        assertTrue(policy.canShow(now, dayAgo, likesEver = 10, actionsSinceLastAd = 5, lastAdAtMillis = null, sessionAds = 0, dailyAds = 0))
    }

    @Test
    fun defaultsListCoversEveryKey() {
        val keys = AdRemoteConfig.DEFAULTS.map { it.first }.toSet()
        assertEquals(10, keys.size)
        assertTrue(AdRemoteConfig.Keys.ADS_ENABLED in keys)
        assertTrue(AdRemoteConfig.Keys.REWARDED_AD_FREQUENCY_LIMIT in keys)
    }
}
