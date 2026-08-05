package com.mcclabs.mook.domain.model

/**
 * Represents a country / ISO region for profile selection.
 *
 * @property code ISO 3166-1 alpha-2 region code (e.g. "TR", "DE").
 * @property name Localized display name for the current device locale
 *                (e.g. "Türkiye" on a Turkish device, "Turkey" on an English one).
 */
data class Country(
    val code: String,
    val name: String
)
