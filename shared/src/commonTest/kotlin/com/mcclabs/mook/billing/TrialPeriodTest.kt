package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.billing.TrialPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gereksinim 1.10: kalan deneme günü hesabı ve deneme sırasında "Yükselt" teşviğinin
 * bastırılması mantığının testleri.
 *
 * [TrialPeriod] hiçbir repository'ye bağımlı olmayan saf bir nesne olduğundan (yalnızca
 * [EntitlementState]'in alanlarından karar üretir), bu testler MockK GEREKTİRMEZ — sahtelenecek
 * hiçbir bağımlılık yoktur.
 */
class TrialPeriodTest {
    private val dayMillis = 24L * 60 * 60 * 1000

    @Test
    fun `deneme aktif degilse kalan gun null doner`() {
        val state = EntitlementState(tier = Tier.STANDARD, inTrial = false, expiresAtMillis = 10_000_000L)
        assertNull(TrialPeriod.remainingDays(state, nowMillis = 0L))
    }

    @Test
    fun `bitis tarihi bilinmiyorsa kalan gun null doner`() {
        val state = EntitlementState(tier = Tier.STANDARD, inTrial = true, expiresAtMillis = null)
        assertNull(TrialPeriod.remainingDays(state, nowMillis = 0L))
    }

    @Test
    fun `tam 3 gun kalmissa 3 doner`() {
        val now = 0L
        val state = EntitlementState(tier = Tier.STANDARD, inTrial = true, expiresAtMillis = now + 3 * dayMillis)
        assertEquals(3, TrialPeriod.remainingDays(state, now))
    }

    @Test
    fun `kismi gun yukari yuvarlanir`() {
        // 2 gün + 1 saat kaldı -> aşağı yuvarlarsa 2 görünür ama kullanıcıya erken bitmiş
        // izlenimi vermemek için 3 dönmelidir.
        val now = 0L
        val remaining = 2 * dayMillis + 60 * 60 * 1000
        val state = EntitlementState(tier = Tier.STANDARD, inTrial = true, expiresAtMillis = now + remaining)
        assertEquals(3, TrialPeriod.remainingDays(state, now))
    }

    @Test
    fun `suresi gecmisse negatif degil 0 doner`() {
        val now = 10_000_000L
        val state = EntitlementState(tier = Tier.STANDARD, inTrial = true, expiresAtMillis = now - dayMillis)
        assertEquals(0, TrialPeriod.remainingDays(state, now))
    }

    @Test
    fun `deneme disindayken yukselt teklifi her zaman gosterilir`() {
        val state = EntitlementState(tier = Tier.STANDARD, inTrial = false)
        assertTrue(TrialPeriod.shouldShowUpgradePrompt(state, targetTier = Tier.PREMIUM))
    }

    @Test
    fun `deneme sirasinda daha ust bir kademeye yukselt teklifi bastirilir`() {
        val state = EntitlementState(tier = Tier.STANDARD, inTrial = true)
        assertEquals(false, TrialPeriod.shouldShowUpgradePrompt(state, targetTier = Tier.PREMIUM))
    }

    @Test
    fun `deneme sirasinda ayni kademeye cross-grade teklifi yine gosterilir`() {
        val state = EntitlementState(tier = Tier.STANDARD, inTrial = true)
        assertTrue(TrialPeriod.shouldShowUpgradePrompt(state, targetTier = Tier.STANDARD))
    }
}
