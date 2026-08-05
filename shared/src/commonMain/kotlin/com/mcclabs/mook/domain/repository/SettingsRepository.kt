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
}
