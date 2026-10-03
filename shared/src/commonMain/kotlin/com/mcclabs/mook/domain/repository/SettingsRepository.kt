package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.MatchSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    suspend fun getSettings(): MatchSettings
    suspend fun saveSettings(settings: MatchSettings)

    /** Emits the current settings and every subsequent save, so Discover can re-query. */
    fun observeSettings(): Flow<MatchSettings>

    /** Whether the user currently opts in to appear in Discover. */
    suspend fun getDiscoverVisible(): Boolean

    /** Persists the user's Discover visibility opt-in to their `users` document. */
    suspend fun setDiscoverVisible(visible: Boolean)

    /** Gizli modun mevcut durumu (`users/{uid}.incognito`, yalnızca sunucu yazar). */
    suspend fun getIncognito(): Boolean

    /**
     * Gizli modu `setIncognito` callable'ı ile açar/kapatır. Açma yalnızca Premium'da izinlidir;
     * sunucu bunu doğrular. Hata fırlatmaz.
     */
    suspend fun setIncognito(enabled: Boolean): IncognitoUpdateResult

    /** Gets whether dark mode is enabled. */
    suspend fun getIsDarkMode(): Boolean

    /** Persists the dark mode preference. */
    suspend fun setIsDarkMode(isDark: Boolean)

    /** Emits the current dark mode setting and every subsequent save. */
    fun observeIsDarkMode(): Flow<Boolean>

    /** Gets the currently selected app language (e.g. "en", "tr"). */
    suspend fun getAppLanguage(): String

    /** Persists the selected app language. */
    suspend fun setAppLanguage(language: String)

    /** Emits the current app language setting and every subsequent save. */
    fun observeAppLanguage(): Flow<String>

    /** Gets whether the user has seen the "liked me" overlay tutorial. */
    suspend fun getHasSeenLikedMeTutorial(): Boolean

    /** Marks the "liked me" tutorial as seen. */
    suspend fun setHasSeenLikedMeTutorial(seen: Boolean)

    /** Gets whether the user has seen the room-switch info dialog. */
    suspend fun getHasSeenRoomSwitchInfo(): Boolean

    /** Marks the room-switch info dialog as seen. */
    suspend fun setHasSeenRoomSwitchInfo(seen: Boolean)

    /** Gets the language code (DeepL format) of the room the user is currently in, or `null` if none chosen yet. */
    suspend fun getRoomLanguageCode(): String?

    /**
     * Kullanıcıyı seçilen odaya geçirir (sunucu tarafında `switchRoom`). Slotlar doluysa en uzun
     * süredir kullanılmayan oda atomik olarak yenisiyle takas edilir; kapasite hatası oluşmaz.
     *
     * @throws com.mcclabs.mook.domain.room.DailyRoomChangeLimitReachedException Bugünkü oda
     *   değiştirme hakkı dolduysa.
     */
    suspend fun setRoomLanguageCode(code: String)
}

/** Gizli mod güncellemesinin sonucu. */
sealed interface IncognitoUpdateResult {
    data class Updated(val enabled: Boolean) : IncognitoUpdateResult

    /** Gizli mod bir Premium ayrıcalığıdır; kullanıcı yükseltmeye yönlendirilir. */
    data object UpgradeRequired : IncognitoUpdateResult

    data object Failed : IncognitoUpdateResult
}
