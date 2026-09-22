package com.mcclabs.mook.domain.billing

/** Pure policy so advertising cadence can be tested without an Android ad SDK. */
class AdFrequencyPolicy(
    private val minIntervalMillis: Long = 90_000,
    private val sessionCap: Int = 6,
    private val dailyCap: Int = 20,
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
        if (registeredAtMillis != null && nowMillis - registeredAtMillis < 24 * 60 * 60 * 1000) return false
        if (likesEver < 5 || actionsSinceLastAd < 3) return false
        if (sessionAds >= sessionCap || dailyAds >= dailyCap) return false
        return lastAdAtMillis == null || nowMillis - lastAdAtMillis >= minIntervalMillis
    }
}
