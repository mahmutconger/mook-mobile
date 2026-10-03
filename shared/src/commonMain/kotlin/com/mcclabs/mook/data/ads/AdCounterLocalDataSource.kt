package com.mcclabs.mook.data.ads

/**
 * Reklam sayaçlarını cihazda saklayan anahtar-değer kaynağı (Android: SharedPreferences,
 * iOS: NSUserDefaults). Yalnızca [AdCounterRepositoryImpl] kullanır.
 */
interface AdCounterLocalDataSource {
    suspend fun getLong(key: String): Long?
    suspend fun putLong(key: String, value: Long)
    suspend fun remove(key: String)
}

expect fun createAdCounterLocalDataSource(): AdCounterLocalDataSource
