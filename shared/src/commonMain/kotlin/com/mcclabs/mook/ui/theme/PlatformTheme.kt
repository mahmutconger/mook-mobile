package com.mcclabs.mook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mcclabs.mook.domain.theme.ThemePreference

/** Sistem çubukları ve yerel pencere kök yüzeyle aynı görünümü kullanır. */
@Composable
expect fun ApplyPlatformTheme(preference: ThemePreference, isDark: Boolean, background: Color)
