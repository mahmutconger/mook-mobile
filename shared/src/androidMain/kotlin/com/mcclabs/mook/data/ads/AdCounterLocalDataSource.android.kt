package com.mcclabs.mook.data.ads

import android.content.Context
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Paylaşılan katmanın Koin grafiğinde Context yok; uygulama Context'i Firebase'den alınır
// (bkz. PlatformPendingActionQueue.android.kt ile aynı desen).
actual fun createAdCounterLocalDataSource(): AdCounterLocalDataSource =
    SharedPreferencesAdCounterLocalDataSource { FirebaseApp.getInstance().applicationContext }

/** SharedPreferences tabanlı sayaç deposu; disk erişimi ana iş parçacığında YAPILMAZ. */
private class SharedPreferencesAdCounterLocalDataSource(
    contextProvider: () -> Context,
) : AdCounterLocalDataSource {
    private val prefs by lazy { contextProvider().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override suspend fun getLong(key: String): Long? = withContext(Dispatchers.IO) {
        if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    override suspend fun putLong(key: String, value: Long) = withContext(Dispatchers.IO) {
        prefs.edit().putLong(key, value).apply()
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val PREFS_NAME = "mook_ad_counters"
    }
}
