package com.mcclabs.mook.domain.billing

/**
 * Reklam kapanmadan/onaylanmadan önce sunucuya henüz gönderilmemiş, kuyruğa alınmış bir
 * beğeni/geçme eylemi (Gereksinim 1.3).
 *
 * Şu an yalnızca beğeni akışı reklam öncesi bir bekleme noktasına sahip olduğundan (bkz.
 * `showLikeInterstitialBeforeAction`) pratikte [isLike] her zaman `true`'dur; tip yine de
 * geleceğe dönük olarak geneldir.
 */
data class PendingSwipeAction(
    val profileId: String,
    val isLike: Boolean,
    val enqueuedAtMillis: Long,
)

/**
 * Reklam gösterilmeden ÖNCE sıraya alınan, henüz sunucuya gönderilmemiş beğeni/geçme
 * eylemlerini KALICI olarak (process ölümüne dayanıklı şekilde) saklar (Gereksinim 1.3).
 *
 * `SavedStateHandle` yalnızca sistem tarafından arka planda öldürülüp geri yüklenen
 * process'lere karşı dayanıklıdır; kullanıcının uygulamayı reklam kapanmadan zorla
 * kapatması (ya da sistemin process'i düşük bellekte tamamen sonlandırması) durumunda
 * `SavedStateHandle`'a yazılmamış/commit edilmemiş bellek içi durum da kaybolabilir. Bu
 * kuyruk bunun yerine her zaman DİSKE yazar: eylem, reklam kapanmadan süreç öldürülse bile
 * bir sonraki açılışta okunup sunucuya gönderilebilir (bkz. [BillingConfig.PENDING_ACTION_MAX_AGE_MILLIS]
 * ve reklamın öncesinde/sonrasında bu arayüzü çağıran ViewModel'ler).
 *
 * Sunucu tarafı `swipe` Cloud Function'ı, etkileşim belgesini `${fromUserId}_${toUserId}`
 * ile belirlenimci (deterministic) bir kimlikle yazdığından bu tekrar oynatma (replay)
 * doğası gereği GÜVENLİDİR: aynı eylem iki kez gönderilse dahi sunucu ikinci çağrıyı
 * `idempotent: true` ile no-op olarak ele alır, kullanım hakkı ikinci kez düşülmez.
 */
interface PendingActionQueue {
    /** Eylemi kalıcı depoya ekler. Reklam gösterilmeden hemen önce çağrılmalıdır. */
    suspend fun enqueue(action: PendingSwipeAction)

    /** Kalıcı depodaki tüm bekleyen eylemleri, eklenme sırasıyla döner. */
    suspend fun dequeueAll(): List<PendingSwipeAction>

    /** Sunucuya başarıyla gönderildikten (ya da bilinçli olarak atlandıktan) sonra kaldırır. */
    suspend fun remove(action: PendingSwipeAction)

    /** Tüm kuyruğu temizler; testler ve çıkış (logout) akışları için. */
    suspend fun clear()
}
