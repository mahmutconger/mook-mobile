package com.mcclabs.mook.util

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun openAppDeepLink(url: String) {
    println("IOS DEEP LINK STUB: Would open $url")
}

actual fun getCurrentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).toLong()
}
