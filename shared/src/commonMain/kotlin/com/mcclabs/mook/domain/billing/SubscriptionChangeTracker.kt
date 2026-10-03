package com.mcclabs.mook.domain.billing

import com.mcclabs.mook.domain.analytics.AnalyticsEvent
import com.mcclabs.mook.domain.analytics.AnalyticsRepository

/**
 * Abonelik kademesindeki geçişleri tespit eden SAF durum makinesi (`subscription_changed`).
 *
 * Kurallar:
 * - Yalnızca RevenueCat'te TANIMLI (anonim olmayan) bir kullanıcı için çalışır. Kullanıcı
 *   değişince (giriş/çıkış) taban çizgisi sıfırlanır — böylece çıkış sırasında kademenin
 *   FREE'ye dönmesi yanlışlıkla "abonelik bitti" olarak SAYILMAZ.
 * - Aynı kullanıcı için ilk çözülmüş durum taban çizgisidir; olay üretmez.
 * - Sonraki her farklı kademe bir [AnalyticsEvent.SubscriptionChanged] üretir.
 */
class SubscriptionChangeDetector {
    private var baselineUserId: String? = null
    private var baselineTier: Tier? = null

    fun onState(userId: String?, state: EntitlementState): AnalyticsEvent.SubscriptionChanged? {
        val identified = userId?.takeUnless { it.isBlank() || it.startsWith(ANONYMOUS_PREFIX) }
        if (identified == null) {
            baselineUserId = null
            baselineTier = null
            return null
        }
        if (!state.isResolved) return null
        if (identified != baselineUserId) {
            baselineUserId = identified
            baselineTier = state.tier
            return null
        }
        val previous = baselineTier
        baselineTier = state.tier
        return if (previous != null && previous != state.tier) {
            AnalyticsEvent.SubscriptionChanged(fromTier = previous, toTier = state.tier)
        } else {
            null
        }
    }

    private companion object {
        const val ANONYMOUS_PREFIX = "\$RCAnonymousID:"
    }
}

/**
 * Uygulama ömrü boyunca abonelik durumunu izler ve kademe değişimlerini analitiğe bildirir.
 * `App.kt` tarafından bir kez başlatılır.
 */
class SubscriptionChangeTracker(
    private val subscriptions: SubscriptionRepository,
    private val analytics: AnalyticsRepository,
) {
    suspend fun run() {
        val detector = SubscriptionChangeDetector()
        subscriptions.state.collect { state ->
            detector.onState(subscriptions.currentIdentifiedUserId(), state)?.let(analytics::track)
        }
    }
}
