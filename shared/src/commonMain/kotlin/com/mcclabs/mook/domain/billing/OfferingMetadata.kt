package com.mcclabs.mook.domain.billing

/**
 * RevenueCat Teklif (Offering) Meta Veri Şeması — Paywall rozetleri koda gömülmez, RevenueCat
 * panelindeki "default" teklifin `metadata` JSON'undan okunur:
 *
 * ```json
 * {
 *   "most_popular_package": "$rc_annual",   // (öncelikli) "En Popüler" rozeti alacak paket kimliği
 *   "most_popular_tier": "standard"          // paket verilmezse rozet bu kademenin paketlerine
 * }
 * ```
 * İki anahtar da yoksa hiçbir pakete rozet verilmez. Tanınmayan/bozuk değerler sessizce yok
 * sayılır (uygulama asla çökmez).
 */
data class PaywallMetadata(
    val mostPopularPackageId: String? = null,
    val mostPopularTier: Tier? = null,
) {
    /** Bu paket "En Popüler" rozeti almalı mı? Paket kimliği verildiyse yalnızca ona bakılır. */
    fun isMostPopular(packageId: String, tier: Tier): Boolean =
        if (mostPopularPackageId != null) packageId == mostPopularPackageId else tier == mostPopularTier

    /** Kademe seçicide rozetin gösterileceği kademe (paket kimliğinden veya doğrudan kademeden). */
    fun badgeTier(packages: List<PlanPackage>): Tier? =
        mostPopularPackageId?.let { id -> packages.firstOrNull { it.identifier == id }?.tier } ?: mostPopularTier
}

object OfferingMetadataParser {
    const val KEY_MOST_POPULAR_PACKAGE = "most_popular_package"
    const val KEY_MOST_POPULAR_TIER = "most_popular_tier"

    /** RevenueCat `Offering.metadata` haritasını güvenli biçimde ayrıştırır. */
    fun parse(raw: Map<String, Any?>?): PaywallMetadata {
        if (raw.isNullOrEmpty()) return PaywallMetadata()
        val packageId = (raw[KEY_MOST_POPULAR_PACKAGE] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val tier = (raw[KEY_MOST_POPULAR_TIER] as? String)?.trim()?.uppercase()
            ?.let { name -> Tier.entries.firstOrNull { it.name == name } }
            ?.takeIf { it != Tier.FREE }
        return PaywallMetadata(mostPopularPackageId = packageId, mostPopularTier = tier)
    }
}
