package com.mcclabs.mook.util

import com.mcclabs.mook.domain.model.Country

/**
 * Provides access to the device's locale information for the country picker.
 *
 * The country list is produced dynamically from the platform's ISO region
 * collection and localized to the device language, so a Turkish device shows
 * "Almanya" / "Türkiye" while an English device shows "Germany" / "Turkey".
 */

/**
 * Returns every ISO region known to the platform as a [Country], with names
 * localized to the current device locale. The list is de-duplicated and sorted
 * alphabetically by localized name.
 */
expect fun getAvailableCountries(): List<Country>

/**
 * Returns the ISO region code of the device's current region (e.g. "TR"),
 * or `null` if it cannot be determined. Used to pre-select the user's country.
 */
expect fun getCurrentRegionCode(): String?

/**
 * Returns the localized country name for an ISO region [code] (e.g. "TR" → "Türkiye"
 * on a Turkish device), or `null` if the platform has no name for it.
 *
 * Used to render a stored `countryCode` back into a display name.
 */
expect fun getCountryName(code: String): String?
