package com.mcclabs.mook.platform

/**
 * Returns the current version name of the application.
 * Note: For a fully dynamic version without Context, consider enabling BuildConfig 
 * in the Android module, or initialize a global variable in MainActivity.
 */
actual fun getAppVersion(): String {
    return "1.0.0" // Hardcoded for now as per plan
}

/**
 * Returns the platform-specific URL for the application's store page.
 */
actual fun getStoreUrl(): String {
    return "https://play.google.com/store/apps/details?id=com.mcclabs.mook"
}
