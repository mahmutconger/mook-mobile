package com.mcclabs.mook.util

import com.mcclabs.mook.domain.model.Country
import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.ISOCountryCodes
import platform.Foundation.localizedStringForCountryCode

actual fun getAvailableCountries(): List<Country> {
    val locale = NSLocale.currentLocale
    @Suppress("UNCHECKED_CAST")
    val codes = NSLocale.ISOCountryCodes as List<String>
    return codes
        .map { code ->
            val name = locale.localizedStringForCountryCode(code)
            Country(code = code, name = name?.takeIf { it.isNotBlank() } ?: code)
        }
        .distinctBy { it.name }
        .sortedBy { it.name.lowercase() }
}

actual fun getCurrentRegionCode(): String? =
    NSLocale.currentLocale.countryCode?.takeIf { it.isNotBlank() }

actual fun getCountryName(code: String): String? {
    if (code.isBlank()) return null
    return NSLocale.currentLocale.localizedStringForCountryCode(code)?.takeIf { it.isNotBlank() }
}
