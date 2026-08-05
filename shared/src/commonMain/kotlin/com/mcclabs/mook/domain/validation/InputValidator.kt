package com.mcclabs.mook.domain.validation

/**
 * Provides pure validation functions for user input fields.
 *
 * All functions are stateless and return a [ValidationResult] indicating
 * whether the input is valid and, if not, a specific error message.
 */
object InputValidator {

    /**
     * Validates an email address.
     *
     * Checks that the email is non-empty, contains an '@' symbol,
     * and has a non-empty domain part after the '@'.
     *
     * @param email The email string to validate.
     * @return [ValidationResult] with an appropriate error message if invalid.
     */
    fun validateEmail(email: String): ValidationResult {
        val trimmed = email.trim()

        if (trimmed.isEmpty()) {
            return ValidationResult(isValid = false, errorMessage = "Email cannot be empty")
        }

        if (!trimmed.contains("@")) {
            return ValidationResult(isValid = false, errorMessage = "Email must contain '@'")
        }

        val domainPart = trimmed.substringAfter("@")
        if (domainPart.isEmpty() || !domainPart.contains(".")) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Email must have a valid domain (e.g., example.com)"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates a password.
     *
     * Requires a minimum of 8 characters, at least one uppercase letter,
     * and at least one digit.
     *
     * @param password The password string to validate.
     * @return [ValidationResult] with an appropriate error message if invalid.
     */
    fun validatePassword(password: String): ValidationResult {
        if (password.length < 8) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Password must be at least 8 characters long"
            )
        }

        if (!password.any { it.isUpperCase() }) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Password must contain at least one uppercase letter"
            )
        }

        if (!password.any { it.isDigit() }) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Password must contain at least one digit"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates a display name.
     *
     * Must be non-empty, between 2 and 30 characters, and contain only
     * letters, digits, and spaces (no special characters).
     *
     * @param name The display name string to validate.
     * @return [ValidationResult] with an appropriate error message if invalid.
     */
    fun validateDisplayName(name: String): ValidationResult {
        val trimmed = name.trim()

        if (trimmed.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Display name cannot be empty"
            )
        }

        if (trimmed.length < 2) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Display name must be at least 2 characters long"
            )
        }

        if (trimmed.length > 30) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Display name must be at most 30 characters long"
            )
        }

        if (!trimmed.all { it.isLetterOrDigit() || it == ' ' }) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Display name can only contain letters, digits, and spaces"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates a user bio.
     *
     * An empty bio is considered valid. The bio must not exceed 500 characters.
     *
     * @param bio The bio string to validate.
     * @return [ValidationResult] with an appropriate error message if invalid.
     */
    fun validateBio(bio: String): ValidationResult {
        if (bio.length > 500) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Bio must be at most 500 characters long"
            )
        }

        return ValidationResult(isValid = true)
    }
}
