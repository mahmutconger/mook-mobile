package com.mcclabs.mook.domain.theme

/** Kalıcı değerler enum sırasından bağımsızdır; yeni paletler güvenle eklenebilir. */
enum class ThemePreference(val storageKey: String) {
    System("system"), Light("light"), Dark("dark"), Ocean("ocean");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        Light -> false
        Dark -> true
        System, Ocean -> systemDark
    }

    companion object {
        fun fromStorage(value: String?): ThemePreference =
            entries.firstOrNull { it.storageKey == value } ?: System
    }
}
