package com.mcclabs.mook.domain.validation

/**
 * Represents the result of a field validation check.
 *
 * @property isValid Whether the validated input passes all checks.
 * @property errorMessage A human-readable error message if validation failed, or `null` if valid.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)
