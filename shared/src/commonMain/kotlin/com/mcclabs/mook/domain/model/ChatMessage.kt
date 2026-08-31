package com.mcclabs.mook.domain.model

/**
 * Where an outgoing message is in its journey to the server.
 *
 * Only ever [SENT] for a message that arrived from a Firestore snapshot; the other
 * two describe a locally-created message that has not been confirmed yet.
 */
enum class MessageStatus {

    /** Rendered optimistically; the callable has not returned. */
    SENDING,

    /** Confirmed by the server — this is what a snapshot delivers. */
    SENT,

    /** The send failed. The bubble stays put so the user can retry or discard it. */
    FAILED,
}

/**
 * A single chat message as displayed in the conversation screen.
 *
 * @property id Firestore document id. Generated on the client *before* sending (see
 *   [com.mcclabs.mook.domain.repository.ChatRepository.newMessageId]) so an
 *   optimistic bubble and the snapshot that later confirms it share one identity and
 *   reconcile exactly, with no text/timestamp guessing.
 * @property senderUid Firebase uid of the sender.
 * @property text The original message text in the sender's language. Empty for a
 *   deleted message.
 * @property translatedText The translated text in the receiver's language,
 *   or `null` when the translation has not yet resolved.
 * @property senderLanguage ISO language code the sender wrote in (e.g. "TR").
 * @property timestamp Epoch millis when the message was sent.
 * @property type Message type — "text" or "deleted"; reserved for future
 *   media types ("image", "voice").
 * @property isMine `true` when the current signed-in user is the sender.
 * @property status Delivery state; see [MessageStatus].
 */
data class ChatMessage(
    val id: String,
    val senderUid: String,
    val text: String,
    val translatedText: String?,
    val senderLanguage: String,
    val timestamp: Long,
    val type: String = "text",
    val isMine: Boolean,
    val status: MessageStatus = MessageStatus.SENT,
) {
    /** A message the sender retracted. Rendered as a placeholder for both sides. */
    val isDeleted: Boolean get() = type == TYPE_DELETED

    companion object {
        const val TYPE_TEXT: String = "text"
        const val TYPE_DELETED: String = "deleted"
    }
}
