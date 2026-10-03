package com.mcclabs.mook.domain.support

/** [CustomerSupportUseCase.invoke]'un dönebileceği sonuçlar (Gereksinim 7, Faz 6). */
sealed interface PromotionalGrantResult {
    /** İstek sunucu tarafında KABUL EDİLDİ ve RevenueCat'te hak BAŞARIYLA tanındı. */
    data object Granted : PromotionalGrantResult

    /** İstek başarısız oldu; [message] destek arayüzünde gösterilecek Türkçe metindir. */
    data class Failed(val message: String) : PromotionalGrantResult
}

/**
 * Gereksinim 7 (Faz 6, Gözlemlenebilirlik & Destek Araçları): [CustomerSupportRepository]'yi
 * tek bir sorumlulukla (SRP) sarmalar -- ham sunucu hatasını ÇAĞIRANA SIZDIRMAZ, Türkçe,
 * gösterilebilir bir sonuca çevirir (bkz. `ExportUserDataUseCase`/`DeleteAccountUseCase` ile
 * AYNI ilke, o sınıfların KDoc'u).
 *
 * BİLEREK bu uygulamanın kendi UI'sinden (Settings vb.) ÇAĞRILMAZ -- `moderateUser`/
 * `bootstrapMonetization` (bkz. `monetization.ts`/`moderation.ts`) gibi, bu da yetkili bir
 * destek/yönetici aracı (ör. dahili bir destek konsolu, ya da `firebase functions:shell`)
 * tarafından tetiklenmesi amaçlanan bir yönetici işlemidir -- son kullanıcıya yönelik bir
 * arayüzde "kendine premium tanı" gibi bir arka kapı YARATILMAMASI için bilinçli bir tasarım
 * kararıdır.
 */
class CustomerSupportUseCase(
    private val customerSupportRepository: CustomerSupportRepository,
) {
    suspend operator fun invoke(
        targetUid: String,
        entitlement: PromotionalEntitlement,
        duration: PromotionalGrantDuration,
        reason: String,
    ): PromotionalGrantResult =
        customerSupportRepository.requestPromotionalGrant(targetUid, entitlement, duration, reason).fold(
            onSuccess = { PromotionalGrantResult.Granted },
            onFailure = { error ->
                PromotionalGrantResult.Failed(
                    error.message ?: "Promosyonel hak tanımlanamadı.",
                )
            },
        )
}
