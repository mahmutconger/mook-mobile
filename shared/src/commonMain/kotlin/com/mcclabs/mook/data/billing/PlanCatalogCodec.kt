package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.PlanLimits
import com.mcclabs.mook.domain.billing.Tier

/**
 * Önbellek biçimi: her satır `kademe.alan=değer` (`null` = sınırsız). Bağımlılıksız ve sürüm
 * değişikliklerine dayanıklıdır: tanınmayan satırlar yok sayılır, eksik alanlar gömülü yedekten
 * tamamlanır. Bozuk bir önbellek ASLA uygulamayı çökertmez (`decode` `null` döner).
 */
internal object PlanCatalogCodec {

    fun encode(catalog: PlanCatalog): String = buildString {
        Tier.entries.forEach { tier ->
            val limits = catalog.limitsFor(tier)
            val prefix = tier.name.lowercase()
            fieldsOf(limits).forEach { (name, value) -> append("$prefix.$name=${value ?: "null"}\n") }
        }
    }

    fun decode(text: String?): PlanCatalog? {
        if (text.isNullOrBlank()) return null
        return try {
            val values = text.lineSequence()
                .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, value) -> key.trim() to value.trim() }
            val byTier = Tier.entries.associateWith { tier ->
                val base = PlanCatalog.BUNDLED.limitsFor(tier)
                val prefix = tier.name.lowercase()
                fun int(name: String, fallback: Int?): Int? = when (val raw = values["$prefix.$name"]) {
                    null -> fallback
                    "null" -> null
                    else -> raw.toIntOrNull() ?: fallback
                }
                fun bool(name: String, fallback: Boolean): Boolean = values["$prefix.$name"]?.toBooleanStrictOrNull() ?: fallback
                PlanLimits(
                    dailyLikes = int("dailyLikes", base.dailyLikes),
                    dailyMessages = int("dailyMessages", base.dailyMessages),
                    dailyNewChats = int("dailyNewChats", base.dailyNewChats),
                    roomSlots = int("roomSlots", base.roomSlots),
                    roomSwitchesPerDay = int("roomSwitchesPerDay", base.roomSwitchesPerDay),
                    showsAds = bool("showsAds", base.showsAds),
                    freeRoam = bool("freeRoam", base.freeRoam),
                    incognito = bool("incognito", base.incognito),
                    likedMeUnlocksPerDay = int("likedMeUnlocksPerDay", base.likedMeUnlocksPerDay),
                    rewindsPerDay = int("rewindsPerDay", base.rewindsPerDay),
                    boostsPerMonth = int("boostsPerMonth", base.boostsPerMonth) ?: base.boostsPerMonth,
                )
            }
            PlanCatalog(byTier)
        } catch (e: Exception) {
            null
        }
    }

    private fun fieldsOf(limits: PlanLimits): List<Pair<String, Any?>> = listOf(
        "dailyLikes" to limits.dailyLikes,
        "dailyMessages" to limits.dailyMessages,
        "dailyNewChats" to limits.dailyNewChats,
        "roomSlots" to limits.roomSlots,
        "roomSwitchesPerDay" to limits.roomSwitchesPerDay,
        "showsAds" to limits.showsAds,
        "freeRoam" to limits.freeRoam,
        "incognito" to limits.incognito,
        "likedMeUnlocksPerDay" to limits.likedMeUnlocksPerDay,
        "rewindsPerDay" to limits.rewindsPerDay,
        "boostsPerMonth" to limits.boostsPerMonth,
    )
}
