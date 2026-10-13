package com.mcclabs.mook.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.mcclabs.mook.domain.theme.ThemePreference
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun ApplyPlatformTheme(preference: ThemePreference, isDark: Boolean, background: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context.findActivity() as? ComponentActivity ?: return
    SideEffect {
        val transparent = Color.Transparent.toArgb()
        val style = if (isDark) SystemBarStyle.dark(transparent)
            else SystemBarStyle.light(transparent, transparent)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        activity.window.decorView.setBackgroundColor(background.toArgb())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
    }
}
