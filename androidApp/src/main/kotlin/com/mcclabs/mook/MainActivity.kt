package com.mcclabs.mook

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply the initial theme immediately (before first frame) to avoid a flash.
        applySystemBarStyle(isDark = false)

        setContent {
            App(
                onDarkModeChange = { isDark ->
                    applySystemBarStyle(isDark)
                }
            )
        }
    }

    /**
     * Switches system bar icon tint between dark (for light backgrounds) and
     * light (for dark backgrounds) so icons always remain legible.
     */
    private fun applySystemBarStyle(isDark: Boolean) {
        if (isDark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            )
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}