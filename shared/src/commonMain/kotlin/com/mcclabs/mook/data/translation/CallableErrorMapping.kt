package com.mcclabs.mook.data.translation

import com.mcclabs.mook.domain.translation.TranslationError

/**
 * Maps a Firebase callable-function error code onto a [TranslationError].
 *
 * Kept as a pure function, separate from [FirebaseFunctionsTranslator], so the whole
 * mapping table is unit-testable without Firebase, a network or a coroutine.
 *
 * Accepts both spellings the Firebase SDKs use — the enum form (`DEADLINE_EXCEEDED`)
 * and the wire form (`deadline-exceeded`) — because Android and iOS do not agree on
 * which one surfaces.
 *
 * @param code Raw code from the SDK, or `null` when the failure carried none.
 */
internal fun mapCallableErrorCode(code: String?): TranslationError {
    val normalized = code?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_')
    return when (normalized) {
        null, "" -> TranslationError.SERVER

        // The request never completed a round trip.
        "UNAVAILABLE", "ABORTED", "DATA_LOSS", "CANCELLED" -> TranslationError.NETWORK

        // It reached the backend but ran out of time.
        "DEADLINE_EXCEEDED" -> TranslationError.TIMEOUT

        // Provider or proxy quota.
        "RESOURCE_EXHAUSTED" -> TranslationError.RATE_LIMITED

        // The payload itself was rejected: unsupported code, empty or oversized text.
        "INVALID_ARGUMENT", "OUT_OF_RANGE", "FAILED_PRECONDITION" -> TranslationError.INVALID_INPUT

        // No valid Firebase session behind the call.
        "UNAUTHENTICATED", "PERMISSION_DENIED" -> TranslationError.UNAUTHENTICATED

        // NOT_FOUND (function not deployed), INTERNAL, UNIMPLEMENTED, UNKNOWN, …
        else -> TranslationError.SERVER
    }
}
