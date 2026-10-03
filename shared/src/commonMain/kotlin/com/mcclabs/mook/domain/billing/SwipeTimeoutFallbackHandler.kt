package com.mcclabs.mook.domain.billing

import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.util.getCurrentTimeMillis
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gereksinim 1 (Faz 6): `swipe` Cloud Function çağrısını gerçek ağ gecikmesinden/kopmasından
 * yalıtır -- kullanıcı arayüzü asla süresiz beklemez ve asla kilitlenmez.
 *
 * [execute], verilen [call] askıya alma (suspend) lambdasını en fazla [timeoutMillis] kadar
 * bekler ([BillingConfig.SWIPE_TIMEOUT_MILLIS], varsayılan 5 saniye). İki senaryoda eylem
 * [PendingActionQueue]'ya (kalıcı, diske yazılan kuyruk -- bkz. o arayüzün KDoc'u) sessizce
 * kuyruğa alınır ve [MatchResult.QueuedOffline] döner -- çağıranlar bunu bir HATA değil,
 * "eylem bağlantı kurulunca gönderilecek" bilgisi olarak ele almalıdır:
 *
 * 1. [call] [timeoutMillis] içinde tamamlanmazsa (`withTimeoutOrNull` `null` döner) -- sunucu
 *    yanıt vermiyor ya da ağ aşırı yavaş.
 * 2. [call] bir [MatchResult.Error] ile tamamlanır AMA tamamlandığı anda cihazın bilinen ağ
 *    durumu çevrimdışıysa -- bu, hatanın muhtemelen bir sunucu reddi değil bir bağlantı
 *    kopması olduğunu gösterir (bkz. [ConnectivityObserver] KDoc'undaki "yalnızca ucuza ön
 *    tespit" uyarısı -- asıl çağrının sonucu esas alınır, bu yalnızca yorumlamayı değiştirir).
 *
 * Kuyruğa alma dışındaki tüm diğer durumlarda (başarı ya da çevrimiçiyken gelen gerçek bir
 * sunucu hatası) [call]'un sonucu olduğu gibi döner -- bu sınıf asla gerçek bir hatayı
 * gizlemez, yalnızca "belki geçicidir" durumunu ayırt eder.
 *
 * Sunucu tarafı `swipe` Cloud Function'ı etkileşimi `${fromUserId}_${toUserId}` belirlenimci
 * kimliğiyle sakladığından kuyruğa alınan eylemin daha sonra tekrar gönderilmesi güvenlidir
 * (bkz. [RecoverPendingSwipeActionsUseCase]); [PendingActionQueue]'nun platform
 * implementasyonları aynı profil+yön için zaten yinelenen kayıt eklemez (bkz.
 * `PlatformPendingActionQueue.android.kt`/`.ios.kt`).
 */
class SwipeTimeoutFallbackHandler(
    private val pendingActionQueue: PendingActionQueue,
    private val connectivityObserver: ConnectivityObserver,
) {
    suspend fun execute(
        profileId: String,
        isLike: Boolean,
        timeoutMillis: Long = BillingConfig.SWIPE_TIMEOUT_MILLIS,
        call: suspend () -> MatchResult,
    ): MatchResult {
        val result = withTimeoutOrNull(timeoutMillis) { call() }
            ?: return queueForRetry(profileId, isLike)

        if (result is MatchResult.Error && !connectivityObserver.isOnline.value) {
            return queueForRetry(profileId, isLike)
        }
        return result
    }

    private suspend fun queueForRetry(profileId: String, isLike: Boolean): MatchResult {
        pendingActionQueue.enqueue(
            PendingSwipeAction(profileId = profileId, isLike = isLike, enqueuedAtMillis = getCurrentTimeMillis()),
        )
        return MatchResult.QueuedOffline
    }
}
