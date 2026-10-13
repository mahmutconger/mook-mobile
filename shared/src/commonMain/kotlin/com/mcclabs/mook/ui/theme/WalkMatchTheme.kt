package com.mcclabs.mook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.mook.domain.theme.ThemePreference
import com.mcclabs.mook.feature.theme.ThemeViewModel
import org.koin.compose.viewmodel.koinViewModel

private val LocalThemeScope = staticCompositionLocalOf { false }

/** Yerel tercih yüklenmeden uygulama ağacı çizilmez; başlangıçta yanlış palet gösterilmez. */
@Composable
fun MookTheme(
    viewModel: ThemeViewModel = koinViewModel(),
    onDarkModeChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) return
    AppTheme(state.preference, onDarkModeChange = onDarkModeChange, content = content)
}

/** Sistem görünümü, Material renkleri ve özel roller aynı tercih üzerinden hesaplanır. */
@Composable
fun AppTheme(
    preference: ThemePreference,
    systemDark: Boolean = isSystemInDarkTheme(),
    onDarkModeChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val palette = remember(preference, systemDark) { themePalette(preference, systemDark) }
    val isRoot = !LocalThemeScope.current
    if (isRoot) {
        ApplyPlatformTheme(preference, preference.isDark(systemDark), palette.scheme.background)
        SideEffect { onDarkModeChange(preference.isDark(systemDark)) }
    }
    MaterialTheme(colorScheme = palette.scheme, typography = MookTypography) {
        CompositionLocalProvider(LocalAppColors provides palette.colors, LocalThemeScope provides true) {
            Surface(Modifier.fillMaxSize(), color = palette.scheme.background, contentColor = palette.scheme.onBackground) {
                content()
            }
        }
    }
}
