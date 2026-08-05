package com.mcclabs.mook.platform

/**
 * Returns the current version name of the application.
 */
actual fun getAppVersion(): String {
    return "1.0.0" // Hardcoded for now as per plan
}

/**
 * Returns the platform-specific URL for the application's store page.
 */
actual fun getStoreUrl(): String {
    // Placeholder Apple ID for Mook
    return "https://apps.apple.com/app/id000000000" 
}
