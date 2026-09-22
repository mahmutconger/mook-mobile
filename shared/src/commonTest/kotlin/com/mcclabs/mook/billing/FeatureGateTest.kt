package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.AdFrequencyPolicy
import com.mcclabs.mook.domain.billing.AdPlacement
import com.mcclabs.mook.domain.billing.DefaultPlanLimits
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.Feature
import com.mcclabs.mook.domain.billing.FeatureGate
import com.mcclabs.mook.domain.billing.GateDecision
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.billing.UsageSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeatureGateTest {
    private val gate = FeatureGate()

    @Test
    fun freeLikeAtLimitOffersRewardedAd() {
        val decision = gate.decide(
            Feature.LIKE,
            EntitlementState(limits = DefaultPlanLimits.byTier.getValue(Tier.FREE)),
            UsageSnapshot(likes = 10),
            showInterstitial = false,
        )
        val limit = assertIs<GateDecision.LimitReached>(decision)
        assertEquals(AdPlacement.LIKES_REWARDED, limit.rewarded)
        assertEquals(Tier.STANDARD, limit.upgradeTo)
    }

    @Test
    fun premiumLikeIsNeverClientLimited() {
        val decision = gate.decide(
            Feature.LIKE,
            EntitlementState(tier = Tier.PREMIUM, limits = DefaultPlanLimits.byTier.getValue(Tier.PREMIUM)),
            UsageSnapshot(likes = 10_000),
            showInterstitial = true,
        )
        assertEquals(GateDecision.Allowed, decision)
    }

    @Test
    fun interstitialWaitsForOnboardingAndActionCadence() {
        val policy = AdFrequencyPolicy()
        assertTrue(!policy.canShow(100_000, 50_000, 10, 3, null, 0, 0))
        assertTrue(!policy.canShow(100_000_000, 0, 4, 3, null, 0, 0))
        assertTrue(policy.canShow(100_000_000, 0, 5, 3, null, 0, 0))
    }
}
