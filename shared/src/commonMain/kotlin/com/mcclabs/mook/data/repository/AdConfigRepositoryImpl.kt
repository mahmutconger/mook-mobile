package com.mcclabs.mook.data.repository

import com.mcclabs.mook.domain.billing.AdConfigRepository
import com.mcclabs.mook.domain.billing.AdRemoteConfig
import com.mcclabs.mook.util.Log
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * [AdConfigRepository]'nin Firebase Remote Config üzerinden uygulaması.
 *
 * @param minimumFetchInterval İki sunucu çekimi arasındaki en kısa süre. Kill-switch'in çalışan
 *   oturumlara da ulaşması için kısa tutulur (varsayılan 30 dk); Remote Config bu süre dolmadan
 *   yapılan çağrıları önbellekten yanıtlar.
 */
class AdConfigRepositoryImpl(
    private val minimumFetchInterval: Duration = 30.minutes,
) : AdConfigRepository {

    private val mutableConfig = MutableStateFlow(AdRemoteConfig.DEFAULT)
    override val config: StateFlow<AdRemoteConfig> = mutableConfig.asStateFlow()
    private var initialized = false

    override suspend fun refresh() {
        try {
            val remoteConfig = Firebase.remoteConfig
            if (!initialized) {
                val intervalSeconds = minimumFetchInterval.inWholeSeconds
                remoteConfig.settings { minimumFetchIntervalInSeconds = intervalSeconds }
                remoteConfig.setDefaults(*AdRemoteConfig.DEFAULTS.toTypedArray())
                initialized = true
                // Önceki oturumda etkinleştirilmiş değerler ağ beklenmeden hemen uygulanır.
                mutableConfig.value = read()
            }
            remoteConfig.fetchAndActivate()
            mutableConfig.value = read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("Reklam ayarları (Remote Config) güncellenemedi; son bilinen değerler kullanılıyor", error)
        }
    }

    private fun read(): AdRemoteConfig {
        val remoteConfig = Firebase.remoteConfig
        return AdRemoteConfig.fromRemote(
            boolean = { key -> runCatching { remoteConfig.getValue(key).asBoolean() }.getOrNull() },
            long = { key -> runCatching { remoteConfig.getValue(key).asLong() }.getOrNull() },
        )
    }
}
