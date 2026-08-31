package com.mcclabs.mook.translation

import com.mcclabs.mook.data.translation.mapCallableErrorCode
import com.mcclabs.mook.domain.translation.TranslationError
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The failure table is the contract between the proxy and the user-facing copy, so it
 * is pinned here rather than left to inspection.
 */
class CallableErrorMappingTest {

    @Test
    fun transportFailuresBecomeNetwork() {
        assertEquals(TranslationError.NETWORK, mapCallableErrorCode("UNAVAILABLE"))
        assertEquals(TranslationError.NETWORK, mapCallableErrorCode("ABORTED"))
        assertEquals(TranslationError.NETWORK, mapCallableErrorCode("DATA_LOSS"))
        assertEquals(TranslationError.NETWORK, mapCallableErrorCode("CANCELLED"))
    }

    @Test
    fun deadlineExceededBecomesTimeout() {
        assertEquals(TranslationError.TIMEOUT, mapCallableErrorCode("DEADLINE_EXCEEDED"))
    }

    @Test
    fun resourceExhaustedBecomesRateLimited() {
        assertEquals(TranslationError.RATE_LIMITED, mapCallableErrorCode("RESOURCE_EXHAUSTED"))
    }

    @Test
    fun payloadRejectionsBecomeInvalidInput() {
        assertEquals(TranslationError.INVALID_INPUT, mapCallableErrorCode("INVALID_ARGUMENT"))
        assertEquals(TranslationError.INVALID_INPUT, mapCallableErrorCode("OUT_OF_RANGE"))
        assertEquals(TranslationError.INVALID_INPUT, mapCallableErrorCode("FAILED_PRECONDITION"))
    }

    @Test
    fun authFailuresBecomeUnauthenticated() {
        assertEquals(TranslationError.UNAUTHENTICATED, mapCallableErrorCode("UNAUTHENTICATED"))
        assertEquals(TranslationError.UNAUTHENTICATED, mapCallableErrorCode("PERMISSION_DENIED"))
    }

    @Test
    fun unknownAndMissingCodesFallBackToServer() {
        assertEquals(TranslationError.SERVER, mapCallableErrorCode(null))
        assertEquals(TranslationError.SERVER, mapCallableErrorCode(""))
        assertEquals(TranslationError.SERVER, mapCallableErrorCode("INTERNAL"))
        assertEquals(TranslationError.SERVER, mapCallableErrorCode("NOT_FOUND"))
        assertEquals(TranslationError.SERVER, mapCallableErrorCode("SOMETHING_NEW"))
    }

    @Test
    fun acceptsWireSpellingAndMixedCase() {
        // Android surfaces the enum name, iOS the hyphenated wire form; both must land
        // on the same user-facing state.
        assertEquals(TranslationError.TIMEOUT, mapCallableErrorCode("deadline-exceeded"))
        assertEquals(TranslationError.RATE_LIMITED, mapCallableErrorCode("Resource-Exhausted"))
        assertEquals(TranslationError.NETWORK, mapCallableErrorCode("  unavailable  "))
    }
}
