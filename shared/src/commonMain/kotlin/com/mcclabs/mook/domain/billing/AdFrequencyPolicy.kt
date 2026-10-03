package com.mcclabs.mook.domain.billing

/** Pure policy so advertising cadence can be tested without an Android ad SDK. */
class AdFrequencyPolicy(
    private val minIntervalMillis: Long = 90_000,
    private val sessionCap: Int = 6,
    private val dailyCap: Int = 20,
    /** Kaç başarılı eylemde bir reklam denenir (Remote Config: `interstitial_actions_between_ads`). */
    private val actionsBetweenAds: Int = 3,
) {
    fun canShow(
        nowMillis: Long,
        registeredAtMillis: Long?,
        likesEver: Int,
        actionsSinceLastAd: Int,
        lastAdAtMillis: Long?,
        sessionAds: Int,
        dailyAds: Int,
    ): Boolean {
        if (likesEver < 5 || actionsSinceLastAd < actionsBetweenAds) return false
        return canShowAtTransition(nowMillis, registeredAtMillis, lastAdAtMillis, sessionAds, dailyAds)
    }

    /**
     * Tüm geçiş reklamlarının ORTAK sınırları (beğeni temposu hariç): kayıttan sonraki ilk 24
     * saat reklamsız, oturum/gün tavanı ve iki reklam arası en kısa süre. Profil ziyareti
     * reklamı yalnızca bunları uygular; kendi temposu (her 3. ziyaret) [AdFrequencyRules]'tadır.
     */
    fun canShowAtTransition(
        nowMillis: Long,
        registeredAtMillis: Long?,
        lastAdAtMillis: Long?,
        sessionAds: Int,
        dailyAds: Int,
    ): Boolean {
        if (registeredAtMillis != null && nowMillis - registeredAtMillis < 24 * 60 * 60 * 1000) return false
        if (sessionAds >= sessionCap || dailyAds >= dailyCap) return false
        return lastAdAtMillis == null || nowMillis - lastAdAtMillis >= minIntervalMillis
    }
}
