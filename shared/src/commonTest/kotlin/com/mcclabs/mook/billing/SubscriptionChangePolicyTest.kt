package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.Period
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.ReplacementPolicy
import com.mcclabs.mook.domain.billing.SubscriptionChange
import com.mcclabs.mook.domain.billing.SubscriptionChangePolicy
import com.mcclabs.mook.domain.billing.SubscriptionChangeType
import com.mcclabs.mook.domain.billing.SubscriptionSource
import com.mcclabs.mook.domain.billing.Tier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Plan değişikliği kurallarının (yükseltme / düşürme / çapraz geçiş) karar tablosu testleri. */
class SubscriptionChangePolicyTest {

    private fun plan(tier: Tier, period: Period): PlanPackage {
        val subscriptionId = "wm_${tier.name.lowercase()}"
        val basePlan = if (period == Period.MONTHLY) "monthly" else "yearly"
        return PlanPackage(
            identifier = "${subscriptionId}_$basePlan",
            tier = tier,
            period = period,
            localizedPrice = "₺1",
            productIdentifier = "$subscriptionId:$basePlan",
        )
    }

    private val allPlans = Tier.entries.filter { it != Tier.FREE }
        .flatMap { tier -> Period.entries.map { plan(tier, it) } }

    /** Play'den canlı doğrulanmış aktif bir abonelik (RevenueCat: productIdentifier = abonelik kimliği). */
    private fun active(tier: Tier, period: Period, source: SubscriptionSource? = SubscriptionSource.PLAY_STORE) =
        EntitlementState(
            tier = tier,
            isResolved = true,
            limits = PlanCatalog.BUNDLED.limitsFor(tier),
            activeProductIdentifier = "wm_${tier.name.lowercase()}",
            activeBasePlanIdentifier = if (period == Period.MONTHLY) "monthly" else "yearly",
            activeSubscriptionSource = source,
        )

    private fun evaluate(current: EntitlementState, target: PlanPackage) =
        SubscriptionChangePolicy.evaluate(current, target, allPlans)

    @Test
    fun freeUserMakesANewPurchase() {
        val free = EntitlementState(tier = Tier.FREE, isResolved = true)
        assertEquals(SubscriptionChange.NewPurchase, evaluate(free, plan(Tier.STANDARD, Period.MONTHLY)))
    }

    @Test
    fun unresolvedStateNeverStartsANewPurchase() {
        // Çözülmemiş durum "ücretsiz" sayılırsa ücretli kullanıcı çift abonelik açabilirdi.
        val result = evaluate(EntitlementState(), plan(Tier.STANDARD, Period.MONTHLY))
        assertEquals(SubscriptionChange.NotAllowed(SubscriptionChange.NotAllowed.Reason.ACTIVE_PLAN_UNKNOWN), result)
    }

    @Test
    fun economyToStandardIsAnImmediateProratedUpgradeReplacingTheOldSubscription() {
        val result = evaluate(active(Tier.ECONOMY, Period.MONTHLY), plan(Tier.STANDARD, Period.MONTHLY))
        assertEquals(
            SubscriptionChange.Replace("wm_economy", SubscriptionChangeType.UPGRADE, ReplacementPolicy.IMMEDIATE_WITH_TIME_PRORATION),
            result,
        )
    }

    @Test
    fun higherTierIsAnUpgradeEvenWhenMovingFromYearlyToMonthly() {
        val result = evaluate(active(Tier.STANDARD, Period.YEARLY), plan(Tier.PREMIUM, Period.MONTHLY))
        assertEquals(ReplacementPolicy.IMMEDIATE_WITH_TIME_PRORATION, (result as SubscriptionChange.Replace).policy)
        assertEquals(SubscriptionChangeType.UPGRADE, result.type)
    }

    @Test
    fun premiumToEconomyIsADeferredDowngrade() {
        val result = evaluate(active(Tier.PREMIUM, Period.MONTHLY), plan(Tier.ECONOMY, Period.MONTHLY))
        assertEquals(
            SubscriptionChange.Replace("wm_premium", SubscriptionChangeType.DOWNGRADE, ReplacementPolicy.DEFERRED),
            result,
        )
    }

    @Test
    fun monthlyToYearlyInTheSameTierIsAnImmediateCrossgrade() {
        val result = evaluate(active(Tier.STANDARD, Period.MONTHLY), plan(Tier.STANDARD, Period.YEARLY))
        assertEquals(
            SubscriptionChange.Replace("wm_standard", SubscriptionChangeType.CROSSGRADE, ReplacementPolicy.IMMEDIATE_WITH_TIME_PRORATION),
            result,
        )
    }

    @Test
    fun yearlyToMonthlyInTheSameTierIsADeferredCrossgrade() {
        val result = evaluate(active(Tier.STANDARD, Period.YEARLY), plan(Tier.STANDARD, Period.MONTHLY))
        assertEquals(
            SubscriptionChange.Replace("wm_standard", SubscriptionChangeType.CROSSGRADE, ReplacementPolicy.DEFERRED),
            result,
        )
    }

    @Test
    fun unknownCurrentPeriodFallsBackToDeferredSoTheUserIsNeverChargedUnexpectedly() {
        val current = active(Tier.STANDARD, Period.MONTHLY).copy(activeBasePlanIdentifier = "legacy-v0")
        val result = evaluate(current, plan(Tier.STANDARD, Period.YEARLY))
        assertEquals(ReplacementPolicy.DEFERRED, (result as SubscriptionChange.Replace).policy)
    }

    @Test
    fun selectingTheActivePlanIsNotPurchasable() {
        val result = evaluate(active(Tier.STANDARD, Period.MONTHLY), plan(Tier.STANDARD, Period.MONTHLY))
        assertEquals(SubscriptionChange.NotAllowed(SubscriptionChange.NotAllowed.Reason.ALREADY_ACTIVE), result)
        assertFalse(SubscriptionChangePolicy.isPurchasable(result))
    }

    @Test
    fun promotionalEntitlementHasNoPlayPurchaseToReplace() {
        val current = active(Tier.PREMIUM, Period.MONTHLY, source = SubscriptionSource.PROMOTIONAL)
        assertEquals(SubscriptionChange.NewPurchase, evaluate(current, plan(Tier.STANDARD, Period.MONTHLY)))
    }

    @Test
    fun subscriptionFromAnotherStoreCannotBeChangedInApp() {
        val current = active(Tier.STANDARD, Period.MONTHLY, source = SubscriptionSource.OTHER_STORE)
        assertEquals(
            SubscriptionChange.NotAllowed(SubscriptionChange.NotAllowed.Reason.MANAGED_OUTSIDE_PLAY),
            evaluate(current, plan(Tier.PREMIUM, Period.MONTHLY)),
        )
    }

    @Test
    fun cachedPaidStateWithoutLiveSourceIsNotTrustedForAReplacement() {
        val cachedOnly = active(Tier.STANDARD, Period.MONTHLY, source = null)
        assertEquals(
            SubscriptionChange.NotAllowed(SubscriptionChange.NotAllowed.Reason.ACTIVE_PLAN_UNKNOWN),
            evaluate(cachedOnly, plan(Tier.PREMIUM, Period.MONTHLY)),
        )
    }

    @Test
    fun combinedProductIdentifierIsNormalisedToTheSubscriptionId() {
        val current = active(Tier.ECONOMY, Period.MONTHLY).copy(
            activeProductIdentifier = "wm_economy:monthly",
            activeBasePlanIdentifier = null,
        )
        val result = evaluate(current, plan(Tier.STANDARD, Period.MONTHLY))
        assertEquals("wm_economy", (result as SubscriptionChange.Replace).oldSubscriptionId)
        assertTrue(SubscriptionChangePolicy.isPurchasable(result))
    }
}
