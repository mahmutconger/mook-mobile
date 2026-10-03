package com.mcclabs.mook.data.local

/**
 * Küçük, kalıcı metin önbelleği (Android: SharedPreferences, iOS: NSUserDefaults).
 * Disk erişimi ana iş parçacığında yapılmaz.
 */
interface LocalKeyValueStore {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
}

expect fun createLocalKeyValueStore(name: String): LocalKeyValueStore
