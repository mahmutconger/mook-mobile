package com.mcclabs.mook.domain.billing

/**
 * Gereksinim 2.1: satın almaları geri yükleme (restore) akışını TEK bir yerden yönetir.
 *
 * Daha önce `subscriptions.restore()` hem `SettingsViewModel` hem `PaywallViewModel`
 * tarafından ayrı ayrı çağrılıyor, sonucu ayrı ayrı yorumluyordu — SOLID'in Tek Sorumluluk
 * ilkesine aykırı bu tekrar, iki ekranın aynı hata için farklı davranmasına (ör. birinin
 * "başka hesaba bağlı" durumunu genel bir hata gibi göstermesine) açık kapı bırakıyordu.
 *
 * **RevenueCat panel yapılandırması (kod DIŞINDA, dashboard'da yapılmalıdır — bkz.
 * `MookApplication.kt`'deki not):** Project Settings → Restore Behavior →
 * **"Transfer if there are no active subscriptions"**. Bu, aynı makbuzun başka bir hesaba
 * aktarılmasına yalnızca o hesapta AKTİF bir abonelik KALMADIĞINDA izin verir; aksi halde
 * [BillingError.SubscriptionLinkedToAnotherAccount] ile başarısız olur (bkz.
 * `PurchasesError.toBillingError()`'daki `ReceiptAlreadyInUseError` eşlemesi) ve bu use case
 * bunu, kullanıcıya sınırlamayı açıklayan yerelleştirilmiş bir mesajla iletir.
 */
class RestoreSubscriptionUseCase(
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend operator fun invoke(): PurchaseOutcome = subscriptionRepository.restore()
}
