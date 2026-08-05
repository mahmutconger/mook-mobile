package com.mcclabs.mook.domain.model

/**
 * A sealed result type representing the outcome of an authentication operation.
 *
 * Used throughout the auth domain to encapsulate either a successful result
 * with associated data, or an error with a descriptive message and optional exception.
 *
 * @param T The type of data returned on success.
 */
sealed interface AuthResult<out T> {

    /**
     * Represents a successful authentication operation.
     *
     * @param T The type of the result data.
     * @property data The resulting data from the operation.
     */
    data class Success<T>(val data: T) : AuthResult<T>

    /**
     * Represents a failed authentication operation.
     *
     * @property message A human-readable error message describing the failure.
     * @property exception The underlying exception that caused the failure, if available.
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : AuthResult<Nothing>
}
