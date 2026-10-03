package com.mcclabs.mook.domain.billing

/**
 * Reklam sayaçlarının (profil ziyareti, günlük upsell zamanı) yerel kaynağı. Sayaçlar
 * oturum açmış kullanıcıya göre ayrıştırılır; aynı cihazda başka bir hesaba geçmek sayaçları
 * devralmaz. Sunucuya yazılmaz — yalnızca reklam/upsell sıklığını belirler.
 */
interface AdCounterRepository {

    /** Profil ziyaret sayacını bir artırır ve yeni değeri döner. */
    suspend fun incrementProfileVisits(): Int

    /** Bir profil ziyareti reklamı gösterildiğinde sayacı sıfırlar. */
    suspend fun resetProfileVisits()

    /** Günlük upsell kartının en son gösterildiği an (epoch ms); hiç gösterilmediyse `null`. */
    suspend fun lastUpsellTimestamp(): Long?

    /** Günlük upsell kartının gösterildiği anı kaydeder. */
    suspend fun setLastUpsellTimestamp(timestampMillis: Long)
}
