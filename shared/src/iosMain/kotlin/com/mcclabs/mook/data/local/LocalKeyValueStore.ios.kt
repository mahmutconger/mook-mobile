package com.mcclabs.mook.data.local

import platform.Foundation.NSUserDefaults

actual fun createLocalKeyValueStore(name: String): LocalKeyValueStore = UserDefaultsKeyValueStore(name)

private class UserDefaultsKeyValueStore(private val name: String) : LocalKeyValueStore {
    private val defaults get() = NSUserDefaults.standardUserDefaults

    override suspend fun getString(key: String): String? = defaults.stringForKey("$name.$key")

    override suspend fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = "$name.$key")
    }
}
