package com.mcclabs.mook.billing

import com.mcclabs.mook.data.billing.PlanCatalogCodec
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.PlanCatalogMerger
import com.mcclabs.mook.domain.billing.PlanLimitsOverride
import com.mcclabs.mook.domain.billing.ScheduledPlanChange
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.feature.settings.PLAY_SUBSCRIPTIONS_FALLBACK_URL
import com.mcclabs.mook.feature.settings.SubscriptionStatusPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanCatalogAndSettingsStatusTest {

    // ── Dinamik plan sınırları ─────────────────────────────────────────────

    @Test
    fun remoteValuesOverrideBundledAndMissingFieldsKeepBase() {
        val merged = PlanCatalogMerger.merge(
            PlanCatalog.BUNDLED,
            mapOf("free" to PlanLimitsOverride(dailyLikes = 25, showsAds = false)),
        )
        val free = merged.limitsFor(Tier.FREE)
        assertEquals(25, free.dailyLikes)
        assertFalse(free.showsAds)
        assertEquals(PlanCatalog.BUNDLED.limitsFor(Tier.FREE).dailyMessages, free.dailyMessages)
        assertEquals(PlanCatalog.BUNDLED.limitsFor(Tier.PREMIUM), merged.limitsFor(Tier.PREMIUM))
    }

    @Test
    fun explicitNullMeansUnlimitedButNegativeIsRejected() {
        val merged = PlanCatalogMerger.merge(
            PlanCatalog.BUNDLED,
            mapOf("economy" to PlanLimitsOverride(dailyMessages = null, dailyLikes = -5, boostsPerMonth = -1), "gold" to PlanLimitsOverride(dailyLikes = 1)),
        )
        val economy = merged.limitsFor(Tier.ECONOMY)
        assertNull(economy.dailyMessages)
        assertEquals(PlanCatalog.BUNDLED.limitsFor(Tier.ECONOMY).dailyLikes, economy.dailyLikes)
        assertEquals(PlanCatalog.BUNDLED.limitsFor(Tier.ECONOMY).boostsPerMonth, economy.boostsPerMonth)
    }

    @Test
    fun cacheRoundTripsAndCorruptCacheIsIgnored() {
        val custom = PlanCatalogMerger.merge(PlanCatalog.BUNDLED, mapOf("standard" to PlanLimitsOverride(dailyLikes = null, roomSlots = 7)))
        assertEquals(custom, PlanCatalogCodec.decode(PlanCatalogCodec.encode(custom)))
        assertNull(PlanCatalogCodec.decode(""))
        assertEquals(PlanCatalog.BUNDLED, PlanCatalogCodec.decode("çöp veri\nfree.dailyLikes=abc"))
    }

    // ── Ayarlar: ertelenmiş düşürme ve ödeme sorunu ───────────────────────

    private val now = 1_000_000L

    private fun standard(change: ScheduledPlanChange? = null, billingIssueAt: Long? = null, managementUrl: String? = null) =
        EntitlementState(
            tier = Tier.STANDARD, isResolved = true, expiresAtMillis = now + 10_000,
            scheduledChange = change, billingIssueDetectedAtMillis = billingIssueAt, managementUrl = managementUrl,
        )

    @Test
    fun deferredDowngradeIsShownWithItsDate() {
        val status = SubscriptionStatusPresenter.present(standard(ScheduledPlanChange(Tier.ECONOMY, now + 5_000)), now)
        assertEquals(Tier.ECONOMY, status.scheduledTier)
        assertEquals(now + 5_000, status.scheduledAtMillis)
    }

    @Test
    fun deferredDowngradeFallsBackToPeriodEndAndDisappearsOnceApplied() {
        val withoutDate = SubscriptionStatusPresenter.present(standard(ScheduledPlanChange(Tier.ECONOMY, null)), now)
        assertEquals(now + 10_000, withoutDate.scheduledAtMillis)
        val applied = EntitlementState(tier = Tier.ECONOMY, isResolved = true, scheduledChange = ScheduledPlanChange(Tier.ECONOMY, now))
        assertNull(SubscriptionStatusPresenter.present(applied, now).scheduledTier)
        val expired = standard(ScheduledPlanChange(Tier.ECONOMY, now - ScheduledPlanChange.GRACE_MILLIS - 1))
        assertNull(SubscriptionStatusPresenter.present(expired, now).scheduledTier)
    }

    @Test
    fun billingIssueShowsUpdateLinkWithManagementUrl() {
        val status = SubscriptionStatusPresenter.present(standard(billingIssueAt = now, managementUrl = "https://play.google.com/store/account/subscriptions?sku=x"), now)
        assertTrue(status.showBillingIssue)
        assertEquals("https://play.google.com/store/account/subscriptions?sku=x", status.paymentUpdateUrl)
    }

    @Test
    fun unsafeOrMissingManagementUrlFallsBackToPlay() {
        assertEquals(PLAY_SUBSCRIPTIONS_FALLBACK_URL, SubscriptionStatusPresenter.present(standard(billingIssueAt = now, managementUrl = "javascript:alert(1)"), now).paymentUpdateUrl)
        assertFalse(SubscriptionStatusPresenter.present(standard(), now).showBillingIssue)
        assertFalse(SubscriptionStatusPresenter.present(EntitlementState(), now).showBillingIssue)
    }
}
