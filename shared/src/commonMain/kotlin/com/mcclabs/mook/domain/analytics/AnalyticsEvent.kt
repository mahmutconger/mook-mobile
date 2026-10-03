package com.mcclabs.mook.domain.analytics

import com.mcclabs.mook.domain.billing.Feature
import com.mcclabs.mook.domain.billing.GateDecision
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.billing.RewardType
import com.mcclabs.mook.domain.billing.Tier

/**
 * Kapsamlı Analitik Olay Sözlüğü — para kazanma raporunun izlenmesini istediği kullanıcı
 * yolculuklarının TEK tanım noktası.
 *
 * Her olay Firebase Analytics'e [name] adıyla ve [parameters] ile gönderilir
 * (bkz. [AnalyticsRepository.track]). Kurallar:
 * - Olay adları en fazla 40 karakter, küçük harf ve alt çizgilidir; GA4'ün ayrılmış adları
 *   (ör. `purchase`, `ad_impression`) KULLANILMAZ.
 * - Parametre anahtarları hassas profil alanlarını çağrıştıramaz (bkz.
 *   [DefaultSensitiveAnalyticsKeyPolicy] — ör. "age" parçası içeren `stage`, `usage` gibi
 *   anahtarlar sessizce elenirdi; bu yüzden `step` kullanılır).
 * - Değerler String/Long/Boolean'dır; metin değerleri 100 karakterle sınırlandırılır.
 * - Kişisel veri (uid, e-posta, ad) ASLA parametre olarak gönderilmez.
 */
sealed interface AnalyticsEvent {
    val name: String
    val parameters: Map<String, Any?>

    // ── Kapı (FeatureGate) kararları ve limitler ─────────────────────────────

    /** Bir özelliğe erişim kararı verildi (izin / önce reklam / limit). */
    data class GateDecisionMade(
        val feature: Feature,
        val outcome: String,
        val tier: Tier,
        val limitReason: LimitReason? = null,
        val upgradeTo: Tier? = null,
        val rewardedAvailable: Boolean = false,
    ) : AnalyticsEvent {
        override val name = "gate_decision"
        override val parameters = mapOf(
            "feature" to feature.name.lowercase(),
            "outcome" to outcome,
            "tier" to tier.name.lowercase(),
            "limit_reason" to limitReason?.name?.lowercase(),
            "upgrade_to" to upgradeTo?.name?.lowercase(),
            "rewarded_available" to rewardedAvailable,
        )

        companion object {
            const val OUTCOME_ALLOWED = "allowed"
            const val OUTCOME_AD_FIRST = "ad_first"
            const val OUTCOME_LIMIT_REACHED = "limit_reached"

            /** [GateDecision]'ı olay parametrelerine çevirir. */
            fun from(feature: Feature, tier: Tier, decision: GateDecision): GateDecisionMade = when (decision) {
                GateDecision.Allowed -> GateDecisionMade(feature, OUTCOME_ALLOWED, tier)
                is GateDecision.AdFirst -> GateDecisionMade(feature, OUTCOME_AD_FIRST, tier)
                is GateDecision.LimitReached -> GateDecisionMade(
                    feature = feature,
                    outcome = OUTCOME_LIMIT_REACHED,
                    tier = tier,
                    limitReason = decision.reason,
                    upgradeTo = decision.upgradeTo,
                    rewardedAvailable = decision.rewarded != null,
                )
            }
        }
    }

    /** Kullanıcı bir günlük/aylık limite takıldı ve limit sayfası gösterildi. */
    data class LimitReached(
        val reason: LimitReason,
        val tier: Tier,
        val source: String,
        val upgradeAvailable: Boolean,
    ) : AnalyticsEvent {
        override val name = "limit_reached"
        override val parameters = mapOf(
            "limit_reason" to reason.name.lowercase(),
            "tier" to tier.name.lowercase(),
            "source" to source,
            "upgrade_available" to upgradeAvailable,
        )
    }

    // ── Reklamlar ───────────────────────────────────────────────────────────

    /** Reklam kullanıcıya gösterildi (tam ekran açıldı veya yerel reklam gösterim saydı). */
    data class AdShown(val placement: String, val format: AdFormat) : AnalyticsEvent {
        override val name = "ad_shown"
        override val parameters = mapOf("placement" to placement, "ad_type" to format.name.lowercase())
    }

    /** Reklam gösterilmeye uygundu ama hazır (yüklenmiş) bir reklam yoktu — kaçan fırsat. */
    data class AdNotReady(val placement: String, val format: AdFormat) : AnalyticsEvent {
        override val name = "ad_not_ready"
        override val parameters = mapOf("placement" to placement, "ad_type" to format.name.lowercase())
    }

    /** Reklam yüklenemedi veya gösterilemedi. */
    data class AdFailed(
        val placement: String,
        val format: AdFormat,
        val step: AdFailureStep,
        val errorCode: Int? = null,
        val errorMessage: String? = null,
    ) : AnalyticsEvent {
        override val name = "ad_failed"
        override val parameters = mapOf(
            "placement" to placement,
            "ad_type" to format.name.lowercase(),
            "step" to step.name.lowercase(),
            "error_code" to errorCode?.toLong(),
            "error_detail" to errorMessage?.take(MAX_TEXT_VALUE_LENGTH),
        )
    }

    /** Ödüllü reklam izlendi; [verified] sunucu (AdMob SSV) onayının gelip gelmediğidir. */
    data class RewardedEarned(val rewardType: RewardType, val placement: String, val verified: Boolean) : AnalyticsEvent {
        override val name = "rewarded_earned"
        override val parameters = mapOf(
            "reward_type" to rewardType.name.lowercase(),
            "placement" to placement,
            "verified" to verified,
        )
    }

    // ── Paywall ve satın alma adımları ──────────────────────────────────────

    /** Satın alma (Paywall) ekranı açıldı. */
    data class PaywallView(val source: String, val tier: Tier) : AnalyticsEvent {
        override val name = "paywall_view"
        override val parameters = mapOf("source" to source, "tier" to tier.name.lowercase())
    }

    /** Kullanıcı bir plan için satın alma akışını başlattı. */
    data class PurchaseStarted(val sku: String, val targetTier: Tier, val currentTier: Tier, val changeType: String) : AnalyticsEvent {
        override val name = "purchase_started"
        override val parameters = mapOf(
            "sku" to sku,
            "target_tier" to targetTier.name.lowercase(),
            "tier" to currentTier.name.lowercase(),
            "change_type" to changeType,
        )
    }

    /** Satın alma başarıyla tamamlandı. */
    data class PurchaseCompleted(val sku: String, val targetTier: Tier, val changeType: String) : AnalyticsEvent {
        override val name = "purchase_completed"
        override val parameters = mapOf(
            "sku" to sku,
            "target_tier" to targetTier.name.lowercase(),
            "change_type" to changeType,
        )
    }

    /** Kullanıcı mağaza ekranında satın almayı iptal etti. */
    data class PurchaseCancelled(val sku: String) : AnalyticsEvent {
        override val name = "purchase_cancelled"
        override val parameters = mapOf("sku" to sku)
    }

    /** Ödeme onay bekliyor (ör. nakit/kart onayı gecikmeli). */
    data class PurchasePending(val sku: String) : AnalyticsEvent {
        override val name = "purchase_pending"
        override val parameters = mapOf("sku" to sku)
    }

    /** Plan değişikliği mevcut dönem sonuna ertelendi (düşürme). */
    data class PurchaseChangeScheduled(val sku: String, val targetTier: Tier) : AnalyticsEvent {
        override val name = "purchase_change_scheduled"
        override val parameters = mapOf("sku" to sku, "target_tier" to targetTier.name.lowercase())
    }

    /** Satın alma bir hatayla sonuçlandı; [error] hata türünün kısa adıdır. */
    data class PurchaseFailed(val sku: String, val error: String) : AnalyticsEvent {
        override val name = "purchase_failed"
        override val parameters = mapOf("sku" to sku, "error" to error.take(MAX_TEXT_VALUE_LENGTH))
    }

    /** "Satın alımları geri yükle" başlatıldı. */
    data object PurchaseRestoreStarted : AnalyticsEvent {
        override val name = "purchase_restore_started"
        override val parameters: Map<String, Any?> = emptyMap()
    }

    /** Geri yükleme sonucu; [result] = completed / pending / scheduled / cancelled / failed. */
    data class PurchaseRestoreFinished(val result: String) : AnalyticsEvent {
        override val name = "purchase_restore_finished"
        override val parameters = mapOf("result" to result)
    }

    // ── Abonelik durumu ─────────────────────────────────────────────────────

    /** Kullanıcının etkin abonelik kademesi değişti (satın alma, yükseltme, düşürme, sona erme). */
    data class SubscriptionChanged(val fromTier: Tier, val toTier: Tier) : AnalyticsEvent {
        override val name = "subscription_changed"
        override val parameters = mapOf(
            "from_tier" to fromTier.name.lowercase(),
            "to_tier" to toTier.name.lowercase(),
            "direction" to directionOf(fromTier, toTier),
        )

        companion object {
            /** Değişimin yönü: new / ended / upgrade / downgrade. */
            fun directionOf(from: Tier, to: Tier): String = when {
                from == Tier.FREE -> "new"
                to == Tier.FREE -> "ended"
                to.ordinal > from.ordinal -> "upgrade"
                else -> "downgrade"
            }
        }
    }

    companion object {
        /** GA4 metin parametre değerlerinin azami uzunluğu. */
        const val MAX_TEXT_VALUE_LENGTH = 100
    }
}

/** Bir reklam hatasının hangi adımda oluştuğu. */
enum class AdFailureStep { LOAD, SHOW }
