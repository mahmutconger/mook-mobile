package com.mcclabs.mook.domain.billing

/**
 * [WinBackOfferUseCase.invoke]'ın döndürdüğü nihai sunum kararı (Gereksinim 2.7).
 */
sealed interface WinBackOfferDecision {
    /** İndirim TEKLİF EDİLEBİLİR — kullanıcı 90 günlük soğuma süresi içinde bir Win-Back kullanmadı. */
    data object Show : WinBackOfferDecision

    /** İndirim gösterilMEMELİ; [reason] nedenini taşır. */
    data class Hidden(val reason: WinBackHiddenReason) : WinBackOfferDecision
}

/** [WinBackOfferDecision.Hidden]'ın taşıyabileceği somut sebepler. */
enum class WinBackHiddenReason {
    /** 90 günlük soğuma süresi HENÜZ dolmadı (bkz. `functions/src/monetization.ts`). */
    COOLDOWN_ACTIVE,

    /**
     * Sunucudan uygunluk bilgisi alınAMADI (ağ hatası, RevenueCat henüz yapılandırılmamış
     * vb.). Belirsizlikte teklif ASLA gösterilmez — bkz. sınıf yorumu.
     */
    UNKNOWN,
}

/**
 * Gereksinim 2.7: bir Win-Back (geri kazanım) indiriminin şu an kullanıcıya
 * SUNULUP SUNULMAYACAĞINA dair nihai kararı üreten domain sınıfı.
 *
 * ## Sorumluluk sınırı (SOLID: Single Responsibility)
 * Asıl 90 günlük soğuma kuralı ve onun yaptırımı [SubscriptionRepository.winBackEligibility]
 * ARACILIĞIYLA sunucudadır (`checkWinBackEligibility` Cloud Function'ı, bkz.
 * `functions/src/monetization.ts`) — bu sınıf o SONUCU tekrar hesaplamaz, yalnızca sunum
 * katmanının anlayacağı BASİT bir ikili karara (`Show`/`Hidden`) indirger. Bu ayrım,
 * `PriceChangeConfirmationUseCase`'in RevenueCat'in kendisiyle değil
 * [SubscriptionRepository] SOYUTLAMASIYLA konuşması gerekliliğiyle AYNI gerekçeye dayanır:
 * repository'ler TÜM üçüncü taraf SDK çağrılarını soyutlamalıdır.
 *
 * ## Güvenli varsayılan (fail-safe default)
 * Sunucudan bir cevap ALINAMAZSA (ör. ağ hatası) sonuç HER ZAMAN [WinBackOfferDecision.Hidden]
 * olur, ASLA [WinBackOfferDecision.Show] değil — belirsizlikte bir indirimi YANLIŞLIKLA
 * GÖSTERMEMEK, uygun bir kullanıcıya onu bir kez KAÇIRMAKTAN çok daha az risklidir (özellikle
 * bu Gereksinim'in amacı tam olarak indirimin KÖTÜYE KULLANIMINI önlemektir).
 */
class WinBackOfferUseCase(
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend operator fun invoke(): WinBackOfferDecision {
        val eligibility = subscriptionRepository.winBackEligibility().getOrNull()
            ?: return WinBackOfferDecision.Hidden(WinBackHiddenReason.UNKNOWN)
        return if (eligibility.isEligible) {
            WinBackOfferDecision.Show
        } else {
            WinBackOfferDecision.Hidden(WinBackHiddenReason.COOLDOWN_ACTIVE)
        }
    }
}
