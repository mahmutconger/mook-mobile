package com.mcclabs.mook.domain.billing

/**
 * Ödüllü reklamla kazanılabilecek haklar ("Ödüllü Reklam Ekonomisi").
 *
 * [ssvCustomData], reklam gösterilirken AdMob SSV `custom_data` alanına yazılır; sunucu ödül
 * TÜRÜNÜ yalnızca bu değerle ayırt eder (üç ödül aynı reklam birimini paylaşır) ve verilecek
 * hakkı kendi tablosundan belirler (`functions/src/rewards.ts` — `REWARD_DEFINITIONS`).
 * [grantPerAd] ve [dailyCap] o tabloyla BİLİNÇLİ olarak aynı tutulur; istemci bunları yalnızca
 * metin ve iyimser kontrol için kullanır.
 */
enum class RewardType(
    val ssvCustomData: String,
    val grantPerAd: Int,
    val dailyCap: Int,
) {
    /** Günde 1 reklam → +5 beğeni. */
    BONUS_LIKES("bonus_like_v1", BillingConfig.REWARDED_LIKES_PER_AD, BillingConfig.MAX_FREE_REWARDED_LIKES),

    /** Günde en fazla 2 reklam → her biri 1 "beni beğenenler" profil açma hakkı. */
    LIKED_ME_UNLOCK("bonus_liked_me_unlock_v1", 1, BillingConfig.MAX_REWARDED_LIKED_ME_UNLOCKS),

    /** Günde 1 reklam → +1 oda değiştirme hakkı. */
    ROOM_SWITCH("bonus_room_switch_v1", 1, BillingConfig.MAX_REWARDED_ROOM_SWITCHES);

    /** Bugün bu ödül için izlenebilecek kalan reklam sayısı. */
    fun adsRemainingToday(earnedToday: Int): Int =
        ((dailyCap - earnedToday.coerceAtLeast(0)) / grantPerAd).coerceAtLeast(0)

    companion object {
        /** Bir limit dolduğunda hangi ödüllü reklamın teklif edilebileceği; yolu yoksa `null`. */
        fun forLimitReason(reason: LimitReason): RewardType? = when (reason) {
            LimitReason.DAILY_LIKES -> BONUS_LIKES
            LimitReason.LIKED_ME_UNLOCKS -> LIKED_ME_UNLOCK
            LimitReason.ROOM_SWITCHES -> ROOM_SWITCH
            else -> null
        }
    }
}
