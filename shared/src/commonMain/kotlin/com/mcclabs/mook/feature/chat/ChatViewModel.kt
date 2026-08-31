package com.mcclabs.mook.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.model.ChatMessage
import com.mcclabs.mook.domain.model.ChatSendError
import com.mcclabs.mook.domain.model.ChatSendException
import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.domain.model.MessageStatus
import com.mcclabs.mook.domain.repository.ChatRepository
import com.mcclabs.mook.domain.repository.DiscoverRepository
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

                    // Clearing the badge only in init leaves it climbing for every
                    // message that arrives while the screen is open and being read.
                    if (messages.lastOrNull()?.isMine == false) {
                        chatRepository.markAsRead(chatId)
                    }
                }
        }
    }

    /**
     * Merges the confirmed and pending lists into what the screen renders.
     *
     * Pending messages always sort after the server ones: they were just created, and
     * a stable tail keeps the list from jumping while a send is in flight.
     */
    private fun publishMessages(isLoading: Boolean? = null) {
        val merged = serverMessages + pendingMessages.values
            .map { it.message }
            .sortedBy { it.timestamp }
        _state.update {
            it.copy(
                messages = merged,
                isLoading = isLoading ?: it.isLoading,
            )
        }
    }

    // ── Composer ────────────────────────────────────────────────────────

    fun onComposerTextChange(text: String) {
        _state.update { it.copy(composerText = text) }
    }

    fun onSend() {
        val text = _state.value.composerText.trim()
        if (text.isBlank()) return

        _state.update { it.copy(composerText = "") }
        dispatchSend(messageId = chatRepository.newMessageId(), text = text)
    }

    /** Re-sends a message that previously failed, reusing its id so it cannot double-post. */
    fun onRetry(message: ChatMessage) {
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
                chatRepository.sendMessage(
                    chatId = chatId,
                    peerUid = peerUid,
                    text = text,
                    senderLanguage = myLanguage,
                    messageId = messageId,
                )
                // Deliberately no success bookkeeping: the snapshot listener removes
                // the pending entry when the real document arrives, which is the only
                // moment the message is genuinely delivered.
            } catch (e: ChatSendException) {
                markFailed(messageId)
                _state.update { it.copy(sendError = e.reason) }
            }
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
        // would reappear the moment the snapshot landed.
        if (message.isDeleted || message.status == MessageStatus.SENDING) return
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
