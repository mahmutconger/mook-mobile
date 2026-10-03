package com.mcclabs.mook.domain.billing

import kotlinx.coroutines.flow.StateFlow

/**
 * Dinamik Plan Sınırları — kademe → sınır tablosu.
 *
 * Tek Doğruluk Kaynağı Firestore'daki `config/plans` belgesidir (sunucu `limitsFor()` aynı
 * belgeyi kendi varsayılanlarının üzerine birleştirir). İstemci bu belgeyi açılışta okur,
 * cihazda önbelleğe alır ve tüm alan/arayüz mantığı [EntitlementState.limits] üzerinden bu
 * tabloya uyar (bkz. [PlanCatalogRepository]).
 */
data class PlanCatalog(val byTier: Map<Tier, PlanLimits>) {

    /** Kademenin sınırları; tabloda yoksa gömülü yedekteki değer. */
    fun limitsFor(tier: Tier): PlanLimits = byTier[tier] ?: BUNDLED.byTier.getValue(tier)

    companion object {
        /**
         * Gömülü YEDEK katalog: yalnızca sunucuya HİÇ ulaşılamamışken ve önbellek de yokken
         * (ilk açılış) kullanılır. Sunucu varsayılanlarıyla (`functions/src/planDefaults.ts`)
         * BİREBİR aynı olmalıdır; `functions/test/planCatalog.test.js` aşağıdaki işaretli bloğu
         * okuyup sunucu tablosuyla karşılaştırır — sapma CI'da yakalanır.
         */
        val BUNDLED: PlanCatalog = PlanCatalog(
            mapOf(
                // BEGIN-PLAN-CATALOG
                Tier.FREE to PlanLimits(dailyLikes = 10, dailyMessages = 50, dailyNewChats = 3, roomSlots = 1, roomSwitchesPerDay = 1, showsAds = true, freeRoam = false, incognito = false, likedMeUnlocksPerDay = 0, rewindsPerDay = 0, boostsPerMonth = 0),
                Tier.ECONOMY to PlanLimits(dailyLikes = 30, dailyMessages = 200, dailyNewChats = 10, roomSlots = 3, roomSwitchesPerDay = 3, showsAds = false, freeRoam = false, incognito = false, likedMeUnlocksPerDay = 0, rewindsPerDay = 0, boostsPerMonth = 0),
                Tier.STANDARD to PlanLimits(dailyLikes = 100, dailyMessages = null, dailyNewChats = null, roomSlots = 5, roomSwitchesPerDay = null, showsAds = false, freeRoam = false, incognito = false, likedMeUnlocksPerDay = 5, rewindsPerDay = 3, boostsPerMonth = 1),
                Tier.PREMIUM to PlanLimits(dailyLikes = null, dailyMessages = null, dailyNewChats = null, roomSlots = null, roomSwitchesPerDay = null, showsAds = false, freeRoam = true, incognito = true, likedMeUnlocksPerDay = null, rewindsPerDay = null, boostsPerMonth = 4),
                // END-PLAN-CATALOG
            ),
        )
    }
}

/**
 * Sunucudan gelen KISMİ sınırlar. [UNSET] (veya `null` mantıksal alan) "belgede yok" demektir ve
 * temel değer korunur; sayısal alanlarda `null` ise sunucunun "sınırsız" anlamına gelir.
 */
data class PlanLimitsOverride(
    val dailyLikes: Int? = UNSET,
    val dailyMessages: Int? = UNSET,
    val dailyNewChats: Int? = UNSET,
    val roomSlots: Int? = UNSET,
    val roomSwitchesPerDay: Int? = UNSET,
    val showsAds: Boolean? = null,
    val freeRoam: Boolean? = null,
    val incognito: Boolean? = null,
    val likedMeUnlocksPerDay: Int? = UNSET,
    val rewindsPerDay: Int? = UNSET,
    val boostsPerMonth: Int = UNSET,
) {
    companion object {
        /** "Alan belgede yok" işareti (sunucu negatif sınır kullanmaz). */
        const val UNSET: Int = Int.MIN_VALUE
    }
}

/** Uzak belgeyi temel katalogla birleştiren saf kurallar. */
object PlanCatalogMerger {

    /**
     * [remote] anahtarları küçük harfli kademe adlarıdır (`free`, `economy`, …). Tanınmayan
     * anahtarlar yok sayılır; eksik veya geçersiz (negatif) alanlarda [base] değeri korunur.
     */
    fun merge(base: PlanCatalog, remote: Map<String, PlanLimitsOverride>): PlanCatalog {
        val merged = Tier.entries.associateWith { tier ->
            val current = base.limitsFor(tier)
            val override = remote[tier.name.lowercase()] ?: return@associateWith current
            current.copy(
                dailyLikes = override.dailyLikes.or(current.dailyLikes),
                dailyMessages = override.dailyMessages.or(current.dailyMessages),
                dailyNewChats = override.dailyNewChats.or(current.dailyNewChats),
                roomSlots = override.roomSlots.or(current.roomSlots),
                roomSwitchesPerDay = override.roomSwitchesPerDay.or(current.roomSwitchesPerDay),
                showsAds = override.showsAds ?: current.showsAds,
                freeRoam = override.freeRoam ?: current.freeRoam,
                incognito = override.incognito ?: current.incognito,
                likedMeUnlocksPerDay = override.likedMeUnlocksPerDay.or(current.likedMeUnlocksPerDay),
                rewindsPerDay = override.rewindsPerDay.or(current.rewindsPerDay),
                boostsPerMonth = override.boostsPerMonth.takeIf { it != PlanLimitsOverride.UNSET && it >= 0 } ?: current.boostsPerMonth,
            )
        }
        return PlanCatalog(merged)
    }

    /** `null` = sınırsız (geçerli), [PlanLimitsOverride.UNSET] veya negatif = temel değer. */
    private fun Int?.or(base: Int?): Int? = when {
        this == null -> null
        this == PlanLimitsOverride.UNSET || this < 0 -> base
        else -> this
    }
}

/**
 * Plan kataloğunun kaynağı: açılışta önbellekten (yoksa gömülü yedekten) başlar, [refresh] ile
 * `config/plans` belgesini okur, birleştirir ve önbelleğe yazar.
 */
interface PlanCatalogRepository {
    val catalog: StateFlow<PlanCatalog>

    /** Sunucudan güncel kataloğu çeker. Hata fırlatmaz; başarısızlıkta son bilinen katalog kalır. */
    suspend fun refresh()
}
