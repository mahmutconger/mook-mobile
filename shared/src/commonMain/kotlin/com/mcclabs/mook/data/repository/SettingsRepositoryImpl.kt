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
            val roomLanguageCode = runCatching { document.get<String>("roomLanguageCode") }.getOrNull()
            // A user who has never opened Filters has no stored range; fall back to defaults.
            MatchSettings(
                ageRangeStart = start ?: MatchSettings().ageRangeStart,
                ageRangeEnd = end ?: MatchSettings().ageRangeEnd,
                targetCountries = countries,
                roomLanguageCode = roomLanguageCode
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
            "targetCountries" to settings.targetCountries
        )
        settings.roomLanguageCode?.let { updateMap["roomLanguageCode"] = it }

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

    private val appLanguageFlow = MutableStateFlow("en")
    private var appLanguageLoaded = false

    override fun observeAppLanguage(): Flow<String> = appLanguageFlow.asStateFlow()

    override suspend fun getAppLanguage(): String {
        val userId = Firebase.auth.currentUser?.uid ?: return appLanguageFlow.value
        if (appLanguageLoaded) return appLanguageFlow.value

        val language = try {
            val document = appFirestore.collection("users").document(userId).get()
            runCatching { document.get<String>("appLanguage") }.getOrNull() ?: "en"
        } catch (e: Exception) {
            Log.e("Uygulama dili okunamadı, varsayılana dönülüyor", e)
            "en"
        }

        appLanguageFlow.value = language
        appLanguageLoaded = true
        return language
    }

    override suspend fun setAppLanguage(language: String) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        appFirestore.collection("users").document(userId).set(
            mapOf("appLanguage" to language),
            merge = true
        )
        appLanguageFlow.value = language
        appLanguageLoaded = true
    }

    override suspend fun getHasSeenLikedMeTutorial(): Boolean {
        val userId = Firebase.auth.currentUser?.uid ?: return false
        return try {
            val document = appFirestore.collection("users").document(userId).get()
            runCatching { document.get<Boolean>("hasSeenLikedMeTutorial") }.getOrNull() ?: false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun setHasSeenLikedMeTutorial(seen: Boolean) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        appFirestore.collection("users").document(userId).set(
            mapOf("hasSeenLikedMeTutorial" to seen),
            merge = true
        )
    }

    override suspend fun getHasSeenRoomSwitchInfo(): Boolean {
        val userId = Firebase.auth.currentUser?.uid ?: return false
        return try {
            val document = appFirestore.collection("users").document(userId).get()
            runCatching { document.get<Boolean>("hasSeenRoomSwitchInfo") }.getOrNull() ?: false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun setHasSeenRoomSwitchInfo(seen: Boolean) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        appFirestore.collection("users").document(userId).set(
            mapOf("hasSeenRoomSwitchInfo" to seen),
            merge = true
        )
    }

    override suspend fun getRoomLanguageCode(): String? {
        return getSettings().roomLanguageCode
    }

    override suspend fun setRoomLanguageCode(code: String) {
        saveSettings(getSettings().copy(roomLanguageCode = code))
    }
}
