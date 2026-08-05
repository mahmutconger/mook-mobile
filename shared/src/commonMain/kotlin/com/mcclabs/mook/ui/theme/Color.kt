package com.mcclabs.mook.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * MOOK brand color palette — now supports both light and dark themes.
 *
 * Built on pure white (or deep purple) with a subtle purple-gray tint for surfaces, a Deep Purple
 * primary, a Vibrant Pink accent, and the pink→purple brand gradient
 * ([BrandGradient]) reserved for primary CTAs and active emphasis.
 */
object NeonColors {
    /** Primary app background. */
    var Background by mutableStateOf(Color(0xFFFFFFFF))
        internal set

    /** Elevated / secondary surface. */
    var Surface by mutableStateOf(Color(0xFFF4F4F9))
        internal set

    /** Card / container fill. */
    var Card by mutableStateOf(Color(0xFFF4F4F9))
        internal set

    /** Light hairline border for cards and dividers. */
    var CardBorder by mutableStateOf(Color(0xFFE8E8EF))
        internal set

    /** Deep Purple — primary: secondary buttons, important borders, headings. */
    var Primary by mutableStateOf(Color(0xFF7218B3))
        internal set

    /** Darker purple for pressed / gradient-endpoint solid states. */
    var PrimaryDark by mutableStateOf(Color(0xFF5A0F8F))
        internal set

    /** Vibrant Pink accent — badges, active toggles, error states. */
    var Accent by mutableStateOf(Color(0xFFFF3562))
        internal set

    /** Brand gradient start (Vibrant Pink). */
    var GradientStart by mutableStateOf(Color(0xFFFF3562))
        internal set

    /** Brand gradient end (Deep Purple). */
    var GradientEnd by mutableStateOf(Color(0xFF7218B3))
        internal set

    /** Primary text. */
    var TextPrimary by mutableStateOf(Color(0xFF111827))
        internal set

    /** Secondary text / placeholders. */
    var TextSecondary by mutableStateOf(Color(0xFF6B7280))
        internal set

    /** Tertiary text — dimmed gray. */
    var TextTertiary by mutableStateOf(Color(0xFF9CA3AF))
        internal set

    /** Error / destructive. */
    var Error by mutableStateOf(Color(0xFFFF3562))
        internal set

    /** Success / positive action color. */
    var Success by mutableStateOf(Color(0xFF22C55E))
        internal set

    /** Glassmorphism / elevated card fill. */
    var GlassBackground by mutableStateOf(Color(0xFFF4F4F9))
        internal set

    /** Text-field / input background fill. */
    var InputBackground by mutableStateOf(Color(0xFFE5E5EF))
        internal set

    /** Divider / separator color. */
    var DividerColor by mutableStateOf(Color(0xFFE8E8EF))
        internal set

    fun updateTheme(isDark: Boolean) {
        if (isDark) {
            Background = Color(0xFF0B0714)
            Surface = Color(0xFF1A1423)
            Card = Color(0xFF1A1423)
            CardBorder = Color(0x1AFFFFFF) // subtle white/10
            Primary = Color(0xFF7218B3)
            PrimaryDark = Color(0xFF5A0F8F)
            Accent = Color(0xFFFF3562)
            GradientStart = Color(0xFFFF3562)
            GradientEnd = Color(0xFF7218B3)
            TextPrimary = Color(0xFFF8F8F8)
            TextSecondary = Color(0xFFA09AB0)
            TextTertiary = Color(0xFF6B7280) // darker gray
            Error = Color(0xFFFF3562)
            Success = Color(0xFF22C55E)
            GlassBackground = Color(0xFF1A1423)
            InputBackground = Color(0xFF1E1E1E)
            DividerColor = Color(0x1AFFFFFF)
        } else {
            Background = Color(0xFFFFFFFF)
            Surface = Color(0xFFF4F4F9)
            Card = Color(0xFFF4F4F9)
            CardBorder = Color(0xFFE8E8EF)
            Primary = Color(0xFF7218B3)
            PrimaryDark = Color(0xFF5A0F8F)
            Accent = Color(0xFFFF3562)
            GradientStart = Color(0xFFFF3562)
            GradientEnd = Color(0xFF7218B3)
            TextPrimary = Color(0xFF111827)
            TextSecondary = Color(0xFF6B7280)
            TextTertiary = Color(0xFF9CA3AF)
            Error = Color(0xFFFF3562)
            Success = Color(0xFF22C55E)
            GlassBackground = Color(0xFFF4F4F9)
            InputBackground = Color(0xFFE5E5EF)
            DividerColor = Color(0xFFE8E8EF)
        }
    }
}

/**
 * The core MOOK brand gradient: Vibrant Pink → Deep Purple, left to right.
 */
val BrandGradient: Brush
    get() = Brush.horizontalGradient(
        colors = listOf(NeonColors.GradientStart, NeonColors.GradientEnd),
    )
