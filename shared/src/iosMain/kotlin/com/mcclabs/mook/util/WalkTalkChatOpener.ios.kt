package com.mcclabs.mook.util

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun rememberWalkTalkChatOpener(): (String) -> Unit = { url ->
    // Universal Link: opens WalkTalk if installed, otherwise the web page (which
    // redirects to the App Store).
    //
    // The modern openURL:options:completionHandler: API is used deliberately: the
    // deprecated single-argument openURL: silently fails on current iOS versions,
    // which is why the button appeared to do nothing.
    Log.d("WalkTalk deep link açılıyor: $url")
    val nsUrl = NSURL.URLWithString(url)
    if (nsUrl == null) {
        Log.e("WalkTalk URL geçersiz, NSURL oluşturulamadı: $url")
    } else {
        UIApplication.sharedApplication.openURL(
            nsUrl,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
    }
}
