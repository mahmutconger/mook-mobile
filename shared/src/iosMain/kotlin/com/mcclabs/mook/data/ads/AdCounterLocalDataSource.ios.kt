package com.mcclabs.mook.data.ads

import platform.Foundation.NSUserDefaults

actual fun createAdCounterLocalDataSource(): AdCounterLocalDataSource = UserDefaultsAdCounterLocalDataSource()

/**
 * NSUserDefaults tabanlı sayaç deposu. Değerler metin olarak saklanır; böylece NSInteger/Long
 * dönüşüm farklılıkları sorun çıkarmaz.
 */
private class UserDefaultsAdCounterLocalDataSource : AdCounterLocalDataSource {
    private val defaults get() = NSUserDefaults.standardUserDefaults

    override suspend fun getLong(key: String): Long? = defaults.stringForKey(prefixed(key))?.toLongOrNull()

    override suspend fun putLong(key: String, value: Long) {
        defaults.setObject(value.toString(), forKey = prefixed(key))
    }

    override suspend fun remove(key: String) {
        defaults.removeObjectForKey(prefixed(key))
    }

    private fun prefixed(key: String) = "mook_ad_counters.$key"
}
