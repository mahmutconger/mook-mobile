package com.mcclabs.mook.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mcclabs.mook.domain.theme.ThemePreference
import com.mcclabs.mook.ui.theme.MookTypography
import com.mcclabs.mook.ui.theme.themePalette
import kotlin.math.max
import kotlin.math.min
import kotlin.test.*

class ThemePaletteTest {
    @Test fun everyPaletteProvidesReadableContentPairs() {
        ThemePreference.entries.forEach { preference ->
            listOf(false, true).forEach { systemDark ->
                val palette = themePalette(preference, systemDark)
                val scheme = palette.scheme
                val pairs = mapOf(
                    "primary" to (scheme.primary to scheme.onPrimary),
                    "primaryContainer" to (scheme.primaryContainer to scheme.onPrimaryContainer),
                    "primaryFixed" to (scheme.primaryFixed to scheme.onPrimaryFixed),
                    "primaryFixedDim" to (scheme.primaryFixedDim to scheme.onPrimaryFixedVariant),
                    "secondaryFixed" to (scheme.secondaryFixed to scheme.onSecondaryFixed),
                    "secondaryFixedDim" to (scheme.secondaryFixedDim to scheme.onSecondaryFixedVariant),
                    "tertiaryFixed" to (scheme.tertiaryFixed to scheme.onTertiaryFixed),
                    "tertiaryFixedDim" to (scheme.tertiaryFixedDim to scheme.onTertiaryFixedVariant),
                    "secondary" to (scheme.secondary to scheme.onSecondary),
                    "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
                    "tertiary" to (scheme.tertiary to scheme.onTertiary),
                    "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer),
                    "error" to (scheme.error to scheme.onError),
                    "errorContainer" to (scheme.errorContainer to scheme.onErrorContainer),
                    "background" to (scheme.background to scheme.onBackground),
                    "surface" to (scheme.surface to scheme.onSurface),
                    "surfaceContainer" to (scheme.surfaceContainer to scheme.onSurfaceVariant),
                    "surfaceContainerHighest" to (scheme.surfaceContainerHighest to scheme.onSurfaceVariant),
                    "inverseSurface" to (scheme.inverseSurface to scheme.inverseOnSurface),
                    "gradientStart" to (palette.colors.gradientStart to palette.colors.onGradient),
                    "gradientEnd" to (palette.colors.gradientEnd to palette.colors.onGradient),
                    "premiumStart" to (palette.colors.premiumStart to palette.colors.onPremium),
                    "premiumEnd" to (palette.colors.premiumEnd to palette.colors.onPremium),
                    "photoControl" to (palette.colors.photoControl to palette.colors.onPhotoControl),
                    "photoScrim" to (palette.colors.photoScrim to palette.colors.onPhoto),
                )
                pairs.forEach { (role, pair) ->
                    val ratio = contrast(pair.first, pair.second)
                    assertTrue(ratio >= 4.5, "${preference.name}/$systemDark/$role için kontrast yetersiz: $ratio")
                }
            }
        }
    }

    @Test fun typographyDoesNotFreezeThemeColors() {
        listOf(MookTypography.headlineLarge, MookTypography.headlineMedium, MookTypography.titleLarge,
            MookTypography.bodyLarge, MookTypography.bodyMedium, MookTypography.labelLarge,
            MookTypography.labelMedium, MookTypography.labelSmall).forEach { assertEquals(Color.Unspecified, it.color) }
    }

    private fun contrast(background: Color, foreground: Color): Double {
        val first = background.luminance().toDouble()
        val second = foreground.luminance().toDouble()
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }
}
