package com.mcclabs.mook.feature.settings

import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.Tier

/**
 * Ayarlar'daki abonelik bilgilendirmelerinin GÖRÜNÜM modeli (iş kuralı ViewModel/sunucu
 * katmanında; arayüz yalnızca bu alanları çizer).
 *
 * @property scheduledTier Dönem sonunda geçilecek kademe (ertelenmiş düşürme); yoksa `null`.
 * @property scheduledAtMillis Geçişin yürürlüğe gireceği an; mağaza bildirmediyse `null`.
 * @property showBillingIssue Ödeme alınamadı: "Ödeme Yöntemini Güncelle" bağlantısı gösterilir.
 * @property paymentUpdateUrl Ödeme yöntemini güncelleme adresi (RevenueCat `managementURL`,
 *   yoksa mağazanın genel abonelik sayfası).
 */
data class SubscriptionStatusUi(
    val scheduledTier: Tier? = null,
    val scheduledAtMillis: Long? = null,
    val showBillingIssue: Boolean = false,
    val paymentUpdateUrl: String = PLAY_SUBSCRIPTIONS_FALLBACK_URL,
)

/** Google Play abonelik yönetimi (RevenueCat yönetim bağlantısı vermezse). */
const val PLAY_SUBSCRIPTIONS_FALLBACK_URL = "https://play.google.com/store/account/subscriptions"

/** [EntitlementState]'ten (RevenueCat `CustomerInfo` türevleri) Ayarlar bilgilendirmelerini üretir. */
object SubscriptionStatusPresenter {

    fun present(entitlement: EntitlementState, nowMillis: Long): SubscriptionStatusUi {
        if (!entitlement.isResolved) return SubscriptionStatusUi()
        val pending = entitlement.scheduledChange?.takeIf { it.isStillPending(entitlement.tier, nowMillis) }
        return SubscriptionStatusUi(
            scheduledTier = pending?.targetTier,
            // Mağaza yürürlük tarihini bildirmediyse mevcut dönemin bitişi kullanılır.
            scheduledAtMillis = pending?.let { it.effectiveAtMillis ?: entitlement.expiresAtMillis },
            // Ödeme sorunu yalnızca hâlâ ücretli bir kademedeyken anlamlıdır (ödemesiz süre).
            showBillingIssue = entitlement.billingIssueDetectedAtMillis != null && entitlement.tier != Tier.FREE,
            paymentUpdateUrl = entitlement.managementUrl?.takeIf { it.startsWith("https://") } ?: PLAY_SUBSCRIPTIONS_FALLBACK_URL,
        )
    }
}
