package com.mcclabs.mook.util

/**
 * Turns an ISO 3166-1 alpha-2 region code into its flag emoji ("TR" → 🇹🇷).
 *
 * Flags are pairs of Unicode regional-indicator symbols, one per letter, offset from 'A'.
 * They live above the BMP, so each is emitted as a surrogate pair — building the string by
 * hand keeps this in commonMain instead of needing a platform-specific code-point API.
 *
 * Returns `null` for anything that is not two ASCII letters, so callers can fall back to the
 * country name rather than rendering tofu.
 */
fun countryCodeToFlagEmoji(code: String?): String? {
    if (code == null || code.length != 2) return null
    val upper = code.uppercase()
    if (upper.any { it !in 'A'..'Z' }) return null

    return buildString {
        for (letter in upper) {
            val codePoint = REGIONAL_INDICATOR_A + (letter - 'A')
            val offset = codePoint - 0x10000
            append((HIGH_SURROGATE_START + (offset shr 10)).toChar())
            append((LOW_SURROGATE_START + (offset and 0x3FF)).toChar())
        }
    }
}

private const val REGIONAL_INDICATOR_A = 0x1F1E6
private const val HIGH_SURROGATE_START = 0xD800
private const val LOW_SURROGATE_START = 0xDC00
