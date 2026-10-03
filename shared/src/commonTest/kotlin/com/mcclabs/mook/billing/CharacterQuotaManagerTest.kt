package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.CharacterQuotaCheck
import com.mcclabs.mook.domain.billing.CharacterQuotaLimits
import com.mcclabs.mook.domain.billing.CharacterQuotaManager
import com.mcclabs.mook.domain.billing.CharacterQuotaScope
import com.mcclabs.mook.domain.billing.DefaultCharacterQuotas
import com.mcclabs.mook.domain.billing.Tier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Gereksinim 2.2: [CharacterQuotaManager], saf/side-effect'siz bir domain sınıfı olduğu
 * için `kotlin.test` ile test edilir — tıpkı [FeatureGateTest] ve [TrialPeriodTest]'te
 * olduğu gibi, gerçek bir bağımlılığı (Firestore, RevenueCat, vb.) olmadığından MockK
 * burada hiçbir şey sahtelemez; sınıf zaten tamamen deterministiktir.
 */
class CharacterQuotaManagerTest {
    private val manager = CharacterQuotaManager()

    @Test
    fun messageUnderBothLimitsIsAllowed() {
        val result = manager.check(
            tier = Tier.FREE,
            messageLength = 100,
            charsUsedToday = 0,
            charsUsedThisMonth = 0,
        )
        assertEquals(CharacterQuotaCheck.Allowed, result)
    }

    @Test
    fun messageExactlyFillingDailyLimitIsStillAllowed() {
        // Sınırda eşitlik reddetmemeli — "1000 karakter hakkın var" 1000. karakterin de
        // hakkı olduğu anlamına gelir, 999.'nun değil.
        val limits = DefaultCharacterQuotas.byTier.getValue(Tier.FREE)
        val result = manager.check(
            tier = Tier.FREE,
            messageLength = limits.dailyChars,
            charsUsedToday = 0,
            charsUsedThisMonth = 0,
        )
        assertEquals(CharacterQuotaCheck.Allowed, result)
    }

    @Test
    fun messageOneCharacterOverDailyLimitIsExhausted() {
        val limits = DefaultCharacterQuotas.byTier.getValue(Tier.FREE)
        val result = manager.check(
            tier = Tier.FREE,
            messageLength = limits.dailyChars + 1,
            charsUsedToday = 0,
            charsUsedThisMonth = 0,
        )
        val exhausted = assertIs<CharacterQuotaCheck.QuotaExhausted>(result)
        assertEquals(CharacterQuotaScope.DAILY, exhausted.scope)
    }

    @Test
    fun previouslyUsedDailyCharsCountTowardTheLimit() {
        val limits = DefaultCharacterQuotas.byTier.getValue(Tier.FREE)
        val result = manager.check(
            tier = Tier.FREE,
            messageLength = 1,
            charsUsedToday = limits.dailyChars,
            charsUsedThisMonth = 0,
        )
        val exhausted = assertIs<CharacterQuotaCheck.QuotaExhausted>(result)
        assertEquals(CharacterQuotaScope.DAILY, exhausted.scope)
    }

    @Test
    fun dailyLimitIsCheckedBeforeMonthlyLimit() {
        // Aynı mesaj hem günlük hem aylık tavanı aşıyorsa, kullanıcıya daha erken/daha
        // doğru sinyal veren günlük sebep raporlanmalı (bkz. sınıf yorumu).
        val limits = DefaultCharacterQuotas.byTier.getValue(Tier.FREE)
        val result = manager.check(
            tier = Tier.FREE,
            messageLength = limits.dailyChars + 1,
            charsUsedToday = 0,
            charsUsedThisMonth = limits.monthlyChars,
        )
        val exhausted = assertIs<CharacterQuotaCheck.QuotaExhausted>(result)
        assertEquals(CharacterQuotaScope.DAILY, exhausted.scope)
    }

    @Test
    fun monthlyLimitExhaustsEvenWhenDailyLimitStillHasRoom() {
        val limits = DefaultCharacterQuotas.byTier.getValue(Tier.FREE)
        val result = manager.check(
            tier = Tier.FREE,
            messageLength = 10,
            charsUsedToday = 0,
            // Ayın önceki günlerinde harcanmış, tavana çok yakın bir aylık toplam.
            charsUsedThisMonth = limits.monthlyChars - 5,
        )
        val exhausted = assertIs<CharacterQuotaCheck.QuotaExhausted>(result)
        assertEquals(CharacterQuotaScope.MONTHLY, exhausted.scope)
    }

    @Test
    fun premiumHasAHigherCeilingThanFree() {
        val free = DefaultCharacterQuotas.byTier.getValue(Tier.FREE)
        val premium = DefaultCharacterQuotas.byTier.getValue(Tier.PREMIUM)
        // Gereksinim 2.2'nin verdiği somut örnek: Premium 100k/ay, Free 10k/ay.
        assertEquals(10_000, free.monthlyChars)
        assertEquals(100_000, premium.monthlyChars)
        assertEquals(true, premium.dailyChars > free.dailyChars)
        assertEquals(true, premium.monthlyChars > free.monthlyChars)
    }

    @Test
    fun everyTierHasAConcreteCeiling() {
        // Eski modelde sınırsız (null) mesaj sayısı olan kademeler vardı (Standart/
        // Premium); karakter kotasında böyle bir "sınırsız" YOKTUR — her kademe somut
        // bir tavana sahiptir (bkz. CharacterQuotaLimits sınıf yorumu).
        Tier.entries.forEach { tier ->
            val limits = DefaultCharacterQuotas.byTier[tier]
            assertEquals(true, limits != null, "Tier $tier için tanımlı bir kota yok")
            assertEquals(true, limits!!.dailyChars > 0)
            assertEquals(true, limits.monthlyChars > 0)
        }
    }

    @Test
    fun remainingDailyCharsNeverGoesNegative() {
        val limits = DefaultCharacterQuotas.byTier.getValue(Tier.FREE)
        assertEquals(0, manager.remainingDailyChars(Tier.FREE, charsUsedToday = limits.dailyChars + 500))
    }

    @Test
    fun remainingDailyCharsReflectsUsage() {
        val limits = DefaultCharacterQuotas.byTier.getValue(Tier.ECONOMY)
        assertEquals(
            limits.dailyChars - 100,
            manager.remainingDailyChars(Tier.ECONOMY, charsUsedToday = 100),
        )
    }

    @Test
    fun remainingMonthlyCharsReflectsUsage() {
        val limits = DefaultCharacterQuotas.byTier.getValue(Tier.STANDARD)
        assertEquals(
            limits.monthlyChars - 1_000,
            manager.remainingMonthlyChars(Tier.STANDARD, charsUsedThisMonth = 1_000),
        )
    }

    @Test
    fun customQuotaMapIsRespected() {
        // Gereksinim: SOLID (Open/Closed) — varsayılan tablo sabit kodlanmış değil,
        // testte olduğu gibi farklı bir harita enjekte edilebilir.
        val custom = CharacterQuotaManager(
            quotasByTier = mapOf(Tier.FREE to CharacterQuotaLimits(dailyChars = 5, monthlyChars = 50)),
        )
        val exhausted = assertIs<CharacterQuotaCheck.QuotaExhausted>(
            custom.check(Tier.FREE, messageLength = 6, charsUsedToday = 0, charsUsedThisMonth = 0),
        )
        assertEquals(CharacterQuotaScope.DAILY, exhausted.scope)
        // Haritada olmayan bir kademe için sınırsız (Allowed) davranışa düşer.
        assertEquals(
            CharacterQuotaCheck.Allowed,
            custom.check(Tier.PREMIUM, messageLength = 1_000_000, charsUsedToday = 0, charsUsedThisMonth = 0),
        )
        assertNull(custom.remainingDailyChars(Tier.PREMIUM, charsUsedToday = 0))
    }
}
