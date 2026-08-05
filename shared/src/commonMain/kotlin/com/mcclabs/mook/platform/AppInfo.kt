package com.mcclabs.mook.platform

/**
 * Returns the current version name of the application (e.g., "1.0.5").
 */
expect fun getAppVersion(): String

/**
 * Returns the platform-specific URL for the application's store page.
 */
expect fun getStoreUrl(): String
