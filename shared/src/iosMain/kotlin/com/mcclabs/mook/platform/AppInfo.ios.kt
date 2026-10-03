package com.mcclabs.mook.platform

import platform.Foundation.NSBundle

/** `CFBundleShortVersionString`'i Info.plist'ten okur. */
actual fun getAppVersion(): String =
    (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String) ?: "0.0.0"

/**
 * Returns the platform-specific URL for the application's store page.
 */
actual fun getStoreUrl(): String {
    // Placeholder Apple ID for Mook
    return "https://apps.apple.com/app/id000000000"
}
