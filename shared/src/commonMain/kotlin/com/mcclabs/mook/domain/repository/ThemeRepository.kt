package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.theme.ThemePreference
import kotlinx.coroutines.flow.StateFlow

interface ThemeRepository {
    /** İlk yerel okuma tamamlanana kadar null; kök arayüz yanlış paletle çizilmez. */
    val preference: StateFlow<ThemePreference?>
    suspend fun initialize()
    suspend fun setPreference(preference: ThemePreference)
    suspend fun migrateLegacyPreference()
}
