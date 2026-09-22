package com.mcclabs.mook.feature.sso

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mcclabs.mook.domain.sso.SsoDeliveryResult
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenURLOptionUniversalLinksOnly
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
actual fun rememberSsoRedirectLauncher(): SsoRedirectLauncher = remember {
    SsoRedirectLauncher { url, _ ->
        val nsUrl = NSURL.URLWithString(url) ?: return@SsoRedirectLauncher SsoDeliveryResult.CLIENT_APP_UNAVAILABLE
        suspendCoroutine { continuation ->
            // universalLinksOnly: uygulama yüklü değilse iOS adresi Safari'de AÇMAZ ve
            // tamamlama bloğu false döner; token hiçbir zaman tarayıcıya ulaşmaz.
            UIApplication.sharedApplication.openURL(
                nsUrl,
                options = mapOf<Any?, Any>(UIApplicationOpenURLOptionUniversalLinksOnly to true),
                completionHandler = { opened ->
                    continuation.resume(
                        if (opened) SsoDeliveryResult.DELIVERED else SsoDeliveryResult.CLIENT_APP_UNAVAILABLE,
                    )
                },
            )
        }
    }
}
