package com.mcclabs.mook.domain.model

/**
 * Represents the user's proficiency level in their target language.
 *
 * @property displayName Human-readable name for UI display.
 */
enum class ProficiencyLevel(val displayName: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced")
}
