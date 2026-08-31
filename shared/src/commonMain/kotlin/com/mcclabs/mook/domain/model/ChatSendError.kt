package com.mcclabs.mook.domain.model

/**
 * Why a [com.mcclabs.mook.domain.repository.ChatRepository.sendMessage] call failed,
 * reduced to the cases the chat screen words differently.
 *
 * The raw callable error code never reaches the UI: it is neither translatable nor
 * meaningful to a user, and the screen must show a localized sentence.
 */
enum class ChatSendError {

    /** The two users are not matched — the server refuses to open the conversation. */
    NOT_MATCHED,

    /** The per-user send quota for the current minute is spent. */
    RATE_LIMITED,

    /** Anything else: offline, function down, malformed payload. */
    GENERIC,
}

/** Thrown by the repository so callers can react without touching Firebase types. */
class ChatSendException(
    val reason: ChatSendError,
    cause: Throwable? = null,
) : Exception(reason.name, cause)

/**
 * Maps a Firebase callable error code onto a [ChatSendError].
 *
 * Accepts both spellings the Firebase SDKs use — the enum form (`RESOURCE_EXHAUSTED`)
 * and the wire form (`resource-exhausted`) — because Android and iOS do not agree on
 * which one surfaces. Kept pure so the table is testable without Firebase.
 */
fun chatSendErrorFor(code: String?): ChatSendError {
    val normalized = code?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_')
    return when (normalized) {
        // The callable rejects an unmatched peer with failed-precondition.
        "FAILED_PRECONDITION", "PERMISSION_DENIED" -> ChatSendError.NOT_MATCHED
        "RESOURCE_EXHAUSTED" -> ChatSendError.RATE_LIMITED
        else -> ChatSendError.GENERIC
    }
}
