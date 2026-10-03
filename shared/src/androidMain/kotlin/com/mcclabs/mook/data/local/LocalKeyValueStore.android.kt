package com.mcclabs.mook.data.local

import android.content.Context
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Uygulama Context'i Firebase'den alınır (bkz. PlatformPendingActionQueue.android.kt ile aynı desen).
actual fun createLocalKeyValueStore(name: String): LocalKeyValueStore =
    SharedPreferencesKeyValueStore(name) { FirebaseApp.getInstance().applicationContext }

private class SharedPreferencesKeyValueStore(
    private val name: String,
    contextProvider: () -> Context,
) : LocalKeyValueStore {
    private val prefs by lazy { contextProvider().getSharedPreferences(name, Context.MODE_PRIVATE) }

    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) { prefs.getString(key, null) }

    override suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }
}
