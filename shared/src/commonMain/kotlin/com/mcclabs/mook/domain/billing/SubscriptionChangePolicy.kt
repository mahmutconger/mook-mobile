package com.mcclabs.mook.domain.billing

/**
 * Aktif yetkinin (entitlement) hangi mağaza/kaynak tarafından sağlandığı.
 *
 * Plan değişikliği (Google Play `SubscriptionUpdateParams`) yalnızca Play'de satın alınmış bir
 * aboneliğin ÜZERİNE uygulanabilir. Promosyonla verilmiş bir yetkinin değiştirilecek bir Play
 * satın alması yoktur; başka bir mağazadaki (ör. App Store) abonelik ise bu uygulamadan hiç
 * değiştirilemez.
 */
enum class SubscriptionSource { PLAY_STORE, PROMOTIONAL, OTHER_STORE }

/**
 * Platformdan bağımsız değiştirme (replacement) politikası. Android tarafında RevenueCat'in
 * `StoreReplacementMode` değerlerine birebir eşlenir (bkz. `PlatformSubscriptionRepository.android.kt`).
 */
enum class ReplacementPolicy {
    /**
     * Yeni plan HEMEN başlar; eski plandan kalan kullanılmamış tutar, Google Play tarafından yeni
     * planda ek süre olarak kredilendirilir (Play: `WITH_TIME_PRORATION`). Kullanıcı aynı anda
     * iki abonelik için ASLA ödeme yapmaz.
     */
    IMMEDIATE_WITH_TIME_PRORATION,

    /**
     * Mevcut plan dönem sonuna kadar sürer; yeni plan ve fiyatı yenileme tarihinde başlar
     * (Play: `DEFERRED`). Kullanıcı zaten ödediği dönemin avantajlarını kaybetmez.
     */
    DEFERRED,
}

/** Bir plan değişikliğinin ürün açısından türü — arayüz metni ve analitik için kullanılır. */
enum class SubscriptionChangeType {
    /** Daha üst bir kademeye geçiş (ör. Ekonomik → Standart). */
    UPGRADE,

    /** Daha alt bir kademeye geçiş (ör. Premium → Ekonomik). */
    DOWNGRADE,

    /** Aynı kademede temel plan (fatura dönemi) değişikliği (ör. Standart aylık → Standart yıllık). */
    CROSSGRADE,
}

/** [SubscriptionChangePolicy.evaluate] sonucu: seçilen paket için hangi satın alma akışının kullanılacağı. */
sealed interface SubscriptionChange {

    /** Değiştirilecek aktif bir Play aboneliği yok — normal (yeni) satın alma akışı. */
    data object NewPurchase : SubscriptionChange

    /**
     * Mevcut Play aboneliği yeni paketle DEĞİŞTİRİLMELİ (çift abonelik oluşmaması için zorunlu).
     *
     * @property oldSubscriptionId Değiştirilecek aboneliğin Play abonelik kimliği (temel plan
     *   kimliği OLMADAN, ör. `wm_economy`). RevenueCat bu kimlikle cihazdaki eski satın almayı
     *   ve onun `purchaseToken`'ını kendisi bulur ve Play'e `oldPurchaseToken` olarak iletir.
     */
    data class Replace(
        val oldSubscriptionId: String,
        val type: SubscriptionChangeType,
        val policy: ReplacementPolicy,
    ) : SubscriptionChange

    /** Bu geçiş uygulama içinden yapılamaz; [reason] kullanıcıya gösterilecek sebebi belirler. */
    data class NotAllowed(val reason: Reason) : SubscriptionChange {
        enum class Reason {
            /** Seçilen paket zaten aktif olan paketin kendisi. */
            ALREADY_ACTIVE,

            /** Aktif abonelik henüz canlı olarak doğrulanamadı (ör. çevrimdışı soğuk başlangıç). */
            ACTIVE_PLAN_UNKNOWN,

            /** Aktif abonelik Google Play dışında (ör. App Store) yönetiliyor. */
            MANAGED_OUTSIDE_PLAY,

            /** Hedef paketin mağaza ürün kimliği bilinmiyor; güvenli bir değiştirme kurulamaz. */
            TARGET_UNAVAILABLE,
        }
    }
}

/**
 * Bir hedef paketin mevcut aboneliğe göre yeni satın alma mı, yükseltme mi, düşürme mi yoksa
 * çapraz geçiş mi olduğuna karar veren SAF (yan etkisiz) kural seti.
 *
 * Paywall'daki `canPurchase` kararı ve Android'deki `purchase()` aynı fonksiyonu kullanır;
 * böylece arayüzün "satın alınabilir" dediği her geçiş, mağazaya da aynı kuralla gönderilir.
 *
 * Kurallar:
 * - Ücretli kademe yoksa ya da yetki promosyondan geliyorsa → [SubscriptionChange.NewPurchase].
 * - Hedef kademe daha yüksekse → UPGRADE, [ReplacementPolicy.IMMEDIATE_WITH_TIME_PRORATION]
 *   (fatura dönemi ne olursa olsun; kullanıcı yeni avantajları hemen almalı).
 * - Hedef kademe daha düşükse → DOWNGRADE, [ReplacementPolicy.DEFERRED] (ödenmiş dönem korunur).
 * - Aynı kademede aylıktan yıllığa → CROSSGRADE, hemen ve süre oranlı.
 * - Aynı kademede yıllıktan aylığa, ya da mevcut dönem bilinmiyorsa → CROSSGRADE, ertelenmiş
 *   (belirsizlikte kullanıcıdan beklenmedik bir anlık ücret ALINMAZ).
 * - Aynı abonelik + aynı temel plan → [SubscriptionChange.NotAllowed.Reason.ALREADY_ACTIVE].
 *
 * Kademe sırası [Tier] enum sırasıdır (FREE < ECONOMY < STANDARD < PREMIUM); dönem sırası
 * [Period] enum sırasıdır (MONTHLY < YEARLY).
 */
object SubscriptionChangePolicy {

    private const val BASE_PLAN_SEPARATOR = ':'

    /**
     * @param current Abonelik durumunun anlık görüntüsü.
     * @param target Kullanıcının seçtiği paket.
     * @param availablePlans Paywall'da listelenen tüm paketler; mevcut temel planın fatura
     *   dönemini (aylık/yıllık) çözmek için kullanılır.
     */
    fun evaluate(
        current: EntitlementState,
        target: PlanPackage,
        availablePlans: List<PlanPackage> = emptyList(),
    ): SubscriptionChange {
        // Plan durumu hiç çözülmediyse "ücretsiz" varsayımı çift aboneliğe yol açabilir.
        if (!current.isResolved) return notAllowed(SubscriptionChange.NotAllowed.Reason.ACTIVE_PLAN_UNKNOWN)
        if (current.tier == Tier.FREE) return SubscriptionChange.NewPurchase

        when (current.activeSubscriptionSource) {
            SubscriptionSource.PROMOTIONAL -> return SubscriptionChange.NewPurchase
            SubscriptionSource.OTHER_STORE -> return notAllowed(SubscriptionChange.NotAllowed.Reason.MANAGED_OUTSIDE_PLAY)
            // Kaynak bilinmiyor = canlı CustomerInfo henüz gelmedi (yalnızca önbellek var).
            null -> return notAllowed(SubscriptionChange.NotAllowed.Reason.ACTIVE_PLAN_UNKNOWN)
            SubscriptionSource.PLAY_STORE -> Unit
        }

        val activeProduct = current.activeProductIdentifier?.takeIf { it.isNotBlank() }
            ?: return notAllowed(SubscriptionChange.NotAllowed.Reason.ACTIVE_PLAN_UNKNOWN)
        if (target.productIdentifier.isBlank()) {
            return notAllowed(SubscriptionChange.NotAllowed.Reason.TARGET_UNAVAILABLE)
        }

        val currentSubscriptionId = activeProduct.substringBefore(BASE_PLAN_SEPARATOR)
        val currentBasePlan = current.activeBasePlanIdentifier?.takeIf { it.isNotBlank() }
            ?: activeProduct.substringAfter(BASE_PLAN_SEPARATOR, missingDelimiterValue = "").ifBlank { null }
        val targetSubscriptionId = target.productIdentifier.substringBefore(BASE_PLAN_SEPARATOR)
        val targetBasePlan = target.productIdentifier.substringAfter(BASE_PLAN_SEPARATOR, missingDelimiterValue = "").ifBlank { null }

        if (currentSubscriptionId == targetSubscriptionId && currentBasePlan != null && currentBasePlan == targetBasePlan) {
            return notAllowed(SubscriptionChange.NotAllowed.Reason.ALREADY_ACTIVE)
        }

        return when {
            target.tier.ordinal > current.tier.ordinal -> SubscriptionChange.Replace(
                oldSubscriptionId = currentSubscriptionId,
                type = SubscriptionChangeType.UPGRADE,
                policy = ReplacementPolicy.IMMEDIATE_WITH_TIME_PRORATION,
            )
            target.tier.ordinal < current.tier.ordinal -> SubscriptionChange.Replace(
                oldSubscriptionId = currentSubscriptionId,
                type = SubscriptionChangeType.DOWNGRADE,
                policy = ReplacementPolicy.DEFERRED,
            )
            else -> {
                val currentPeriod = currentBasePlan?.let { basePlan ->
                    availablePlans.firstOrNull { plan ->
                        plan.productIdentifier == "$currentSubscriptionId$BASE_PLAN_SEPARATOR$basePlan"
                    }?.period
                }
                val immediate = currentPeriod != null && target.period.ordinal > currentPeriod.ordinal
                SubscriptionChange.Replace(
                    oldSubscriptionId = currentSubscriptionId,
                    type = SubscriptionChangeType.CROSSGRADE,
                    policy = if (immediate) ReplacementPolicy.IMMEDIATE_WITH_TIME_PRORATION else ReplacementPolicy.DEFERRED,
                )
            }
        }
    }

    /** Kısayol: [SubscriptionChange] satın alma akışını başlatabilir mi? */
    fun isPurchasable(change: SubscriptionChange?): Boolean =
        change is SubscriptionChange.NewPurchase || change is SubscriptionChange.Replace

    private fun notAllowed(reason: SubscriptionChange.NotAllowed.Reason) = SubscriptionChange.NotAllowed(reason)
}
