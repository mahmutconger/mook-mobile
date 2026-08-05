package com.mcclabs.mook.domain.model

/**
 * Represents a spoken language with its ISO code, display name, and flag emoji.
 *
 * @property code ISO 639-1 language code (e.g., "en", "es").
 * @property name Human-readable language name (e.g., "English", "Spanish").
 * @property flagEmoji Unicode flag emoji representing the language's primary country.
 */
data class Language(
    val code: String,
    val name: String,
    val flagEmoji: String
)
