package com.mcclabs.mook.data.billing

import com.mcclabs.mook.data.appFirestore
import com.mcclabs.mook.data.local.LocalKeyValueStore
import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.PlanCatalogMerger
import com.mcclabs.mook.domain.billing.PlanCatalogRepository
import com.mcclabs.mook.domain.billing.PlanLimitsOverride
import com.mcclabs.mook.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * [PlanCatalogRepository]'nin Firestore + yerel önbellek uygulaması (uygulama ömrü boyunca tekil).
 *
 * Sıra: gömülü yedek → (hemen ardından) önbellek → `config/plans` (açılış/giriş sonrası [refresh]).
 * Belge okunamaz veya bozuksa son bilinen katalog korunur; kullanıcı ASLA "boş" sınırlarla kalmaz.
 */
class PlanCatalogRepositoryImpl(
    private val cache: LocalKeyValueStore,
) : PlanCatalogRepository {

    private val mutableCatalog = MutableStateFlow(PlanCatalog.BUNDLED)
    override val catalog: StateFlow<PlanCatalog> = mutableCatalog.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var cacheLoaded = false

    init {
        scope.launch { loadCacheOnce() }
    }

    private suspend fun loadCacheOnce() = mutex.withLock {
        if (cacheLoaded) return@withLock
        cacheLoaded = true
        PlanCatalogCodec.decode(cache.getString(CACHE_KEY))?.let { cached ->
            // Sunucudan daha yeni bir katalog geldiyse önbellek onu ezmez.
            if (mutableCatalog.value === PlanCatalog.BUNDLED) mutableCatalog.value = cached
        }
    }

    override suspend fun refresh() {
        loadCacheOnce()
        try {
            val document = appFirestore.collection("config").document("plans").get()
            if (!document.exists) {
                Log.e("config/plans belgesi yok; son bilinen plan kataloğu kullanılıyor")
                return
            }
            val remote = document.data(MapSerializer(String.serializer(), PlanLimitsDto.serializer()))
                .mapValues { (_, dto) -> dto.toOverride() }
            val merged = PlanCatalogMerger.merge(PlanCatalog.BUNDLED, remote)
            mutableCatalog.value = merged
            cache.putString(CACHE_KEY, PlanCatalogCodec.encode(merged))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("Plan kataloğu (config/plans) okunamadı; son bilinen katalog kullanılıyor", error)
        }
    }

    private companion object {
        const val CACHE_KEY = "plan_catalog_v1"
    }
}

/**
 * `config/plans` içindeki tek kademe. Varsayılan değerler "alan belgede yok" işaretidir
 * ([PlanLimitsOverride.UNSET]); açık `null` sunucunun "sınırsız" anlamına gelir.
 */
@Serializable
private data class PlanLimitsDto(
    val dailyLikes: Int? = PlanLimitsOverride.UNSET,
    val dailyMessages: Int? = PlanLimitsOverride.UNSET,
    val dailyNewChats: Int? = PlanLimitsOverride.UNSET,
    val roomSlots: Int? = PlanLimitsOverride.UNSET,
    val roomSwitchesPerDay: Int? = PlanLimitsOverride.UNSET,
    val showsAds: Boolean? = null,
    val freeRoam: Boolean? = null,
    val incognito: Boolean? = null,
    val likedMeUnlocksPerDay: Int? = PlanLimitsOverride.UNSET,
    val rewindsPerDay: Int? = PlanLimitsOverride.UNSET,
    val boostsPerMonth: Int = PlanLimitsOverride.UNSET,
) {
    fun toOverride() = PlanLimitsOverride(
        dailyLikes, dailyMessages, dailyNewChats, roomSlots, roomSwitchesPerDay,
        showsAds, freeRoam, incognito, likedMeUnlocksPerDay, rewindsPerDay, boostsPerMonth,
    )
}
