package com.mcclabs.mook.domain.billing

/**
 * [PriceChangeConfirmationUseCase.invoke]'ın dönebileceği durumlar (Gereksinim 2.3/2.4).
 */
sealed interface PriceChangeCheckResult {
    /** Kullanıcının ücretli bir aboneliği yok — karşılaştırılacak bir şey yok. */
    data object NoActiveSubscription : PriceChangeCheckResult

    /** Fiyat karşılaştırıldı ve artış YOK (aynı ya da düşmüş). */
    data object NoPriceChange : PriceChangeCheckResult

    /**
     * Fiyat, bu cihazda en son satın alındığı andan bu yana ARTMIŞ.
     *
     * [previousLocalizedPrice] ve [currentLocalizedPrice] ikisi de RevenueCat'in ZATEN
     * yerelleştirdiği metinlerdir (Gereksinim 2.4: UI'da sabit kodlanmış TRY/USD YOK) —
     * sunum katmanı bunları olduğu gibi gösterir, asla kendi biçimlendirmesini YAPMAZ.
     */
    data class Increased(
        val productIdentifier: String,
        val previousLocalizedPrice: String,
        val currentLocalizedPrice: String,
    ) : PriceChangeCheckResult

    /** Bir karşılaştırma YAPILAMADI; [reason] nedenini taşır. */
    data class Unknown(val reason: UnknownReason) : PriceChangeCheckResult
}

/** [PriceChangeCheckResult.Unknown]'ın taşıyabileceği somut sebepler. */
enum class UnknownReason {
    /** RevenueCat henüz canlı bir `CustomerInfo` döndürmedi (`activeProductIdentifier` boş). */
    ACTIVE_PRODUCT_NOT_RESOLVED,

    /**
     * Bu cihazda bu ürün için yerel bir satın alma fiyatı geçmişi yok — ör. abonelik
     * başka bir cihazda satın alınıp buraya `restore()`/transfer edildi, ya da kullanıcı
     * en son satın almadan farklı bir plana geçti.
     */
    NO_LOCAL_PURCHASE_HISTORY,

    /** RevenueCat'in güncel teklifleri şu an okunamadı (ağ hatası vb.). */
    OFFERINGS_UNAVAILABLE,

    /** Satın alınan ürün artık güncel tekliflerde bulunmuyor (ör. ürün kaldırıldı). */
    OFFER_NOT_FOUND,

    /**
     * Para birimi, satın alma anından bu yana DEĞİŞMİŞ (ör. kullanıcı bölge/mağaza
     * değiştirdi). Bu, sayısal karşılaştırmayı ANLAMSIZ kılar ve Google'ın "fiyat artışı
     * onayı" gerektiren senaryosu DEĞİLDİR — sessizce atlanır.
     */
    CURRENCY_CHANGED,
}

/**
 * Gereksinim 2.3: bir abonelik fiyatının, bu cihazda en son satın alındığı andan bu yana
 * artıp artmadığını tespit eden domain sınıfı.
 *
 * ## Neden uygulama içi bir "fiyat değişikliği onay ekranı" YOK
 * Google Play Billing Library, `launchPriceChangeConfirmationFlow()` API'sini (eski
 * sürümlerde vardı) KALDIRDI. Güncel Billing Library sürümlerinde bir fiyat artışının
 * yasal onayı ARTIK uygulama içinden tetiklenemez — Google, gerekli bildirimi/onayı
 * TAMAMEN KENDİ Play Store yüzeyinde (e-posta + Play Store uygulamasındaki abonelik
 * yönetimi sayfası) yürütür; kullanıcı onaylamazsa abonelik yenilenme sırasında OTOMATİK
 * olarak iptal edilir. Bu yüzden bu sınıf bir onay EKRANI GÖSTERMEZ (var olmayan bir API'yi
 * çağırıyormuş gibi YAPMAZ) — yalnızca artışı TESPİT EDER ve kullanıcıyı [Increased]
 * durumuyla RESMİ Play Store sayfasına (bkz. [playStoreSubscriptionManagementUrl])
 * yönlendirecek bir sunum katmanına veri sağlar. Asıl onay ekranı HER ZAMAN Google'ındır;
 * bu uygulama onu asla taklit etmez ya da atlamaz.
 *
 * ## Karşılaştırma nasıl çalışır
 * [SubscriptionRepository.state]'teki [EntitlementState.lastKnownPurchasePrice] (bu
 * cihazda en son BİZZAT yapılan satın almanın anlık görüntüsü) ile
 * [SubscriptionRepository.offerings]'in ŞU AN döndürdüğü, AYNI ürün kimliğine sahip
 * paketin fiyatı karşılaştırılır. Ham mikro birim + para birimi kodu kullanılır (metin
 * karşılaştırması DEĞİL) — bu, yerelleştirilmiş biçimlendirme farklarının (ör. binlik
 * ayraç) yanlışlıkla "fiyat değişti" olarak algılanmasını önler.
 *
 * Asıl yaptırım (aboneliğin gerçekten iptal olup olmayacağı) her zaman Google
 * Play'dedir — bu sınıf yalnızca kullanıcıya ERKEN, BİLGİLENDİRİCİ bir sinyal verir.
 */
class PriceChangeConfirmationUseCase(
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend operator fun invoke(): PriceChangeCheckResult {
        val state = subscriptionRepository.state.value
        if (state.tier == Tier.FREE || !state.isResolved) {
            return PriceChangeCheckResult.NoActiveSubscription
        }

        val activeProductId = state.activeProductIdentifier
            ?: return PriceChangeCheckResult.Unknown(UnknownReason.ACTIVE_PRODUCT_NOT_RESOLVED)

        val purchased = state.lastKnownPurchasePrice
        if (purchased == null || purchased.productIdentifier != activeProductId) {
            return PriceChangeCheckResult.Unknown(UnknownReason.NO_LOCAL_PURCHASE_HISTORY)
        }

        val offer = subscriptionRepository.offerings().getOrElse {
            return PriceChangeCheckResult.Unknown(UnknownReason.OFFERINGS_UNAVAILABLE)
        }
        val currentPlan = offer.packages.firstOrNull { it.productIdentifier == activeProductId }
            ?: return PriceChangeCheckResult.Unknown(UnknownReason.OFFER_NOT_FOUND)

        if (currentPlan.priceCurrencyCode != purchased.currencyCode) {
            return PriceChangeCheckResult.Unknown(UnknownReason.CURRENCY_CHANGED)
        }

        return if (currentPlan.priceAmountMicros > purchased.amountMicros) {
            PriceChangeCheckResult.Increased(
                productIdentifier = activeProductId,
                previousLocalizedPrice = purchased.formattedPrice,
                currentLocalizedPrice = currentPlan.localizedPrice,
            )
        } else {
            PriceChangeCheckResult.NoPriceChange
        }
    }
}

/**
 * Google Play'in abonelik yönetimi ekranına, ÜRÜNE ÖZEL giden derin bağlantı
 * (Gereksinim 2.3).
 *
 * Bu, kullanıcıyı Google'ın RESMİ fiyat değişikliği onay arayüzünün yaşadığı yere
 * götürür — bkz. [PriceChangeConfirmationUseCase] sınıf yorumundaki teknik gerekçe.
 * [productIdentifier] `null`/boş ise genel abonelik listesine (`sku` parametresi
 * olmadan) yönlendirir.
 */
fun playStoreSubscriptionManagementUrl(packageName: String, productIdentifier: String?): String {
    val base = "https://play.google.com/store/account/subscriptions?package=$packageName"
    return if (productIdentifier.isNullOrBlank()) base else "$base&sku=$productIdentifier"
}
