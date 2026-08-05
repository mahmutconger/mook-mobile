package com.mcclabs.mook.data.repository

import com.mcclabs.mook.domain.model.MatchSettings
import com.mcclabs.mook.domain.repository.SettingsRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.mcclabs.mook.data.appFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mcclabs.mook.util.Log

/**
 * Stores the user's Discover filters on their own `users` document, so they survive
 * a restart and follow the account across devices.
 *
 * The in-memory [state] is a cache that lets Discover react to a save immediately
 * rather than waiting on a round trip.
 */
class SettingsRepositoryImpl : SettingsRepository {

    private val state = MutableStateFlow(MatchSettings())
    private var isLoaded = false

    override fun observeSettings(): Flow<MatchSettings> = state.asStateFlow()

    override suspend fun getSettings(): MatchSettings {
        val userId = Firebase.auth.currentUser?.uid ?: return state.value
        if (isLoaded) return state.value

        val settings = try {
            val document = appFirestore.collection("users").document(userId).get()
            val start = runCatching { document.get<Long>("ageRangeStart") }.getOrNull()?.toInt()
            val end = runCatching { document.get<Long>("ageRangeEnd") }.getOrNull()?.toInt()
            val countries = runCatching { document.get<List<String>>("targetCountries") }.getOrNull() ?: emptyList()
            val languages = runCatching { document.get<List<String>>("targetLanguages") }.getOrNull() ?: emptyList()
            // A user who has never opened Filters has no stored range; fall back to defaults.
            MatchSettings(
                ageRangeStart = start ?: MatchSettings().ageRangeStart,
                ageRangeEnd = end ?: MatchSettings().ageRangeEnd,
                targetCountries = countries,
                targetLanguages = languages
            )
        } catch (e: Exception) {
            Log.e("Filtreler okunamadı, varsayılana dönülüyor", e)
            MatchSettings()
        }

        state.value = settings
        isLoaded = true
        return settings
    }

    override suspend fun saveSettings(settings: MatchSettings) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        
        val updateMap = mutableMapOf<String, Any>(
            "ageRangeStart" to settings.ageRangeStart,
            "ageRangeEnd" to settings.ageRangeEnd,
            "targetCountries" to settings.targetCountries,
            "targetLanguages" to settings.targetLanguages
        )

        appFirestore.collection("users").document(userId).set(
            updateMap,
            merge = true
        )
        state.value = settings
        isLoaded = true
    }

    override suspend fun getDiscoverVisible(): Boolean {
        val userId = Firebase.auth.currentUser?.uid ?: return true
        return try {
            val document = appFirestore.collection("users").document(userId).get()
            // Absent field means the user has never toggled it — default to visible.
            runCatching { document.get<Boolean>("discoverVisible") }.getOrNull() ?: true
        } catch (e: Exception) {
            Log.e("Discover görünürlüğü okunamadı, varsayılana dönülüyor", e)
            true
        }
    }

    override suspend fun setDiscoverVisible(visible: Boolean) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        appFirestore.collection("users").document(userId).set(
            mapOf("discoverVisible" to visible),
            merge = true
        )
    }

    private val isDarkModeFlow = MutableStateFlow(false)
    private var isDarkModeLoaded = false

    override fun observeIsDarkMode(): Flow<Boolean> = isDarkModeFlow.asStateFlow()

    override suspend fun getIsDarkMode(): Boolean {
        val userId = Firebase.auth.currentUser?.uid ?: return isDarkModeFlow.value
        if (isDarkModeLoaded) return isDarkModeFlow.value

        val isDark = try {
            val document = appFirestore.collection("users").document(userId).get()
            runCatching { document.get<Boolean>("isDarkMode") }.getOrNull() ?: false
        } catch (e: Exception) {
            Log.e("Koyu tema tercihi okunamadı, varsayılana dönülüyor", e)
            false
        }

        isDarkModeFlow.value = isDark
        isDarkModeLoaded = true
        return isDark
    }

    override suspend fun setIsDarkMode(isDark: Boolean) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        appFirestore.collection("users").document(userId).set(
            mapOf("isDarkMode" to isDark),
            merge = true
        )
        isDarkModeFlow.value = isDark
        isDarkModeLoaded = true
    }
}
