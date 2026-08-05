package com.mcclabs.mook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.mcclabs.mook.domain.repository.SettingsRepository
import org.koin.compose.koinInject

/**
 * Builds the Material 3 color scheme from the current [NeonColors] token values.
 *
 * Called inside the composition so the scheme always reflects the active theme
 * (light or dark) without a one-frame flash.
 */
@Composable
private fun buildColorScheme(isDark: Boolean) = if (isDark) {
    darkColorScheme(
        background = NeonColors.Background,
        surface = NeonColors.Surface,
        primary = NeonColors.Primary,
        onPrimary = Color.White,
        onBackground = NeonColors.TextPrimary,
        onSurface = NeonColors.TextPrimary,
        onSurfaceVariant = NeonColors.TextSecondary,
        error = NeonColors.Error,
        onError = Color.White,
        surfaceVariant = NeonColors.Card,
        outline = NeonColors.CardBorder,
    )
} else {
    lightColorScheme(
        background = NeonColors.Background,
        surface = NeonColors.Surface,
        primary = NeonColors.Primary,
        onPrimary = Color.White,
        onBackground = NeonColors.TextPrimary,
        onSurface = NeonColors.TextPrimary,
        onSurfaceVariant = NeonColors.TextSecondary,
        error = NeonColors.Error,
        onError = Color.White,
        surfaceVariant = NeonColors.Card,
        outline = NeonColors.CardBorder,
    )
}

/**
 * Root theme composable for the Mook app.
 *
 * Observes the user's dark-mode preference from [SettingsRepository] and:
 * 1. Updates [NeonColors] tokens synchronously (no LaunchedEffect delay) so
 *    custom components that read NeonColors directly stay in sync.
 * 2. Builds the correct Material 3 color scheme for the same frame so
 *    M3 components are also themed correctly without a one-frame flash.
 *
 * @param content The composable content to be themed.
 */
@Composable
fun MookTheme(
    settingsRepository: SettingsRepository = koinInject(),
    content: @Composable () -> Unit
) {
    val isDark by settingsRepository.observeIsDarkMode().collectAsState(initial = false)

    // Update NeonColors synchronously during composition so there is no frame
    // where the scheme and NeonColors tokens are out of step.
    NeonColors.updateTheme(isDark)

    MaterialTheme(
        colorScheme = buildColorScheme(isDark),
        typography = MookTypography,
        content = content,
    )
}
