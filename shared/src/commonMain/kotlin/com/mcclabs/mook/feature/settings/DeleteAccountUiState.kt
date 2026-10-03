package com.mcclabs.mook.feature.settings

import com.mcclabs.mook.domain.billing.Tier

/**
 * Hesap silme akışının tek yönlü veri akışındaki (UDF) durumları.
 *
 * Birbirini dışlayan durumlar ayrı tiplerle modellenir; böylece "yükleniyor" ve "hata"
 * gibi tutarsız kombinasyonlar derleme zamanında imkânsız olur.
 */
sealed interface DeleteAccountUiState {
    /** Henüz bir silme işlemi başlatılmadı. */
    data object Idle : DeleteAccountUiState

    /** Silme sürüyor; kullanıcıya ilerleme gösterilir, butonlar pasifleştirilir. */
    data object Loading : DeleteAccountUiState

    /** Silme başarıyla tamamlandı; UI Login ekranına yönlendirir. */
    data object Success : DeleteAccountUiState

    /** Silme başarısız oldu; [message] kullanıcıya gösterilecek Türkçe hata metnidir. */
    data class Error(val message: String) : DeleteAccountUiState

    /**
     * Gereksinim 1.8: silme henüz yapılmadı — [DeleteAccountUseCase] cihazda aktif, ücretli
     * bir abonelik tespit etti. Diyalog bu durumda hesabı silmez; bunun yerine kullanıcıyı
     * Google Play abonelik yönetimine yönlendiren bir uyarı ve "yine de sil" onayı gösterir.
     */
    data class ActiveSubscriptionWarning(val tier: Tier, val expiresAtMillis: Long?) : DeleteAccountUiState
}
