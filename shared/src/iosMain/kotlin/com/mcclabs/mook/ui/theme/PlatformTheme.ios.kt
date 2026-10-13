package com.mcclabs.mook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.mcclabs.mook.domain.theme.ThemePreference
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIColor
import platform.UIKit.UIUserInterfaceStyle

@Composable
actual fun ApplyPlatformTheme(preference: ThemePreference, isDark: Boolean, background: Color) {
    val controller = LocalUIViewController.current
    SideEffect {
        controller.overrideUserInterfaceStyle = when (preference) {
            ThemePreference.System, ThemePreference.Ocean -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            ThemePreference.Dark -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
            ThemePreference.Light -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        controller.view.backgroundColor = UIColor(
            red = background.red.toDouble(), green = background.green.toDouble(),
            blue = background.blue.toDouble(), alpha = background.alpha.toDouble(),
        )
        controller.setNeedsStatusBarAppearanceUpdate()
    }
}
