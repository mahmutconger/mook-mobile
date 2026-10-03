package com.mcclabs.mook.domain.billing

/**
 * Paywall'ın NEDEN açıldığı. Limit sayfasından gelindiyse [reason] dolu olur ve Paywall başlığı
 * buna göre değişir (ör. "Beğeni hakkın bitti"); [preselectTrial] ise deneme teklifi olan paketi
 * önceden seçer (limit sayfasındaki "Standart'ı 3 gün ücretsiz dene" seçeneği).
 */
data class PaywallRequest(
    val reason: LimitReason? = null,
    val preselectTrial: Boolean = false,
)
