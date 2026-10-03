package com.mcclabs.mook.data.repository

import kotlinx.coroutines.flow.catch
import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.data.appFirestore
import com.mcclabs.mook.domain.model.ChatMessage
import com.mcclabs.mook.domain.model.ChatRoom
import com.mcclabs.mook.domain.model.ChatSendError
import com.mcclabs.mook.domain.model.ChatSendException
import com.mcclabs.mook.domain.model.MessageStatus
import com.mcclabs.mook.domain.model.chatSendErrorFor
import com.mcclabs.mook.domain.repository.ChatRepository
import com.mcclabs.mook.domain.repository.SendMessageResult
import com.mcclabs.mook.util.Log
import com.mcclabs.mook.util.getCurrentTimeMillis
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.where
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.code
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Payload of the `sendMessage` callable. Field names must match `functions/src/index.ts`. */
@Serializable
internal data class SendMessageRequest(
    val chatId: String,
    val peerUid: String,
    val text: String,
    val senderLanguage: String,
    val messageId: String,
)

/**
 * Response of the `sendMessage` callable. Field names must match `functions/src/index.ts`.
 *
 * [recipientAtDailyLimit] is read-only telemetry (Gereksinim 1.6): the server sets it
 * whether or not this particular send was a new chat or a reply, and it never blocks
 * or affects the send itself.
 */
@Serializable
internal data class SendMessageResponse(
    val success: Boolean = true,
    val messageId: String? = null,
    val translatedText: String? = null,
    val recipientAtDailyLimit: Boolean = false,
    /** Yalnızca Çeviri Kotası: kota yetmediği için mesaj çevrilmeden gönderildi. */
    val translationQuotaExhausted: Boolean = false,
)

/** Payload of the `deleteMessage` callable. */
@Serializable
internal data class DeleteMessageRequest(
    val chatId: String,
    val messageId: String,
)

/**
 * How many of the most recent messages a conversation keeps live.
 *
 * A snapshot listener without a bound re-downloads the entire history on every
 * change, so the window is capped. Older messages scroll off; paging them back in
 * is a separate feature, not a silent unbounded read.
 */
private const val MESSAGE_WINDOW = 200

/**
 * Firestore-backed [ChatRepository].
 *
 * Uses the shared [appFirestore] singleton to avoid the iOS double-settings crash.
 *
 * ## Collections
 * ```
 * chats/{chatId}                     ← room metadata + lastMessage + unreadCounts
 *   └─ messages/{auto-id}            ← individual messages
 * ```
 *
 * Reads are real time through gitlive's `snapshots` flows. Writes go through
 * callables instead of direct Firestore writes, because the server is where the match
 * check, the rate limit, the DeepL translation, the push notification and the
 * delete-ownership check live — a client write would skip all of them, and the
 * security rules deny it anyway.
 */
class ChatRepositoryImpl : ChatRepository {

    private val db get() = appFirestore
    private val currentUid: String?
        get() = Firebase.auth.currentUser?.uid

    // ── Deterministic chat id ───────────────────────────────────────────

    override fun buildChatId(uid1: String, uid2: String): String =
        if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"

    // ── Client-minted message id ────────────────────────────────────────

    override fun newMessageId(): String {
        // Timestamp prefix keeps ids roughly ordered, which makes them pleasant to
        // read in the Firestore console; the random suffix is what makes them unique.
        // Hex only, so the value is always a legal Firestore document id.
        val random = Random.nextLong().toULong().toString(16).padStart(16, '0')
        return "${getCurrentTimeMillis().toString(16)}-$random"
    }

    // ── Real-time message stream ────────────────────────────────────────

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> {
        val uid = currentUid.orEmpty()

        // Descending + limit takes the *newest* window; gitlive 1.13 has no
        // limitToLast, so the list is flipped back to oldest-first for the UI.
        return db
            .collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Direction.DESCENDING)
            .limit(MESSAGE_WINDOW)
            .snapshots
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { doc -> doc.toChatMessage(currentUid = uid) }
                    .asReversed()
            }
    }

    private fun DocumentSnapshot.toChatMessage(currentUid: String): ChatMessage? {
        val senderUid = runCatching { get<String>("senderUid") }.getOrNull()
        val type = runCatching { get<String?>("type") }.getOrNull() ?: ChatMessage.TYPE_TEXT
        // A deleted message legitimately has no text, so only a live one is rejected
        // for missing it.
        val text = runCatching { get<String?>("text") }.getOrNull().orEmpty()
        if (senderUid.isNullOrBlank() || (text.isEmpty() && type != ChatMessage.TYPE_DELETED)) {
            Log.e("Mesaj deserialize hatası — senderUid/text eksik (doc=$id)")
            return null
        }
        return ChatMessage(
            id = id,
            senderUid = senderUid,
            text = text,
            translatedText = runCatching { get<String?>("translatedText") }.getOrNull(),
            senderLanguage = runCatching { get<String?>("senderLanguage") }.getOrNull().orEmpty(),
            timestamp = runCatching { get<Long?>("timestamp") }.getOrNull() ?: 0L,
            type = type,
            isMine = senderUid == currentUid,
            status = MessageStatus.SENT,
        )
    }

    // ── Send message ────────────────────────────────────────────────────

    override suspend fun sendMessage(
        chatId: String,
        peerUid: String,
        text: String,
        senderLanguage: String,
        messageId: String,
    ): SendMessageResult {
        if (currentUid == null) {
            Log.e("Mesaj gönderilemedi — kullanıcı oturumu yok")
            throw ChatSendException(ChatSendError.GENERIC)
        }

        val response = callableWithResponse<SendMessageRequest, SendMessageResponse>(
            name = "sendMessage",
            context = "chatId=$chatId",
            payload = SendMessageRequest(
                chatId = chatId,
                peerUid = peerUid,
                text = text,
                senderLanguage = senderLanguage,
                messageId = messageId,
            ),
        )
        return SendMessageResult(
            recipientAtDailyLimit = response.recipientAtDailyLimit,
            translationQuotaExhausted = response.translationQuotaExhausted,
        )
    }

    // ── Delete message ──────────────────────────────────────────────────

    override suspend fun deleteMessage(chatId: String, messageId: String) {
        if (currentUid == null) {
            Log.e("Mesaj silinemedi — kullanıcı oturumu yok")
            throw ChatSendException(ChatSendError.GENERIC)
        }

        callable(
            name = "deleteMessage",
            context = "chatId=$chatId, messageId=$messageId",
            payload = DeleteMessageRequest(chatId = chatId, messageId = messageId),
        )
    }

    /**
     * Invokes a callable in the default region and normalises its failure.
     *
     * Default region (us-central1) matches where the callables are deployed; naming a
     * different one makes every call fail with NOT_FOUND.
     */
    private suspend inline fun <reified T> callable(name: String, context: String, payload: T) {
        try {
            appHttpsCallable(name).invoke(payload)
        } catch (cancellation: CancellationException) {
            // Cooperative cancellation is not a failure — let structured concurrency
            // keep working.
            throw cancellation
        } catch (e: Throwable) {
            Log.e("$name çağrısı başarısız ($context)", e)
            throw ChatSendException(
                chatSendErrorFor(
                    (e as? FirebaseFunctionsException)?.code?.name,
                    // Gereksinim 2.2: RESOURCE_EXHAUSTED kodunun ALTINDAKİ asıl sebebi
                    // (ör. karakter kotası mı, dakikalık hız sınırı mı) ayırt etmek için.
                    (e as? FirebaseFunctionsException)?.message,
                ),
                e,
            )
        }
    }

    /**
     * Like [callable], but decodes and returns the callable's response body instead of
     * discarding it. Kept as a separate overload rather than changing [callable]'s
     * signature, since most callables here (delete, report, ...) have nothing useful
     * to decode and a `Unit` response would need lenient/ignore-unknown-keys decoding
     * to tolerate the server's actual payload.
     */
    private suspend inline fun <reified TRequest, reified TResponse> callableWithResponse(
        name: String,
        context: String,
        payload: TRequest,
    ): TResponse {
        try {
            return appHttpsCallable(name).invoke(payload).data<TResponse>()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Throwable) {
            Log.e("$name çağrısı başarısız ($context)", e)
            throw ChatSendException(
                chatSendErrorFor(
                    (e as? FirebaseFunctionsException)?.code?.name,
                    // Gereksinim 2.2: RESOURCE_EXHAUSTED kodunun ALTINDAKİ asıl sebebi
                    // (ör. karakter kotası mı, dakikalık hız sınırı mı) ayırt etmek için.
                    (e as? FirebaseFunctionsException)?.message,
                ),
                e,
            )
        }
    }

    // ── Report a message ────────────────────────────────────────────────

    override suspend fun reportMessage(
        chatId: String,
        messageId: String,
        reportedUid: String,
        reason: String,
    ) {
        val uid = currentUid ?: throw ChatSendException(ChatSendError.GENERIC)
        try {
            // Same collection and shape as the profile report flow, plus the ids a
            // moderator needs to locate the message itself.
            db.collection("reports").document.set(
                mapOf(
                    "reporterUid" to uid,
                    "reportedUid" to reportedUid,
                    "reason" to reason,
                    "chatId" to chatId,
                    "messageId" to messageId,
                    "kind" to "message",
                    "timestamp" to getCurrentTimeMillis(),
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Log.e("Mesaj bildirilemedi (chatId=$chatId, messageId=$messageId)", e)
            throw ChatSendException(ChatSendError.GENERIC, e)
        }
    }

    // ── Real-time chat list ─────────────────────────────────────────────

    override fun observeChats(): Flow<List<ChatRoom>> {
        val uid = currentUid ?: return flowOf(emptyList())

        return db
            .collection("chats")
            .where { "users" contains uid }
            .snapshots
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { doc -> doc.toChatRoom(currentUid = uid) }
                    .sortedByDescending { it.lastMessageTimestamp ?: 0L }
            }
    }

    private fun DocumentSnapshot.toChatRoom(currentUid: String): ChatRoom? {
        val users = runCatching { get<List<String>>("users") }.getOrNull().orEmpty()
        val peerUid = users.firstOrNull { it != currentUid }
        if (peerUid == null) {
            Log.e("Sohbet dokümanı atlandı — karşı taraf bulunamadı (doc=$id)")
            return null
        }
        val unreadCounts = runCatching { get<Map<String, Int>?>("unreadCounts") }.getOrNull()
        return ChatRoom(
            chatId = id,
            peerUid = peerUid,
            lastMessage = runCatching { get<String?>("lastMessage") }.getOrNull(),
            lastMessageTimestamp = runCatching { get<Long?>("lastMessageTimestamp") }.getOrNull(),
            lastSenderUid = runCatching { get<String?>("lastSenderUid") }.getOrNull(),
            unreadCount = unreadCounts?.get(currentUid) ?: 0,
            lastMessageDeleted = runCatching { get<Boolean?>("lastMessageDeleted") }
                .getOrNull() ?: false,
            roomLanguageCode = runCatching { get<String?>("roomLanguageCode") }.getOrNull(),
        )
    }

    // ── Single chat lookup ──────────────────────────────────────────────

    override suspend fun getChatRoom(chatId: String): ChatRoom? {
        val uid = currentUid ?: return null
        return try {
            db.collection("chats").document(chatId).get().toChatRoom(currentUid = uid)
        } catch (e: Exception) {
            Log.e("Sohbet dokümanı okunamadı (chatId=$chatId)", e)
            null
        }
    }

    // ── Mark as read ────────────────────────────────────────────────────

    override suspend fun markAsRead(chatId: String) {
        val uid = currentUid ?: return
        try {
            // Dotted key = nested field path, so this clears unreadCounts.{uid}
            // written by the callable rather than creating a sibling field.
            db.collection("chats")
                .document(chatId)
                .update("unreadCounts.$uid" to 0)
        } catch (e: Exception) {
            // The room has no document until the first message is sent — nothing to clear.
            Log.e("Okundu işaretleme hatası (chatId=$chatId)", e)
        }
        // Okundu bilgisi: karşı taraf Premium ise "Görüldü" etiketini bu kayıttan okur.
        // Kayıt yalnızca kendi adıma ve katılımcısı olduğum sohbet için yazılabilir (kurallar).
        try {
            db.collection("read_receipts")
                .document("${chatId}_$uid")
                .set(
                    mapOf(
                        "chatId" to chatId,
                        "readerUid" to uid,
                        "lastReadAt" to getCurrentTimeMillis(),
                    ),
                )
        } catch (e: Exception) {
            Log.e("Okundu bilgisi yazılamadı (chatId=$chatId)", e)
        }
    }

    override fun observePeerReadAt(chatId: String, peerUid: String): Flow<Long?> =
        db.collection("read_receipts")
            .document("${chatId}_$peerUid")
            .snapshots
            .map { snapshot ->
                if (!snapshot.exists) null else runCatching { snapshot.get<Long?>("lastReadAt") }.getOrNull()
            }
            .catch { error ->
                // Premium değilse kurallar okumayı reddeder — bu beklenen bir durumdur.
                Log.d("Okundu bilgisi dinlenemiyor: ${error.message}")
                emit(null)
            }
}
