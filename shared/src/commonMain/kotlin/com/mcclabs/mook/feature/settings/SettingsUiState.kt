package com.mcclabs.mook.feature.settings

import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.PriceChangeCheckResult

data class SettingsUiState(
    val email: String = "",
    val discoverVisible: Boolean = true,
    /** Gizli mod açık mı (profilin yalnızca beğendiğin kişilere görünür; ziyaretlerin kaydedilmez). */
    val incognitoEnabled: Boolean = false,
    /** Gizli mod isteği sunucuda işlenirken anahtar kilitlenir. */
    val isIncognitoUpdating: Boolean = false,
    /** Gizli mod güncellenemediğinde gösterilecek tek seferlik metin. */
    val incognitoMessage: String? = null,
    val isDarkMode: Boolean = false,
    val ageRangeStart: Int = 18,
    val ageRangeEnd: Int = 35,
    val isLoading: Boolean = true,
    /** Onay iletişim kutusunun görünürlüğü ("Emin misin?" adımı). */
    val showDeleteConfirmDialog: Boolean = false,
    /** Hesap silme akışının anlık durumu (Idle/Loading/Success/Error). */
    val deleteAccount: DeleteAccountUiState = DeleteAccountUiState.Idle,
    val appLanguage: String = "en",
    val showLanguageDialog: Boolean = false,
    /** Live RevenueCat state; this is presentation only, never an authority for quotas. */
    val subscription: EntitlementState = EntitlementState(),
    /** Ertelenmiş düşürme ve ödeme sorunu bilgilendirmeleri (bkz. [SubscriptionStatusPresenter]). */
    val subscriptionStatus: SubscriptionStatusUi = SubscriptionStatusUi(),
    val isRestoringPurchases: Boolean = false,
    val restorePurchasesMessage: String? = null,
    /**
     * Gereksinim 2.3/2.4: `null` DEĞİLSE, bu cihazda en son satın alındığından bu yana
     * abonelik fiyatının ARTTIĞI tespit edildi — bkz. `PriceChangeConfirmationUseCase`.
     * Yalnızca [PriceChangeCheckResult.Increased] durumu burada tutulur; diğer tüm
     * sonuçlar (değişiklik yok, karşılaştırılamadı) sessizce `null`'a düşer — kullanıcıya
     * yalnızca KESİN bir artış gösterilir, belirsiz bir durum ASLA alarm gibi sunulmaz.
     */
    val priceChangeNotice: PriceChangeCheckResult.Increased? = null,
    /** Gereksinim 5 (Faz 6, KVKK Madde 11): veri dışa aktarma isteğinin anlık durumu. */
    val exportData: ExportDataUiState = ExportDataUiState.Idle,
)

/** [SettingsUiState.exportData] için olası durumlar (Gereksinim 5). */
sealed interface ExportDataUiState {
    data object Idle : ExportDataUiState
    data object Loading : ExportDataUiState
    /** İstek sunucu tarafında KABUL EDİLDİ; e-posta AYRI, asenkron bir adımda gönderilecek. */
    data object Requested : ExportDataUiState
    data class Error(val message: String) : ExportDataUiState
}
