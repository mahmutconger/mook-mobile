package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.data.repository.mapLikedMeUnlockFailure
import com.mcclabs.mook.domain.billing.BillingConfig
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.billing.RewardType
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.repository.LikedMeUnlockResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Ödüllü Reklam Ekonomisi" istemci kuralları — sunucudaki rewards.ts ile aynı olmalı. */
class RewardEconomyTest {

    @Test
    fun birBegeniReklamiBesBegeniKazandirirVeGundeBirKezIzlenir() {
        assertEquals(5, RewardType.BONUS_LIKES.grantPerAd)
        assertEquals(1, RewardType.BONUS_LIKES.adsRemainingToday(earnedToday = 0))
        assertEquals(0, RewardType.BONUS_LIKES.adsRemainingToday(earnedToday = 5))
    }

    @Test
    fun begeniReklamiYalnizcaTamOdulSigarsaTeklifEdilir() {
        assertTrue(BillingConfig.canEarnRewardedLikeBonus(Tier.FREE, rewardedLikesToday = 0))
        assertFalse(BillingConfig.canEarnRewardedLikeBonus(Tier.FREE, rewardedLikesToday = 5))
        // Eski "+1 / reklam" sisteminden kalan kısmi sayaç: +5 artık sığmaz.
        assertFalse(BillingConfig.canEarnRewardedLikeBonus(Tier.ECONOMY, rewardedLikesToday = 1))
        assertFalse(BillingConfig.canEarnRewardedLikeBonus(Tier.STANDARD, rewardedLikesToday = 0))
    }

    @Test
    fun izlenenTekReklamGunlukBegeniLimitineBesEkler() {
        val free = com.mcclabs.mook.domain.billing.PlanCatalog.BUNDLED.limitsFor(Tier.FREE)
        assertEquals(15, BillingConfig.effectiveDailyLikeLimit(free, Tier.FREE, rewardedLikesToday = 5))
    }

    @Test
    fun begenmeVeOdaReklamlariGunlukTavanlariniKorur() {
        assertEquals(2, RewardType.LIKED_ME_UNLOCK.adsRemainingToday(0))
        assertEquals(0, RewardType.LIKED_ME_UNLOCK.adsRemainingToday(2))
        assertEquals(1, RewardType.ROOM_SWITCH.adsRemainingToday(0))
        assertEquals(0, RewardType.ROOM_SWITCH.adsRemainingToday(1))
    }

    @Test
    fun ssvCustomDataSunucuAnahtarlariylaAynidir() {
        assertEquals("bonus_like_v1", RewardType.BONUS_LIKES.ssvCustomData)
        assertEquals("bonus_liked_me_unlock_v1", RewardType.LIKED_ME_UNLOCK.ssvCustomData)
        assertEquals("bonus_room_switch_v1", RewardType.ROOM_SWITCH.ssvCustomData)
    }

    @Test
    fun limitSayfasiYalnizcaKazancYoluOlanLimitlerdeReklamTeklifEder() {
        assertEquals(RewardType.BONUS_LIKES, RewardType.forLimitReason(LimitReason.DAILY_LIKES))
        assertEquals(RewardType.LIKED_ME_UNLOCK, RewardType.forLimitReason(LimitReason.LIKED_ME_UNLOCKS))
        assertEquals(RewardType.ROOM_SWITCH, RewardType.forLimitReason(LimitReason.ROOM_SWITCHES))
        assertNull(RewardType.forLimitReason(LimitReason.DAILY_MESSAGES))
    }

    @Test
    fun unlockLikedMeSunucuHatalariAlanSonucunaEslenir() {
        assertEquals(LikedMeUnlockResult.DailyLimitReached, mapLikedMeUnlockFailure("RESOURCE_EXHAUSTED: daily-liked-me-limit"))
        assertEquals(LikedMeUnlockResult.UpgradeRequired, mapLikedMeUnlockFailure("upgrade-required"))
        assertEquals(LikedMeUnlockResult.NotLikedByProfile, mapLikedMeUnlockFailure("not-liked-by-profile"))
        assertEquals(LikedMeUnlockResult.Failed("ağ hatası"), mapLikedMeUnlockFailure("ağ hatası"))
    }
}
