package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.SubscriptionChangeDetector
import com.mcclabs.mook.domain.billing.Tier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionChangeDetectorTest {

    private fun state(tier: Tier, resolved: Boolean = true) = EntitlementState(tier = tier, isResolved = resolved)

    @Test
    fun firstResolvedStateIsBaselineOnly() {
        val detector = SubscriptionChangeDetector()
        assertNull(detector.onState("u1", state(Tier.PREMIUM)))
    }

    @Test
    fun tierChangeForSameUserEmitsEvent() {
        val detector = SubscriptionChangeDetector()
        detector.onState("u1", state(Tier.FREE))
        val event = detector.onState("u1", state(Tier.STANDARD))
        assertEquals(Tier.FREE, event?.fromTier)
        assertEquals(Tier.STANDARD, event?.toTier)
        assertNull(detector.onState("u1", state(Tier.STANDARD)))
    }

    @Test
    fun logoutAndAnonymousIdsNeverCountAsCancellation() {
        val detector = SubscriptionChangeDetector()
        detector.onState("u1", state(Tier.PREMIUM))
        assertNull(detector.onState("\$RCAnonymousID:abc", state(Tier.FREE)))
        assertNull(detector.onState(null, state(Tier.FREE)))
        // Farklı kullanıcı girişi yeni taban çizgisidir.
        assertNull(detector.onState("u2", state(Tier.FREE)))
    }

    @Test
    fun unresolvedStatesAreIgnored() {
        val detector = SubscriptionChangeDetector()
        detector.onState("u1", state(Tier.PREMIUM))
        assertNull(detector.onState("u1", state(Tier.FREE, resolved = false)))
        assertEquals(Tier.FREE, detector.onState("u1", state(Tier.FREE))?.toTier)
    }
}
