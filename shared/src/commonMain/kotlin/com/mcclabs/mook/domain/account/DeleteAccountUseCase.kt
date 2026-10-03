package com.mcclabs.mook.domain.account

import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.model.AuthResult
import com.mcclabs.mook.domain.repository.AuthRepository

/** [DeleteAccountUseCase.invoke]'un dönebileceği sonuçlar (Gereksinim 1.8). */
sealed interface AccountDeletionResult {
    /** Hesap ve tüm ilişkili veri kalıcı olarak silindi. */
    data object Deleted : AccountDeletionResult

    /** Silme denendi ama başarısız oldu; [message] kullanıcıya gösterilecek Türkçe metindir. */
    data class Failed(val message: String) : AccountDeletionResult

    /**
     * Silme HENÜZ YAPILMADI: cihazda/hesapta hâlâ çözülmüş, ücretli bir abonelik var.
     *
     * Google Play politikası hesap silmeyi App Store/Play Store aboneliğini iptal etmekten
     * ayrı tutar — hesabı silmek RevenueCat/Play Billing aboneliğini OTOMATİK iptal etmez,
     * kullanıcı yine de faturalandırılmaya devam eder. Sunum katmanı bu sonucu aldığında
     * kullanıcıyı bilgilendirip Google Play abonelik yönetimine yönlendirmeli, ancak yine de
     * silmeye devam etmek isterse [invoke]'u `acknowledgedActiveSubscription = true` ile
     * tekrar çağırmalıdır.
     */
    data class ActiveSubscriptionWarning(val tier: Tier, val expiresAtMillis: Long?) : AccountDeletionResult
}

/**
 * Hesap silme iş kuralını tek bir yerde toplayan use case (Gereksinim 1.8).
 *
 * Sunum katmanını (ViewModel) veri kaynağı ayrıntılarından yalıtır: ViewModel yalnızca
 * bu use case'i çağırır, silmenin arkasında bir Cloud Function mı yoksa başka bir kaynak
 * mı olduğunu bilmez. Böylece Tek Sorumluluk ve Bağımlılığın Tersine Çevrilmesi (SOLID)
 * ilkelerine uyulur ve iş kuralı bağımsız olarak test edilebilir.
 *
 * Google Play, hesap oluşturmaya izin veren her uygulamanın hem UYGULAMA İÇİ bir silme yolu
 * (bu use case + `deleteAccount` Cloud Function) HEM DE genel/web tabanlı bir silme talebi
 * adresi sunmasını zorunlu kılar (2023 Hesap Silme politikası). Bu ikinci gereksinim için
 * `hosting/mook-sso/account-deletion.html` altında statik bir Türkçe bilgilendirme sayfası
 * eklendi; Firebase Hosting'in `mook-sso` sitesi üzerinden
 * `https://mook-sso.web.app/account-deletion.html` adresinden erişilebilir ve Play
 * Console'un Veri Güvenliği (Data Safety) formundaki "hesap silme URL'si" alanına bu
 * adresin girilmesi gerekir.
 */
class DeleteAccountUseCase(
    private val authRepository: AuthRepository,
    private val subscriptionRepository: SubscriptionRepository,
) {
    /**
     * Hesabı ve tüm ilişkili veriyi kalıcı olarak silmeyi dener.
     *
     * @param acknowledgedActiveSubscription Kullanıcı, aktif bir ücretli abonelik varken bile
     *   silmeye devam etmeyi zaten onayladıysa `true` geçilmelidir — bu durumda abonelik
     *   kontrolü ATLANIR ve silme doğrudan denenir. İlk çağrıda her zaman `false` (varsayılan)
     *   bırakılmalıdır; yalnızca [AccountDeletionResult.ActiveSubscriptionWarning] alındıktan
     *   ve kullanıcı "yine de sil" seçtikten SONRA `true` ile tekrar çağrılmalıdır.
     */
    suspend operator fun invoke(acknowledgedActiveSubscription: Boolean = false): AccountDeletionResult {
        val entitlement = subscriptionRepository.state.value
        val hasActivePaidTier = entitlement.isResolved && entitlement.tier != Tier.FREE
        if (!acknowledgedActiveSubscription && hasActivePaidTier) {
            return AccountDeletionResult.ActiveSubscriptionWarning(entitlement.tier, entitlement.expiresAtMillis)
        }
        return when (val result = authRepository.deleteAccount()) {
            is AuthResult.Success -> AccountDeletionResult.Deleted
            is AuthResult.Error -> AccountDeletionResult.Failed(result.message)
        }
    }
}
