package com.mcclabs.mook.domain.billing

/** Limit sayfasında sunulan ücretsiz deneme teklifi. */
data class TrialOffer(val plan: PlanPackage, val trialDays: Int)

/**
 * "Standart'ı 3 gün ücretsiz dene" seçeneğinin gösterilip gösterilmeyeceğine karar verir.
 *
 * Yalnızca şu koşulların HEPSİ sağlanırsa teklif döner:
 * - Plan durumu doğrulanmış ve kullanıcı Ücretsiz plandadır (ücretli abone denemeye yönlendirilmez).
 * - RevenueCat teklifinde Standart kademede ücretsiz denemesi olan bir paket vardır. Bu bayrak
 *   mağaza (Play Billing) uygunluğu ve cihaz başına deneme defteri (çoklu hesap suistimali)
 *   kontrolünden SONRA gelir — uygun olmayan kullanıcıda zaten `false`dur.
 * - Mağaza giriş fiyatı uygunluğu ([SubscriptionRepository.introEligibility]) reddetmez.
 * Ağ hatası dahil her belirsizlikte teklif GÖSTERİLMEZ (yanlış vaat vermemek için).
 */
class TrialOfferUseCase(private val subscriptions: SubscriptionRepository) {

    suspend operator fun invoke(): TrialOffer? {
        val entitlement = subscriptions.state.value
        if (!entitlement.isResolved || entitlement.tier != Tier.FREE) return null
        val offer = subscriptions.offerings().getOrNull() ?: return null
        val plan = offer.packages
            .filter { it.tier == Tier.STANDARD && it.hasFreeTrialAvailable }
            .minByOrNull { it.period.ordinal } ?: return null
        if (plan.productIdentifier.isNotBlank()) {
            val eligible = runCatching { subscriptions.introEligibility(listOf(plan.productIdentifier)) }
                .getOrNull()?.get(plan.productIdentifier) ?: false
            if (!eligible) return null
        }
        return TrialOffer(plan = plan, trialDays = plan.freeTrialDays ?: DEFAULT_TRIAL_DAYS)
    }

    companion object {
        /** Mağaza deneme süresini bildirmezse varsayılan (Play Console'daki `try` teklifi: 7 gün). */
        const val DEFAULT_TRIAL_DAYS = 7
    }
}
