package com.mcclabs.mook.chat

import com.mcclabs.mook.domain.model.ChatSendError
import com.mcclabs.mook.domain.model.chatSendErrorFor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The mapping decides which sentence a user reads when a message fails, and the two
 * SDKs disagree on the spelling of the code it reads. Both spellings are pinned here,
 * along with the contract that the callable signals "not matched" with
 * `failed-precondition` — see `sendMessage` in functions/src/index.ts.
 */
class ChatSendErrorTest {

    @Test
    fun failedPreconditionMeansTheUsersAreNotMatched() {
        assertEquals(ChatSendError.NOT_MATCHED, chatSendErrorFor("FAILED_PRECONDITION"))
        assertEquals(ChatSendError.NOT_MATCHED, chatSendErrorFor("failed-precondition"))
    }

    @Test
    fun permissionDeniedAlsoReadsAsNotMatched() {
        // Rules reject a write to a room the caller has no business in; to the user
        // that is the same situation as an unmatched peer.
        assertEquals(ChatSendError.NOT_MATCHED, chatSendErrorFor("PERMISSION_DENIED"))
        assertEquals(ChatSendError.NOT_MATCHED, chatSendErrorFor("permission-denied"))
    }

    @Test
    fun resourceExhaustedMeansTheRateLimitIsSpent() {
        assertEquals(ChatSendError.RATE_LIMITED, chatSendErrorFor("RESOURCE_EXHAUSTED"))
        assertEquals(ChatSendError.RATE_LIMITED, chatSendErrorFor("resource-exhausted"))
    }

    @Test
    fun everythingElseFallsBackToTheGenericMessage() {
        // NOT_FOUND is the one a region mismatch produces — it must not be silently
        // reported as something the user can fix by waiting.
        listOf("NOT_FOUND", "unavailable", "INTERNAL", "unknown", "", null).forEach { code ->
            assertEquals(
                ChatSendError.GENERIC,
                chatSendErrorFor(code),
                "Unexpected mapping for code=$code",
            )
        }
    }
}
