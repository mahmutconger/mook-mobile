package com.mcclabs.mook.util

import com.mcclabs.mook.domain.model.Country
import java.util.Locale

actual fun getAvailableCountries(): List<Country> {
    val deviceLocale = Locale.getDefault()
    return Locale.getISOCountries()
        .map { code ->
            val name = Locale.Builder().setRegion(code).build().getDisplayCountry(deviceLocale)
            // Fall back to the raw code if the platform has no localized name.
            Country(code = code, name = name.ifBlank { code })
        }
        .distinctBy { it.name }
        .sortedBy { it.name.lowercase(deviceLocale) }
}

actual fun getCurrentRegionCode(): String? =
    Locale.getDefault().country.takeIf { it.isNotBlank() }

actual fun getCountryName(code: String): String? {
    if (code.isBlank()) return null
    val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault())
    // The platform echoes the raw code back when it has no localized name.
    return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
}
