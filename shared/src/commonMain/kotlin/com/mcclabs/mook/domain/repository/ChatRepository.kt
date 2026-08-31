package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.ChatMessage
import com.mcclabs.mook.domain.model.ChatRoom
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the Firestore chat/message collections.
 *
 * Implementations must use Firestore snapshot listeners (real-time) for the
 * `observe*` methods, not one-shot reads.
 */
interface ChatRepository {

    /**
     * Builds a deterministic chat-room id from two user uids.
     *
     * The id is the lexicographically sorted pair joined with an underscore,
     * guaranteeing that `buildChatId(a, b) == buildChatId(b, a)`.
     */
    fun buildChatId(uid1: String, uid2: String): String

    /**
     * Mints an id for a message that has not been sent yet.
     *
     * The client owns the id so an optimistically-rendered bubble and the snapshot
     * that later confirms it are the same document — reconciliation is an id match
     * rather than a guess at which incoming message corresponds to which pending one.
     * It also makes [sendMessage] idempotent: a retry with the same id overwrites
     * instead of posting a duplicate.
     */
    fun newMessageId(): String

    /**
     * Observes the most recent messages of [chatId] in real time, ordered by
     * timestamp ascending.
     *
     * The window is bounded — an unbounded listener re-downloads the whole history on
     * every new message. The flow emits a new list every time a message is added,
     * modified, or removed. The [ChatMessage.isMine] flag is pre-computed against the
     * current signed-in user.
     */
    fun observeMessages(chatId: String): Flow<List<ChatMessage>>

    /**
     * Sends a text message through the `sendMessage` callable.
     *
     * The server owns everything past the text itself: it verifies the two users are
     * matched, enforces the per-user rate limit, translates into the recipient's
     * language, upserts the chat document (`users`, `lastMessage`,
     * `lastMessageTimestamp`, `unreadCounts`) together with the message in one batch,
     * and pushes the notification. Translating on the client would only duplicate the
     * DeepL call the server already makes.
     *
     * Throws [com.mcclabs.mook.domain.model.ChatSendException] when the call fails —
     * an unmatched peer, the rate limit, or no network — so the caller can mark the
     * optimistic bubble as failed and surface the reason.
     *
     * @param chatId The chat room id (see [buildChatId]).
     * @param peerUid The other participant's uid.
     * @param text The plain-text message body.
     * @param senderLanguage DeepL code of the sender's language (e.g. "TR", "EN-US").
     * @param messageId Document id from [newMessageId].
     */
    suspend fun sendMessage(
        chatId: String,
        peerUid: String,
        text: String,
        senderLanguage: String,
        messageId: String,
    )

    /**
     * Retracts one of the current user's own messages.
     *
     * A soft delete: the document survives with its type switched to
     * [ChatMessage.TYPE_DELETED] and its text cleared, so both participants see a
     * placeholder where it was. Hard-deleting would silently reshuffle the other
     * person's conversation and leave the chat list quoting text that no longer
     * exists.
     *
     * The server re-checks ownership; only the sender may delete. Throws
     * [com.mcclabs.mook.domain.model.ChatSendException] on failure.
     */
    suspend fun deleteMessage(chatId: String, messageId: String)

    /**
     * Files a report about a single message.
     *
     * Writes to the same `reports` collection the profile report flow uses, with the
     * chat and message ids attached so a moderator can find the content in context.
     */
    suspend fun reportMessage(
        chatId: String,
        messageId: String,
        reportedUid: String,
        reason: String,
    )

    /**
     * Observes all chat rooms the current user participates in, ordered by
     * most-recent message first.
     *
     * Each emission carries the full snapshot — consumers should diff or let
     * `LazyColumn` key-based diffing handle updates.
     */
    fun observeChats(): Flow<List<ChatRoom>>

    /**
     * Marks all messages in the specified chat as read for the current user,
     * resetting their unread badge count to 0.
     */
    suspend fun markAsRead(chatId: String)
}
