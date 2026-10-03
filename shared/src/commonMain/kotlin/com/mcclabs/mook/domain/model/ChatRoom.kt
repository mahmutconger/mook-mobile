package com.mcclabs.mook.domain.model

/**
 * Summary of a chat room, shown in the chat list screen.
 *
 * Each [ChatRoom] maps to a top-level `chats/{chatId}` Firestore document.
 * The peer's display name and photo are resolved from their user profile.
 *
 * @property chatId Firestore document id — `sorted(uid1, uid2).joinToString("_")`.
 * @property peerUid The other participant's Firebase uid.
 * @property peerName Display name of the peer, or `null` if not yet loaded.
 * @property peerPhotoUrl First photo URL of the peer, or `null`.
 * @property lastMessage Preview text of the most recent message.
 * @property lastMessageTimestamp Epoch millis of the most recent message, used for
 *   sorting and for the row's date label.
 * @property lastSenderUid Who sent the last message — used to show "You: …" prefix.
 * @property lastMessageDeleted `true` when the most recent message was retracted, so
 *   the row shows a placeholder instead of the text of a message that no longer
 *   exists. Set by the `deleteMessage` callable and cleared by the next send.
 * @property roomLanguageCode The language-room the pair matched in, set once by the
 *   server on the chat's first message and never rewritten afterward (Gereksinim 1.7).
 *   `null` for a chat that predates this field. Whether the chat is currently
 *   read-only is derived by the caller — comparing this against the current user's
 *   own open rooms ([com.mcclabs.mook.domain.repository.RoomSlotRepository]) — rather
 *   than stored here, since it depends on which side is looking: a room one
 *   participant closed after a downgrade may still be open for the other.
 */
data class ChatRoom(
    val chatId: String,
    val peerUid: String,
    val peerName: String? = null,
    val peerPhotoUrl: String? = null,
    val lastMessage: String? = null,
    val lastMessageTimestamp: Long? = null,
    val lastSenderUid: String? = null,
    val unreadCount: Int = 0,
    val lastMessageDeleted: Boolean = false,
    val roomLanguageCode: String? = null,
)
