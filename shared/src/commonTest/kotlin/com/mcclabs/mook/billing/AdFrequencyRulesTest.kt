package com.mcclabs.mook.billing

import com.mcclabs.mook.ads.LikeInterstitialAttempt
import com.mcclabs.mook.data.ads.AdCounterLocalDataSource
import com.mcclabs.mook.data.ads.AdCounterRepositoryImpl
import com.mcclabs.mook.domain.billing.AdCounterRepository
import com.mcclabs.mook.domain.billing.AdFrequencyRules
import com.mcclabs.mook.domain.billing.DailyUpsellCoordinator
import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.GateDecision
import com.mcclabs.mook.domain.billing.LikeInterstitialGateway
import com.mcclabs.mook.domain.billing.ProfileVisitInterstitialUseCase
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.billing.UpsellAwareInterstitialGateway
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bellek içi sayaç deposu (testler için). */
private class InMemoryLocal : AdCounterLocalDataSource {
    val values = mutableMapOf<String, Long>()
    override suspend fun getLong(key: String) = values[key]
    override suspend fun putLong(key: String, value: Long) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
}

/** Gösterim sonucunu testin belirlediği sahte geçiş reklamı ağ geçidi. */
private class FakeGateway(var showResult: Boolean = true) : LikeInterstitialGateway {
    var profileVisitCalls = 0
    var likeAttempt = LikeInterstitialAttempt()
    override suspend fun attemptShowAfterTransition(isFree: Boolean, likesEver: Int) = likeAttempt
    override fun recordAction(isFree: Boolean, attempt: LikeInterstitialAttempt, succeeded: Boolean) = Unit
    override suspend fun showProfileVisitInterstitial(isFree: Boolean): Boolean {
        profileVisitCalls++
        return showResult
    }
}

class AdFrequencyRulesTest {

    private val free = EntitlementState(tier = Tier.FREE, isResolved = true, limits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE))
    private val premium = EntitlementState(tier = Tier.PREMIUM, isResolved = true, limits = PlanCatalog.BUNDLED.limitsFor(Tier.PREMIUM))

    @Test
    fun likedMeAdSlotsAreEveryEighthPosition() {
        val slots = (0 until 32).filter(AdFrequencyRules::isLikedMeAdSlot)
        assertEquals(listOf(7, 15, 23, 31), slots)
    }

    @Test
    fun interleavePlacesAdsAtIndexSevenFifteenTwentyThree() {
        val rows = AdFrequencyRules.interleaveLikedMeAds((1..22).toList(), showAds = true, item = { "p$it" }, ad = { "ad$it" })
        assertEquals(listOf(7, 15, 23), rows.indices.filter { rows[it].startsWith("ad") })
        assertEquals(25, rows.size)
        assertEquals("p22", rows.last())
    }

    @Test
    fun interleaveNeverEndsWithAnEmptyAdOrAddsAdsForPaidPlans() {
        assertEquals(7, AdFrequencyRules.interleaveLikedMeAds((1..7).toList(), true, { it }, { -1 }).size)
        assertTrue(AdFrequencyRules.interleaveLikedMeAds((1..30).toList(), false, { it }, { -1 }).none { it == -1 })
    }

    @Test
    fun dailyUpsellIsDueOncePerLocalCalendarDay() {
        val zone = TimeZone.of("Europe/Istanbul")
        val day1Morning = 1_759_294_800_000L // 2025-10-01 08:00 İstanbul
        assertTrue(AdFrequencyRules.isDailyUpsellDue(null, day1Morning, zone))
        assertFalse(AdFrequencyRules.isDailyUpsellDue(day1Morning, day1Morning + 14 * 3_600_000L, zone)) // aynı gün 22:00
        assertTrue(AdFrequencyRules.isDailyUpsellDue(day1Morning, day1Morning + 17 * 3_600_000L, zone)) // ertesi gün 01:00
        assertFalse(AdFrequencyRules.isDailyUpsellDue(day1Morning, day1Morning - 1, zone)) // saat geri alındı
    }

    @Test
    fun profileVisitAdShowsOnEveryThirdVisitAndResetsOnlyWhenShown() = runTest {
        val counters = AdCounterRepositoryImpl(InMemoryLocal(), currentUserId = { "u1" })
        val gateway = FakeGateway(showResult = false)
        val useCase = ProfileVisitInterstitialUseCase(counters, gateway)

        assertIs<GateDecision.Allowed>(useCase(free)!!.decision)
        assertIs<GateDecision.Allowed>(useCase(free)!!.decision)
        // 3. ziyaret: reklam isteniyor ama hazır değil → sayaç korunur.
        assertFalse(useCase(free)!!.adShown)
        assertEquals(1, gateway.profileVisitCalls)
        // 4. ziyaret yeniden dener; bu kez gösterilir ve sayaç sıfırlanır.
        gateway.showResult = true
        assertTrue(useCase(free)!!.adShown)
        assertIs<GateDecision.Allowed>(useCase(free)!!.decision)
        assertEquals(2, gateway.profileVisitCalls)
    }

    @Test
    fun adFreePlansDoNotCountVisits() = runTest {
        val local = InMemoryLocal()
        val useCase = ProfileVisitInterstitialUseCase(AdCounterRepositoryImpl(local, currentUserId = { "u1" }), FakeGateway())
        assertNull(useCase(premium))
        assertNull(useCase(EntitlementState()))
        assertTrue(local.values.isEmpty())
    }

    @Test
    fun countersAreSeparatedPerUser() = runTest {
        var uid = "a"
        val counters: AdCounterRepository = AdCounterRepositoryImpl(InMemoryLocal(), currentUserId = { uid })
        counters.incrementProfileVisits()
        counters.incrementProfileVisits()
        uid = "b"
        assertEquals(1, counters.incrementProfileVisits())
    }

    @Test
    fun upsellAppearsAfterInterstitialAtMostOncePerDay() = runTest {
        var now = 1_759_294_800_000L
        val counters = AdCounterRepositoryImpl(InMemoryLocal(), currentUserId = { "u1" })
        val coordinator = DailyUpsellCoordinator(counters, now = { now }, timeZone = { TimeZone.of("Europe/Istanbul") })
        val delegate = FakeGateway(showResult = true)
        val gateway = UpsellAwareInterstitialGateway(delegate, coordinator)

        // Reklam gösterilmediyse upsell yok.
        delegate.likeAttempt = LikeInterstitialAttempt(adShown = false)
        gateway.attemptShowAfterTransition(isFree = true, likesEver = 10)
        assertFalse(coordinator.isVisible.value)

        // İlk kapatılan reklam → kart görünür.
        delegate.likeAttempt = LikeInterstitialAttempt(adShown = true)
        gateway.attemptShowAfterTransition(isFree = true, likesEver = 10)
        assertTrue(coordinator.isVisible.value)
        coordinator.dismiss()

        // Aynı gün ikinci reklam → kart yok.
        now += 3_600_000L
        gateway.showProfileVisitInterstitial(isFree = true)
        assertFalse(coordinator.isVisible.value)

        // Ertesi gün → yeniden görünür.
        now += 24 * 3_600_000L
        gateway.showProfileVisitInterstitial(isFree = true)
        assertTrue(coordinator.isVisible.value)
    }
}
