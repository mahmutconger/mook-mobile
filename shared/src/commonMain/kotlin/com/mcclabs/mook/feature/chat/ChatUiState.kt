package com.mcclabs.mook.feature.chat

import com.mcclabs.mook.domain.model.ChatMessage
import com.mcclabs.mook.domain.model.ChatSendError

/**
 * UI state for the 1-on-1 chat screen.
 *
 * @property messages Ordered list of messages (oldest first), already merged from the
 *   server snapshot and any locally pending sends.
 * @property peerName Display name of the other participant.
 * @property peerPhotoUrl First photo URL of the peer (avatar in the top bar).
 * @property composerText Current text in the message input field.
 * @property isLoading `true` while the initial message list + peer profile load.
 * @property actionTarget The message whose long-press action sheet is open, or `null`.
 * @property reportTarget The message being reported, or `null` when the reason picker
 *   is closed. Separate from [actionTarget] so the sheet can close as the dialog opens.
 * @property sendError Why the last send failed, or `null`. Typed rather than a raw
 *   message so the screen can show a localized, translatable sentence.
 * @property reportSubmitted One-shot flag: a report was filed and the confirmation
 *   has not been shown yet.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val peerName: String? = null,
    val peerPhotoUrl: String? = null,
    val composerText: String = "",
    val isLoading: Boolean = true,
    val actionTarget: ChatMessage? = null,
    val reportTarget: ChatMessage? = null,
    val sendError: ChatSendError? = null,
    val reportSubmitted: Boolean = false,
)
