package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.local.LocalKeyValueStore
import com.mcclabs.mook.domain.repository.ThemeRepository
import com.mcclabs.mook.domain.theme.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface LegacyThemePreferenceSource {
    suspend fun read(): Boolean?
}

/** Yerel palet hemen yayınlanır; disk yazmaları sıralanır ve eski sonuçlar seçimi ezmez. */
class ThemeRepositoryImpl(
    private val store: LocalKeyValueStore,
    private val legacySource: LegacyThemePreferenceSource = LegacyThemePreferenceSource { null },
) : ThemeRepository {
    private val state = MutableStateFlow<ThemePreference?>(null)
    override val preference = state.asStateFlow()
    private val stateMutex = Mutex()
    private val writeMutex = Mutex()
    private var hasStoredPreference = false
    private var selectionRevision = 0L
    private var persistedPreference = ThemePreference.System

    override suspend fun initialize() = stateMutex.withLock {
        if (state.value == null) {
            val stored = store.getString(PREFERENCE_KEY)
            hasStoredPreference = stored != null
            persistedPreference = ThemePreference.fromStorage(stored)
            state.value = persistedPreference
        }
    }

    override suspend fun setPreference(preference: ThemePreference) {
        val revision = stateMutex.withLock {
            selectionRevision += 1
            state.value = preference
            selectionRevision
        }
        writeMutex.withLock {
            if (stateMutex.withLock { revision != selectionRevision }) return@withLock
            try {
                store.putString(PREFERENCE_KEY, preference.storageKey)
                stateMutex.withLock {
                    persistedPreference = preference
                    hasStoredPreference = true
                }
            } catch (error: Exception) {
                stateMutex.withLock {
                    if (revision == selectionRevision) state.value = persistedPreference
                }
                throw error
            }
        }
    }

    override suspend fun migrateLegacyPreference() {
        initialize()
        if (stateMutex.withLock { hasStoredPreference || selectionRevision > 0 }) return
        val legacy = try {
            legacySource.read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        } ?: return
        writeMutex.withLock {
            // Disk yazması sürerken kullanıcı yeni paleti hemen seçebilir.
            if (stateMutex.withLock { hasStoredPreference || selectionRevision > 0 }) return@withLock
            val migrated = if (legacy) ThemePreference.Dark else ThemePreference.Light
            store.putString(PREFERENCE_KEY, migrated.storageKey)
            stateMutex.withLock {
                hasStoredPreference = true
                persistedPreference = migrated
                if (selectionRevision == 0L) state.value = migrated
            }
        }
    }

    companion object {
        const val PREFERENCE_KEY = "theme_preference"
    }
}
