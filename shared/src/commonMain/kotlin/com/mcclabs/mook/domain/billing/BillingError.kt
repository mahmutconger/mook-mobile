package com.mcclabs.mook.domain.billing

/**
 * Play Billing / RevenueCat satın alma hatalarının sağlayıcıdan bağımsız sınıflandırması
 * (Gereksinim 1.11).
 *
 * Daha önce [PurchaseOutcome.Error] RevenueCat'in ham `error.message` metnini olduğu gibi
 * taşıyordu — bu hem İngilizce/yerelleştirilmemişti hem de sunum katmanının HANGİ durumun
 * oluştuğuna göre farklı davranmasını (ör. "Play Store'u güncelle" ile "zaten sahipsin"
 * ayrımını) imkânsız kılıyordu. Bu sealed interface'in her durumu, sunum katmanında ayrı bir
 * Türkçe metne ve (gerekiyorsa) ayrı bir eyleme (ör. "Abonelikleri yönet"e gitme) eşlenir.
 *
 * Sağlayıcıya özgü ayrıntı bu sınıfın DIŞINDA tutulur: eşleme mantığı yalnızca
 * `androidMain`'deki `PurchasesError.toBillingError()` içinde yaşar, böylece bu tip
 * `commonMain`'de kalıp iOS için de anlamlı olur (iOS abonelik tarafı henüz no-op olsa da).
 */
sealed interface BillingError {
    /**
     * Cihazda Play Store yok, oturum açılmamış ya da mağaza bir şekilde erişilemez durumda
     * (RevenueCat'in ham Play Billing `BILLING_UNAVAILABLE` yanıt kodunu ayrı bir alan olarak
     * dışarı vermemesi nedeniyle burada `StoreProblemError`'a eşlenir — bkz. `toBillingError()`
     * KDoc'u).
     */
    data object BillingUnavailable : BillingError

    /** Bu ürün bu Google hesabıyla zaten satın alınmış (Play Billing `ITEM_ALREADY_OWNED`). */
    data object ItemAlreadyOwned : BillingError

    /** İstek sırasında ağ bağlantısı sorunu yaşandı. */
    data object NetworkError : BillingError

    /** Aynı anda zaten devam eden bir satın alma/geri yükleme isteği var. */
    data object OperationInProgress : BillingError

    /** Satın alma, uygulama ön planda (aktif bir Activity) değilken tetiklenmeye çalışıldı. */
    data object ActivityUnavailable : BillingError

    /** Seçilen paket artık mevcut değil (ör. teklif listesi tazelenmeden önce değişti). */
    data object ProductUnavailable : BillingError

    /**
     * Gereksinim 2.1: geri yükleme (restore), bu Google hesabındaki makbuzun ZATEN BAŞKA bir
     * WalkMatch hesabına (farklı bir RevenueCat App User ID'sine) bağlı olduğunu tespit etti
     * ve RevenueCat panelindeki "Restore Behavior" ayarı ("Transfer if there are no active
     * subscriptions") bu durumda aktarımı REDDETTİ — çünkü o diğer hesapta hâlâ aktif bir
     * abonelik var. RevenueCat'in `ReceiptAlreadyInUseError`'ının (SDK: "bu makbuz zaten başka
     * bir App User ID tarafından kullanılıyor") karşılığıdır — bkz. `toBillingError()` KDoc'u.
     */
    data object SubscriptionLinkedToAnotherAccount : BillingError

    /** Yukarıdakilerin hiçbirine net biçimde eşlenmeyen, sağlayıcı kaynaklı diğer hatalar. */
    /**
     * Plan değişikliği için değiştirilecek eski Play satın alması bu cihazdaki Google hesabında
     * bulunamadı (ör. abonelik başka bir Google hesabıyla alınmış) — RevenueCat bu durumda eski
     * `purchaseToken`'ı çözemez ve değiştirme başlatılamaz.
     */
    data object ExistingSubscriptionNotFound : BillingError

    /** Aktif abonelik Google Play dışında yönetiliyor; plan bu uygulamadan değiştirilemez. */
    data object SubscriptionManagedElsewhere : BillingError

    /** Seçilen paket zaten aktif olan paket. */
    data object PlanAlreadyActive : BillingError

    /** Aktif abonelik canlı olarak doğrulanamadı; güvenli bir plan değişikliği kurulamaz. */
    data object ActivePlanUnknown : BillingError

    data class Unknown(val rawMessage: String) : BillingError
}
