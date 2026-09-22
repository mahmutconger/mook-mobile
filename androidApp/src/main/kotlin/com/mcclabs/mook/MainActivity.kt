package com.mcclabs.mook

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.mcclabs.mook.data.billing.RevenueCatActivityHolder

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        RevenueCatActivityHolder.activity = this
    }

    override fun onPause() {
        if (RevenueCatActivityHolder.activity === this) RevenueCatActivityHolder.activity = null
        super.onPause()
    }

    /**
     * Android 13+ drops every notification silently until the user grants this, while
     * the FCM token registers either way — so a missing grant looks like a broken
     * server rather than a missing permission. The result is ignored: declining only
     * costs new-message banners, which must not block using the app.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply the initial theme immediately (before first frame) to avoid a flash.
        applySystemBarStyle(isDark = false)
        askForNotificationPermission()

        setContent {
            App(
                onDarkModeChange = { isDark ->
                    applySystemBarStyle(isDark)
                }
            )
        }
    }

    /** No-op below API 33, where notifications need no runtime grant. */
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
