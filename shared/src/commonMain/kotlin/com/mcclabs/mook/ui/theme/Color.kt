package com.mcclabs.mook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mcclabs.mook.domain.theme.ThemePreference

/** Material rollerine sığmayan renkler, eşleşen içerik renkleriyle birlikte tanımlanır. */
@Immutable
data class AppColors(
    val gradientStart: Color,
    val gradientEnd: Color,
    val onGradient: Color,
    val success: Color,
    val onSuccess: Color,
    val premiumStart: Color,
    val premiumEnd: Color,
    val onPremium: Color,
    val photoScrim: Color,
    val onPhoto: Color,
    val photoControl: Color,
    val onPhotoControl: Color,
    val shimmerHighlight: Color,
)

@Immutable
data class ThemePalette(val scheme: ColorScheme, val colors: AppColors)

/** Yeni özel temalar bu fabrikaya eklenir; bileşenler yalnızca semantik rolleri okur. */
fun themePalette(preference: ThemePreference, systemDark: Boolean): ThemePalette {
    val dark = preference.isDark(systemDark)
    val ocean = preference == ThemePreference.Ocean
    val primary = when {
        ocean && dark -> Color(0xFF8CD2E5)
        ocean -> Color(0xFF00677D)
        dark -> Color(0xFFD9AEFF)
        else -> Color(0xFF7218B3)
    }
    val onPrimary = when {
        ocean && dark -> Color(0xFF003642)
        dark -> Color(0xFF39005C)
        else -> Color.White
    }
    val primaryContainer = when {
        ocean && dark -> Color(0xFF004E5F)
        ocean -> Color(0xFFB5EBF8)
        dark -> Color(0xFF552075)
        else -> Color(0xFFF0DCFF)
    }
    val onPrimaryContainer = when {
        ocean && dark -> Color(0xFFB5EBF8)
        ocean -> Color(0xFF001F28)
        dark -> Color(0xFFF0DCFF)
        else -> Color(0xFF290044)
    }
    val background = if (dark) Color(0xFF0F1018) else Color(0xFFFEFBFF)
    val surface = if (dark) Color(0xFF171721) else Color(0xFFFEFBFF)
    val onSurface = if (dark) Color(0xFFEAE1EE) else Color(0xFF201A24)
    val container = if (dark) Color(0xFF24232F) else Color(0xFFF3EDF6)
    val variant = if (dark) Color(0xFF484350) else Color(0xFFE9E0ED)
    val onVariant = if (dark) Color(0xFFCDC2D3) else Color(0xFF4D4355)
    val secondary = if (dark) Color(0xFFFFB1C3) else Color(0xFFAD1645)
    val onSecondary = if (dark) Color(0xFF650022) else Color.White
    val secondaryContainer = if (dark) Color(0xFF890B36) else Color(0xFFFFD9E2)
    val onSecondaryContainer = if (dark) Color(0xFFFFD9E2) else Color(0xFF400014)
    val error = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
    val onError = if (dark) Color(0xFF690005) else Color.White
    val success = if (dark) Color(0xFF80DB9B) else Color(0xFF006D36)
    val onSuccess = if (dark) Color(0xFF003919) else Color.White
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        inversePrimary = when {
            ocean && dark -> Color(0xFF00677D)
            ocean -> Color(0xFF8CD2E5)
            dark -> Color(0xFF7218B3)
            else -> Color(0xFFD9AEFF)
        },
        primaryFixed = if (ocean) Color(0xFFB5EBF8) else Color(0xFFF0DCFF),
        primaryFixedDim = if (ocean) Color(0xFF8CD2E5) else Color(0xFFD9AEFF),
        onPrimaryFixed = if (ocean) Color(0xFF001F28) else Color(0xFF290044),
        onPrimaryFixedVariant = if (ocean) Color(0xFF004E5F) else Color(0xFF552075),
        secondaryFixed = Color(0xFFFFD9E2), secondaryFixedDim = Color(0xFFFFB1C3),
        onSecondaryFixed = Color(0xFF400014), onSecondaryFixedVariant = Color(0xFF890B36),
        tertiaryFixed = Color(0xFFA0F5B6), tertiaryFixedDim = Color(0xFF80DB9B),
        onTertiaryFixed = Color(0xFF00210B), onTertiaryFixedVariant = Color(0xFF005227),
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = success, onTertiary = onSuccess,
        tertiaryContainer = if (dark) Color(0xFF005227) else Color(0xFFA0F5B6),
        onTertiaryContainer = if (dark) Color(0xFFA0F5B6) else Color(0xFF00210B),
        background = background, onBackground = onSurface,
        surface = surface, onSurface = onSurface,
        surfaceVariant = variant, onSurfaceVariant = onVariant,
        surfaceTint = primary,
        surfaceDim = if (dark) surface else Color(0xFFDDD5DF),
        surfaceBright = if (dark) Color(0xFF393641) else surface,
        surfaceContainerLowest = background,
        surfaceContainerLow = if (dark) Color(0xFF1D1B26) else Color(0xFFF8F2FB),
        surfaceContainer = container,
        surfaceContainerHigh = if (dark) Color(0xFF2E2B37) else Color(0xFFEDE7F0),
        surfaceContainerHighest = if (dark) Color(0xFF393641) else Color(0xFFE7E1EA),
        inverseSurface = if (dark) Color(0xFFEAE1EE) else Color(0xFF352F39),
        inverseOnSurface = if (dark) Color(0xFF352F39) else Color(0xFFF8F2FB),
        outline = if (dark) Color(0xFF978C9E) else Color(0xFF7E7187),
        outlineVariant = variant,
        scrim = Color.Black,
        error = error, onError = onError,
        errorContainer = if (dark) Color(0xFF93000A) else Color(0xFFFFDAD6),
        onErrorContainer = if (dark) Color(0xFFFFDAD6) else Color(0xFF410002),
    )
    return ThemePalette(scheme, AppColors(
        gradientStart = if (ocean) Color(0xFF00677D) else Color(0xFFAD1645),
        gradientEnd = if (ocean) Color(0xFF004B65) else Color(0xFF7218B3),
        onGradient = Color.White,
        success = success, onSuccess = onSuccess,
        premiumStart = Color(0xFFF5B301), premiumEnd = Color(0xFFFF8A00), onPremium = Color(0xFF251500),
        photoScrim = Color.Black, onPhoto = Color.White,
        photoControl = Color.White, onPhotoControl = Color(0xFF201A24),
        shimmerHighlight = onSurface.copy(alpha = if (dark) 0.12f else 0.08f),
    ))
}

val LocalAppColors = staticCompositionLocalOf { themePalette(ThemePreference.System, false).colors }

val BrandGradient: Brush
    @Composable get() = Brush.horizontalGradient(listOf(LocalAppColors.current.gradientStart, LocalAppColors.current.gradientEnd))
