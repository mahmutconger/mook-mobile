package com.mcclabs.mook.domain.billing

import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.domain.repository.InteractionRepository
import com.mcclabs.mook.util.Log
import com.mcclabs.mook.util.getCurrentTimeMillis

/**
 * Uygulama açılışında (Gereksinim 1.3) [PendingActionQueue]'da kalmış — yani bir önceki
 * oturumda reklam kapanmadan/onaylanmadan süreç öldürülmüş — beğeni/geçme eylemlerini
 * sunucuya yeniden gönderir.
 *
 * Sunucu tarafı `swipe` Cloud Function'ı etkileşimi `${fromUserId}_${toUserId}` belirlenimci
 * kimliğiyle sakladığından bu tekrar oynatma güvenlidir: eylem daha önce zaten sunucuya
 * ulaşmış olsa dahi sunucu ikinci çağrıyı no-op (idempotent) olarak ele alır — kullanım
 * hakkı ikinci kez düşülmez (bkz. [PendingActionQueue] KDoc'u).
 *
 * Sessizce (kullanıcıya herhangi bir yükleme/hata UI'ı göstermeden) arka planda çalışır;
 * uygulama zaten normal şekilde açılmaya devam eder.
 */
class RecoverPendingSwipeActionsUseCase(
    private val pendingActionQueue: PendingActionQueue,
    private val interactionRepository: InteractionRepository,
    private val connectivityObserver: ConnectivityObserver,
) {
    suspend operator fun invoke() {
        val pending = pendingActionQueue.dequeueAll()
        if (pending.isEmpty()) return
        // Bağlantı yoksa hiç denemeyiz — kuyruk olduğu gibi kalır, bir sonraki açılışta
        // (ya da yaş sınırına kadar) tekrar denenir.
        if (!connectivityObserver.isOnline.value) return

        val now = getCurrentTimeMillis()
        for (action in pending) {
            val age = now - action.enqueuedAtMillis
            if (age > BillingConfig.PENDING_ACTION_MAX_AGE_MILLIS) {
                // Muhtemelen zaten sunucuda işlendi ya da kullanıcı için anlamını yitirdi;
                // sonsuza dek tekrar denenmesin diye sessizce atılır.
                pendingActionQueue.remove(action)
                continue
            }
            // `InteractionRepositoryImpl.swipeUser` kendi içinde tüm istisnaları yakalayıp
            // `MatchResult.Error` döndürür (asla fırlatmaz); `runCatching` yalnızca ekstra bir
            // güvenlik ağıdır. Sonuç ne olursa olsun (başarı ya da sunucu reddi) kayıt
            // kuyruktan kaldırılır — bu, aynı eylemin canlı (recovery dışı) akıştaki
            // davranışıyla tutarlıdır: sunucudan kesin bir cevap alındıktan sonra kalıcı
            // kuyruğun görevi biter.
            runCatching { interactionRepository.swipeUser(action.profileId, action.isLike) }
                .onSuccess { result ->
                    if (result is MatchResult.Error) {
                        Log.e("Bekleyen eylem sunucu tarafından reddedildi: ${action.profileId} — ${result.message}")
                    }
                    pendingActionQueue.remove(action)
                }
                .onFailure { error ->
                    Log.e("Bekleyen eylem kurtarma başarısız, kuyrukta bırakılıyor", error)
                }
        }
    }
}
