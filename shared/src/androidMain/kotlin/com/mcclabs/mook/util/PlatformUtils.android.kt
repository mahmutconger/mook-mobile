package com.mcclabs.mook.util

// Actually, in KMP Android, opening a URL without context is hard unless we use a library or have a global context.
// Let's create a global var in androidMain that the Activity sets, or just try to launch it if we have a context.
// Since we don't have a context readily available, just print a log for the MVP deep link.
actual fun openAppDeepLink(url: String) {
    println("ANDROID DEEP LINK STUB: Would open $url")
}

actual fun getCurrentTimeMillis(): Long {
    return System.currentTimeMillis()
}
