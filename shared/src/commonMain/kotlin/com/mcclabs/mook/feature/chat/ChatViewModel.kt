package com.mcclabs.mook.feature.chat

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.model.ReadReceiptRules
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.model.ChatMessage
import com.mcclabs.mook.domain.model.ChatSendError
import com.mcclabs.mook.domain.model.ChatSendException
import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.domain.model.MessageStatus
import com.mcclabs.mook.domain.repository.ChatRepository
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.RoomSlotRepository
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.util.Log
import com.mcclabs.mook.util.getCurrentTimeMillis
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.chat_system_recipient_limit_reached
import mook.shared.generated.resources.chat_system_translation_quota_exhausted
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.random.Random

/**
 * ViewModel for the 1-on-1 real-time chat screen.
 *
 * Subscribes to [ChatRepository.observeMessages] and exposes the conversation as a
 * reactive [StateFlow]. Translation is *not* done here: the `sendMessage` callable
 * already translates into the recipient's language server-side, so a client-side
 * pass would be a second paid DeepL call whose result is thrown away.
 *
 * ## Optimistic sending
 * A message is rendered the instant the user hits send, before the round trip.
 * Because the id is minted on the client, the confirming snapshot carries the *same*
 * document id, so reconciliation is an id lookup rather than a heuristic match on
 * text and timestamp. Pending entries the server has confirmed are dropped; ones that
 * failed stay put, marked, so the user can retry or discard them.
 *
 * @param chatRepository Firestore chat operations.
 * @param discoverRepository Supplies the peer's display profile and the sender's own
 *   language code (both read from the same `users` document).
 * @param chatId Deterministic id of the chat room.
 * @param peerUid Firebase uid of the other participant.
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val discoverRepository: DiscoverRepository,
    private val roomSlotRepository: RoomSlotRepository,
    /**
     * Gönderenin kademesini okumak için (ör. Premium kullanıcıya özel davranışlar).
     */
    private val subscriptionRepository: SubscriptionRepository,
    /** Gereksinim 2.14 (Faz 4): "first_message_replied" olayını kaydetmek için — bkz.
     *  [maybeLogFirstMessageReplied] ve [AnalyticsRepository] KDoc'u. */
    private val analyticsRepository: AnalyticsRepository,
    private val chatId: String,
    private val peerUid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** Latest server-confirmed messages, kept so a pending change can re-merge. */
    private var serverMessages: List<ChatMessage> = emptyList()

    /** Locally created messages awaiting (or denied) confirmation, oldest first. */
    private val pendingMessages = mutableMapOf<String, PendingMessage>()

    /** A pending send, retaining the original text so a retry can resend it. */
    private data class PendingMessage(val message: ChatMessage, val text: String)

    /**
     * Locally-injected informational notices (Gereksinim 1.6) — never sent to Firestore,
     * never part of [serverMessages]. Kept separately from [pendingMessages] so the
     * snapshot-confirmation cleanup in [observeMessages] (which only ever removes ids the
     * server now knows about) cannot accidentally drop one.
     */
    private val systemMessages = mutableListOf<ChatMessage>()

    /**
     * `true` once the peer's daily-limit notice has been shown for the *current*
     * uninterrupted streak of "recipient still capped" sends, so hammering send while
     * the peer stays capped shows the notice once rather than after every message. It
     * re-arms the moment a send reports the peer is no longer at their limit.
     */
    private var recipientLimitNoticeShown = false

    /**
     * Çeviri kotası notunun, "kota hâlâ bitik" gönderim serisi için zaten gösterilip
     * gösterilmediği — [recipientLimitNoticeShown] ile aynı desen.
     */
    private var translationQuotaNoticeShown = false

    /**
     * The sender's language, as a DeepL code, read once from their own profile.
     *
     * The server needs it as `source_lang`; guessing it (the old hard-coded "TR")
     * mistranslates every message from a user who is not Turkish. Falls back to
     * [Languages.DEFAULT] only when the profile has no language set.
     */
    private var myLanguage: String = Languages.DEFAULT.code

    private val myUid: String? get() = Firebase.auth.currentUser?.uid

    init {
        loadMyLanguage()
        loadPeerProfile()
        observeMessages()
        loadReadOnlyState()
        observeReadReceipts()
    }

    /** Karşı tarafın en son okuma anı (yalnızca Premium'da dolu). */
    private var peerLastReadAt: Long? = null

    /**
     * Okundu bilgisi (Premium ayrıcalığı): kullanıcı Premium olduğu sürece karşı tarafın okuma
     * kaydı dinlenir; Premium değilse dinleyici hiç başlatılmaz ve etiket gösterilmez.
     */
    private fun observeReadReceipts() {
        viewModelScope.launch {
            subscriptionRepository.state
                .map { it.isResolved && it.tier == Tier.PREMIUM }
                .distinctUntilChanged()
                .collectLatest { isPremium ->
                    if (!isPremium) {
                        peerLastReadAt = null
                        publishMessages()
                        return@collectLatest
                    }
                    chatRepository.observePeerReadAt(chatId, peerUid).collect { readAt ->
                        peerLastReadAt = readAt
                        publishMessages()
                    }
                }
        }
    }

    // ── Profiles ────────────────────────────────────────────────────────

    private fun loadMyLanguage() {
        val uid = myUid ?: return
        viewModelScope.launch {
            try {
                val code = discoverRepository.getProfileDetails(uid)?.language?.code
                if (!code.isNullOrBlank()) myLanguage = code
            } catch (e: Exception) {
                Log.e("Kendi dil kodu okunamadı — varsayılan kullanılıyor", e)
            }
        }
    }

    private fun loadPeerProfile() {
        viewModelScope.launch {
            try {
                val profile = discoverRepository.getProfileDetails(peerUid)
                _state.update {
                    it.copy(
                        peerName = profile?.name,
                        peerPhotoUrl = profile?.photoUrls?.firstOrNull(),
                    )
                }
            } catch (e: Exception) {
                Log.e("Peer profil yüklenemedi (uid=$peerUid)", e)
            }
        }
    }

    // ── Room read-only state (Gereksinim 1.7) ────────────────────────────

    /**
     * A chat is read-only when its origin room (tagged once on the server, see
     * [com.mcclabs.mook.domain.model.ChatRoom.roomLanguageCode]) is not among the
     * current user's open room slots — most commonly because a tier downgrade closed
     * it. A chat with no room tag (predates this feature) is always writable.
     */
    private fun loadReadOnlyState() {
        viewModelScope.launch {
            try {
                val roomCode = chatRepository.getChatRoom(chatId)?.roomLanguageCode
                val isReadOnly = if (roomCode == null) {
                    false
                } else {
                    roomSlotRepository.openRooms().none { it.code == roomCode }
                }
                _state.update { it.copy(isReadOnly = isReadOnly) }
            } catch (e: Exception) {
                // Bilinmiyorsa yazılabilir varsayılır — kullanıcıyı kendi sohbetinden
                // sebepsiz yere kilitlememek, fazladan bir yazma denemesine izin
                // vermekten daha güvenli bir varsayılandır (sunucu zaten gerçek dünyada
                // hiçbir ek yetki vermez, yalnızca bu istemcinin UX'ini etkiler).
                Log.e("Sohbetin salt-okunur durumu belirlenemedi (chatId=$chatId)", e)
            }
        }
    }

    // ── Real-time message stream ────────────────────────────────────────

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.observeMessages(chatId)
                .catch { e ->
                    Log.e("Mesaj akışı hatası (chatId=$chatId)", e)
                    _state.update { it.copy(sendError = ChatSendError.GENERIC, isLoading = false) }
                }
                .collect { messages ->
                    serverMessages = messages
                    // Anything the server now knows about is no longer pending.
                    messages.forEach { pendingMessages.remove(it.id) }
                    publishMessages(isLoading = false)
                    maybeLogFirstMessageReplied(messages)

                    // Clearing the badge only in init leaves it climbing for every
                    // message that arrives while the screen is open and being read.
                    if (messages.lastOrNull()?.isMine == false) {
                        chatRepository.markAsRead(chatId)
                    }
                }
        }
    }

    /** Bu ViewModel örneği boyunca "first_message_replied"ın en fazla bir kez ateşlenmesini sağlar. */
    private var hasLoggedFirstMessageReplied = false

    /**
     * Gereksinim 2.14 (Faz 4): bu sohbette karşı tarafın açılış mesajına BEN yanıt
     * verdiğimde ateşlenir — bkz. [AnalyticsRepository.logFirstMessageReplied] KDoc'u
     * (yalnızca "ben yanıt verdiğimde" ateşlenmesinin nedeni orada açıklanır).
     *
     * En-iyi-çaba bir tespittir: [hasLoggedFirstMessageReplied] yalnızca bu ViewModel
     * örneğinin ömrü boyunca yinelenen ateşlemeyi engeller — ekran kapatılıp yeniden
     * açılırsa aynı olay teorik olarak tekrar ateşlenebilir; havuz sağlığı analitiği
     * için bu, kesin bir sayaçtan çok bir EĞİLİM göstergesi olduğundan kabul edilebilir
     * bir ödünleşimdir.
     */
    private fun maybeLogFirstMessageReplied(messages: List<ChatMessage>) {
        if (hasLoggedFirstMessageReplied || messages.size < 2) return
        val opening = messages[0]
        val reply = messages[1]
        if (!opening.isMine && reply.isMine) {
            hasLoggedFirstMessageReplied = true
            val isPremium = subscriptionRepository.state.value.limits.dailyMessages == null
            analyticsRepository.logFirstMessageReplied(replierIsPremium = isPremium)
        }
    }

    /**
     * Merges the confirmed and pending lists into what the screen renders.
     *
     * Pending messages always sort after the server ones: they were just created, and
     * a stable tail keeps the list from jumping while a send is in flight.
     */
    private fun publishMessages(isLoading: Boolean? = null) {
        // System notices always sort after every pending send: they are a reaction to
        // the most recent attempt, never a comment on older history.
        val merged = serverMessages +
            pendingMessages.values.map { it.message }.sortedBy { it.timestamp } +
            systemMessages.sortedBy { it.timestamp }
        _state.update {
            it.copy(
                messages = merged,
                isLoading = isLoading ?: it.isLoading,
                seenMessageId = ReadReceiptRules.lastSeenOwnMessageId(merged, peerLastReadAt),
            )
        }
    }

    // ── Composer ────────────────────────────────────────────────────────

    fun onComposerTextChange(text: String) {
        _state.update { it.copy(composerText = text) }
    }

    fun onSend() {
        // Gereksinim 1.7: arayüz zaten kompozer'ı devre dışı bırakır (bkz.
        // `ChatUiState.isReadOnly`); bu, o korumayı atlayan herhangi bir çağrı yolu
        // için ikinci bir güvenlik hattıdır. Çeviri kotasının bitmesi gönderimi ENGELLEMEZ.
        if (_state.value.isReadOnly) return
        val text = _state.value.composerText.trim()
        if (text.isBlank()) return

        _state.update { it.copy(composerText = "") }
        dispatchSend(messageId = chatRepository.newMessageId(), text = text)
    }

    /** Re-sends a message that previously failed, reusing its id so it cannot double-post. */
    fun onRetry(message: ChatMessage) {
        if (_state.value.isReadOnly) return
        val pending = pendingMessages[message.id] ?: return
        closeActionSheet()
        dispatchSend(messageId = message.id, text = pending.text)
    }

    /** Drops a failed message from the conversation without contacting the server. */
    fun onDiscardFailed(message: ChatMessage) {
        closeActionSheet()
        if (pendingMessages.remove(message.id) != null) publishMessages()
    }

    private fun dispatchSend(messageId: String, text: String) {
        val optimistic = ChatMessage(
            id = messageId,
            senderUid = myUid.orEmpty(),
            text = text,
            // The server does the translating, so a pending bubble shows only the
            // original; the translation appears when the snapshot confirms it.
            translatedText = null,
            senderLanguage = myLanguage,
            timestamp = getCurrentTimeMillis(),
            isMine = true,
            status = MessageStatus.SENDING,
        )
        pendingMessages[messageId] = PendingMessage(optimistic, text)
        publishMessages()

        viewModelScope.launch {
            try {
                val result = chatRepository.sendMessage(
                    chatId = chatId,
                    peerUid = peerUid,
                    text = text,
                    senderLanguage = myLanguage,
                    messageId = messageId,
                )
                // Deliberately no success bookkeeping for the message itself: the
                // snapshot listener removes the pending entry when the real document
                // arrives, which is the only moment the message is genuinely delivered.
                onRecipientQuotaStateKnown(result.recipientAtDailyLimit)
                onTranslationQuotaStateKnown(result.translationQuotaExhausted)
            } catch (e: ChatSendException) {
                markFailed(messageId)
                _state.update { it.copy(sendError = e.reason) }
            }
        }
    }

    /**
     * Yalnızca Çeviri Kotası Mantığı: çeviri kotası bittiğinde mesaj yine de (çevrilmeden)
     * teslim edilir. Kullanıcı bunu fark etmeyebileceği için sohbete yerel, kalıcı olmayan bir
     * sistem notu düşülür — art arda gönderimlerde yalnızca BİR KEZ; çeviri yeniden
     * çalıştığında (kota yenilendiğinde) not yeniden gösterilebilir hale gelir.
     */
    private fun onTranslationQuotaStateKnown(translationQuotaExhausted: Boolean) {
        if (!translationQuotaExhausted) {
            translationQuotaNoticeShown = false
            return
        }
        if (translationQuotaNoticeShown) return
        translationQuotaNoticeShown = true
        addLocalSystemMessage(Res.string.chat_system_translation_quota_exhausted)
    }

    /**
     * Gereksinim 1.6: a Premium (or any tier's) sender messaging a peer who has already
     * exhausted their own daily message quota still gets delivered — the server never
     * blocks it — but the sender otherwise has no way to know a reply may not come. A
     * single local, non-persisted system message covers that until the peer's own quota
     * resets (see [recipientLimitNoticeShown]).
     */
    private fun onRecipientQuotaStateKnown(recipientAtDailyLimit: Boolean) {
        if (!recipientAtDailyLimit) {
            recipientLimitNoticeShown = false
            return
        }
        if (recipientLimitNoticeShown) return
        recipientLimitNoticeShown = true
        addLocalSystemMessage(Res.string.chat_system_recipient_limit_reached)
    }

    /** Sohbete yalnızca bu cihazda görünen, sunucuya yazılmayan bir sistem notu ekler. */
    private fun addLocalSystemMessage(text: StringResource) {
        viewModelScope.launch {
            systemMessages += ChatMessage(
                id = "local_system_${getCurrentTimeMillis()}_${Random.nextInt()}",
                senderUid = "",
                text = getString(text),
                translatedText = null,
                senderLanguage = myLanguage,
                timestamp = getCurrentTimeMillis(),
                type = ChatMessage.TYPE_SYSTEM,
                isMine = false,
                status = MessageStatus.SENT,
            )
            publishMessages()
        }
    }

    private fun markFailed(messageId: String) {
        val pending = pendingMessages[messageId] ?: return
        pendingMessages[messageId] =
            pending.copy(message = pending.message.copy(status = MessageStatus.FAILED))
        publishMessages()
    }

    // ── Long-press actions ──────────────────────────────────────────────

    fun onMessageLongPressed(message: ChatMessage) {
        // A retracted message has nothing left to delete or report. One still in
        // flight is skipped too: discarding it locally would not stop the send, so it
        // would reappear the moment the snapshot landed. A system notice is a local-only
        // construct with no server document at all — delete/report would just fail.
        if (message.isDeleted || message.status == MessageStatus.SENDING || message.isSystem) return
        _state.update { it.copy(actionTarget = message) }
    }

    fun closeActionSheet() {
        _state.update { it.copy(actionTarget = null) }
    }

    fun onDeleteMessage(message: ChatMessage) {
        closeActionSheet()
        // A message that never reached the server is discarded locally instead.
        if (message.status != MessageStatus.SENT) {
            onDiscardFailed(message)
            return
        }
        viewModelScope.launch {
            try {
                chatRepository.deleteMessage(chatId, message.id)
                // No local mutation: the snapshot delivers the retracted document.
            } catch (e: ChatSendException) {
                _state.update { it.copy(sendError = e.reason) }
            }
        }
    }

    // ── Reporting ───────────────────────────────────────────────────────

    fun onReportMessage(message: ChatMessage) {
        _state.update { it.copy(actionTarget = null, reportTarget = message) }
    }

    fun onReportDismissed() {
        _state.update { it.copy(reportTarget = null) }
    }

    fun onReportReasonSelected(reason: String) {
        val message = _state.value.reportTarget ?: return
        _state.update { it.copy(reportTarget = null) }
        viewModelScope.launch {
            try {
                chatRepository.reportMessage(
                    chatId = chatId,
                    messageId = message.id,
                    reportedUid = message.senderUid,
                    reason = reason,
                )
                _state.update { it.copy(reportSubmitted = true) }
            } catch (e: ChatSendException) {
                _state.update { it.copy(sendError = e.reason) }
            }
        }
    }

    fun clearReportSubmitted() {
        _state.update { it.copy(reportSubmitted = false) }
    }

    fun clearError() {
        _state.update { it.copy(sendError = null) }
    }
}
