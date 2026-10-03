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
import androidx.activity.result.IntentSenderRequest
import com.mcclabs.mook.domain.update.InAppUpdateActivityHolder
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.mcclabs.mook.ads.AdMobActivityHolder
import com.mcclabs.mook.ads.AdMobConsentManager
import com.mcclabs.mook.data.billing.RevenueCatActivityHolder

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        RevenueCatActivityHolder.activity = this
        AdMobActivityHolder.activity = this
        InAppUpdateActivityHolder.activity = this
        InAppUpdateActivityHolder.launcher = inAppUpdateLauncher
    }

    override fun onPause() {
        if (RevenueCatActivityHolder.activity === this) RevenueCatActivityHolder.activity = null
        super.onPause()
    }

    override fun onDestroy() {
        if (AdMobActivityHolder.activity === this) AdMobActivityHolder.activity = null
        if (InAppUpdateActivityHolder.activity === this) {
            InAppUpdateActivityHolder.activity = null
            InAppUpdateActivityHolder.launcher = null
        }
        super.onDestroy()
    }

    /**
     * Android 13+ drops every notification silently until the user grants this, while
     * the FCM token registers either way — so a missing grant looks like a broken
     * server rather than a missing permission. The result is ignored: declining only
     * costs new-message banners, which must not block using the app.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * Zorla Güncelleme: Play Core'un Immediate akışı sonucu — kullanıcı akışı iptal
     * ederse ya da bir hata olursa özel bir işlem GEREKMEZ: `App.kt`'deki kilit ekranı
     * (`ForceUpdateScreen`) zaten görünür kalır, çünkü [com.mcclabs.mook.domain.model.UpdateState]
     * yalnızca bir sonraki başarılı [com.mcclabs.mook.domain.update.ForceUpdateUseCase.evaluate]
     * çağrısında değişir — kullanıcı butona her tıkladığında akış yeniden denenir.
     */
    private val inAppUpdateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdMobActivityHolder.activity = this

        // Apply the initial theme immediately (before first frame) to avoid a flash.
        applySystemBarStyle(isDark = false)
        askForNotificationPermission()
        AdMobConsentManager.requestConsent(this)

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
