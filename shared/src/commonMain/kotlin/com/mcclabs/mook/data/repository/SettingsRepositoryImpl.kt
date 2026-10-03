package com.mcclabs.mook.data.repository

import com.mcclabs.mook.domain.repository.IncognitoUpdateResult
import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.data.room.toRoomSwitchDomainError
import kotlinx.coroutines.CancellationException
import com.mcclabs.mook.domain.model.MatchSettings
import com.mcclabs.mook.domain.repository.SettingsRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.mcclabs.mook.data.appFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mcclabs.mook.util.Log
import kotlinx.serialization.Serializable

/**
 * Stores the user's Discover filters on their own `users` document, so they survive
 * a restart and follow the account across devices.
 *
 * The in-memory [state] is a cache that lets Discover react to a save immediately
 * rather than waiting on a round trip.
 */
class SettingsRepositoryImpl : SettingsRepository {

    private val state = MutableStateFlow(MatchSettings())
    // Repositories are Koin singletons and outlive a logout/login navigation cycle.
    // Cache ownership must therefore be tied to a Firebase uid, never just a boolean.
    private var settingsLoadedForUid: String? = null

    override fun observeSettings(): Flow<MatchSettings> = state.asStateFlow()

    override suspend fun getSettings(): MatchSettings {
        val userId = Firebase.auth.currentUser?.uid ?: return MatchSettings()
        if (settingsLoadedForUid == userId) return state.value

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
        settingsLoadedForUid = userId
        return settings
    }

    override suspend fun saveSettings(settings: MatchSettings) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        
        val updateMap = mutableMapOf<String, Any>(
            "ageRangeStart" to settings.ageRangeStart,
            "ageRangeEnd" to settings.ageRangeEnd,
            "targetCountries" to settings.targetCountries
        )
        appFirestore.collection("users").document(userId).set(
            updateMap,
            merge = true
        )
        state.value = settings
        settingsLoadedForUid = userId
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

    override suspend fun getIncognito(): Boolean {
        val userId = Firebase.auth.currentUser?.uid ?: return false
        return try {
            val document = appFirestore.collection("users").document(userId).get()
            runCatching { document.get<Boolean?>("incognito") }.getOrNull() ?: false
        } catch (e: Exception) {
            Log.e("Gizli mod durumu okunamadı, kapalı varsayılıyor", e)
            false
        }
    }

    override suspend fun setIncognito(enabled: Boolean): IncognitoUpdateResult = try {
        val response = appHttpsCallable("setIncognito")
            .invoke(IncognitoRequest(enabled))
            .data<IncognitoResponse>()
        IncognitoUpdateResult.Updated(response.enabled)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e("Gizli mod güncellenemedi", error)
        if (error.message?.contains("upgrade-required") == true) IncognitoUpdateResult.UpgradeRequired
        else IncognitoUpdateResult.Failed
    }

    override suspend fun setDiscoverVisible(visible: Boolean) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        appFirestore.collection("users").document(userId).set(
            mapOf("discoverVisible" to visible),
            merge = true
        )
    }

    private val isDarkModeFlow = MutableStateFlow(false)
    private var darkModeLoadedForUid: String? = null

    override fun observeIsDarkMode(): Flow<Boolean> = isDarkModeFlow.asStateFlow()

    override suspend fun getIsDarkMode(): Boolean {
        val userId = Firebase.auth.currentUser?.uid ?: return false
        if (darkModeLoadedForUid == userId) return isDarkModeFlow.value

        val isDark = try {
            val document = appFirestore.collection("users").document(userId).get()
            runCatching { document.get<Boolean>("isDarkMode") }.getOrNull() ?: false
        } catch (e: Exception) {
            Log.e("Koyu tema tercihi okunamadı, varsayılana dönülüyor", e)
            false
        }

        isDarkModeFlow.value = isDark
        darkModeLoadedForUid = userId
        return isDark
    }

    override suspend fun setIsDarkMode(isDark: Boolean) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        appFirestore.collection("users").document(userId).set(
            mapOf("isDarkMode" to isDark),
            merge = true
        )
        isDarkModeFlow.value = isDark
        darkModeLoadedForUid = userId
    }

    private val appLanguageFlow = MutableStateFlow("en")
    private var appLanguageLoadedForUid: String? = null

    override fun observeAppLanguage(): Flow<String> = appLanguageFlow.asStateFlow()

    override suspend fun getAppLanguage(): String {
        val userId = Firebase.auth.currentUser?.uid ?: return "en"
        if (appLanguageLoadedForUid == userId) return appLanguageFlow.value

        val language = try {
            val document = appFirestore.collection("users").document(userId).get()
            runCatching { document.get<String>("appLanguage") }.getOrNull() ?: "en"
        } catch (e: Exception) {
            Log.e("Uygulama dili okunamadı, varsayılana dönülüyor", e)
            "en"
        }

        appLanguageFlow.value = language
        appLanguageLoadedForUid = userId
        return language
    }

    override suspend fun setAppLanguage(language: String) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        appFirestore.collection("users").document(userId).set(
            mapOf("appLanguage" to language),
            merge = true
        )
        appLanguageFlow.value = language
        appLanguageLoadedForUid = userId
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
        // Membership and daily room-switch quotas are enforced by Cloud Functions.
        // Do not write this protected field directly: a modified client could otherwise
        // join every room without spending its tier allowance.
        try {
            appHttpsCallable("switchRoom")
                .invoke(SwitchRoomRequest(code))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Günlük hak dolduysa genel bir hata yerine alan istisnası fırlatılır; sunum katmanı
            // bunu limit sayfasına yönlendirir (bkz. DailyRoomChangeLimitReachedException).
            throw error.toRoomSwitchDomainError()
        }
        state.value = getSettings().copy(roomLanguageCode = code)
    }
}

@Serializable
private data class SwitchRoomRequest(val languageCode: String)

@Serializable
private data class IncognitoRequest(val enabled: Boolean)

@Serializable
private data class IncognitoResponse(val enabled: Boolean = false)
