package com.mcclabs.mook.domain.billing

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** [SsvConfirmationPolicy.await] çağrısının sonucu. */
sealed interface SsvConfirmationResult {
    /** Sunucu, ödülü zaman aşımından önce doğruladı. */
    data object Confirmed : SsvConfirmationResult

    /** [BillingConfig.REWARDED_SSV_TIMEOUT_MILLIS] süresi içinde doğrulama gelmedi. */
    data object TimedOut : SsvConfirmationResult
}

/**
 * AdMob sunucu tarafı doğrulama (SSV) callback'inin gelmesini bekleyen, saf ve platformdan
 * bağımsız bekleme politikası (Gereksinim 1.4).
 *
 * Reklam SDK'sının kendisi (Android'de `RewardedAd`) platforma özgü kaldığından bilerek burada
 * yer almaz; yalnızca "ödül sunucuda göründü mü" diye SORAN taraf ([await]'in aldığı
 * `checkRewardGranted` parametresi) enjekte edilir. Bu ayrım, zamanlama mantığının
 * `kotlinx-coroutines-test`'in sanal zamanıyla gerçek saniyeler beklemeden test edilebilmesini
 * sağlar — bkz. `SsvConfirmationPolicyTest`.
 */
class SsvConfirmationPolicy(
    private val timeoutMillis: Long = BillingConfig.REWARDED_SSV_TIMEOUT_MILLIS,
    private val pollIntervalMillis: Long = 1_000L,
) {
    /**
     * [checkRewardGranted] `true` dönene ya da [timeoutMillis] dolana kadar [pollIntervalMillis]
     * aralıklarla bekler ve sonucu döner. Zaman aşımında [checkRewardGranted] bir daha
     * çağrılmaz — çağıran taraf ödülü ASLA yerel olarak vermemeli, yalnızca sunucudan gelecek
     * bir sonraki senkronizasyonu (ör. `onRewardedLikeConfirmed()`) beklemelidir.
     */
    suspend fun await(checkRewardGranted: suspend () -> Boolean): SsvConfirmationResult {
        val confirmed = withTimeoutOrNull(timeoutMillis) {
            while (true) {
                if (checkRewardGranted()) return@withTimeoutOrNull true
                delay(pollIntervalMillis)
            }
            @Suppress("UNREACHABLE_CODE")
            true
        }
        return if (confirmed == true) SsvConfirmationResult.Confirmed else SsvConfirmationResult.TimedOut
    }
}
