package com.mcclabs.mook.util

import platform.Foundation.NSUserDefaults

/**
 * Records the preferred language in `AppleLanguages`. iOS reads this at launch, so the
 * change takes effect the next time the app starts rather than immediately.
 */
actual fun applyAppLocale(languageTag: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(languageTag), forKey = "AppleLanguages")
    NSUserDefaults.standardUserDefaults.synchronize()
}
