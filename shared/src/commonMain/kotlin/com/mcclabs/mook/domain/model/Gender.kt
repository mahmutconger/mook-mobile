package com.mcclabs.mook.domain.model

/**
 * Represents the user's gender identity.
 *
 * @property displayName Human-readable label for UI display.
 */
enum class Gender(val displayName: String) {
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other"),
    NON_BINARY("Non-binary"),
    PREFER_NOT_TO_SAY("Prefer not to say")
}
