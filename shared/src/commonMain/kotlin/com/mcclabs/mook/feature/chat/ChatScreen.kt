package com.mcclabs.mook.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mcclabs.mook.domain.model.ChatMessage
import com.mcclabs.mook.domain.model.ChatSendError
import com.mcclabs.mook.domain.model.MessageStatus
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
import com.mcclabs.mook.util.formatClockTime
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.chat_action_cancel
import mook.shared.generated.resources.chat_action_delete
import mook.shared.generated.resources.chat_action_discard
import mook.shared.generated.resources.chat_action_report
import mook.shared.generated.resources.chat_action_retry
import mook.shared.generated.resources.chat_composer_placeholder
import mook.shared.generated.resources.chat_empty
import mook.shared.generated.resources.chat_error_not_matched
import mook.shared.generated.resources.chat_error_rate_limited
import mook.shared.generated.resources.chat_error_send_failed
import mook.shared.generated.resources.chat_message_deleted
import mook.shared.generated.resources.chat_report_subtitle
import mook.shared.generated.resources.chat_report_title
import mook.shared.generated.resources.chat_send_cd
import mook.shared.generated.resources.chat_status_failed
import mook.shared.generated.resources.chat_status_sending
import mook.shared.generated.resources.chat_title_fallback
import mook.shared.generated.resources.common_back
import mook.shared.generated.resources.report_reason_harassment
import mook.shared.generated.resources.report_reason_other
import mook.shared.generated.resources.report_reason_spam
import mook.shared.generated.resources.report_submitted
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Production 1-on-1 chat screen.
 *
 * Messages are observed in real time and translated server-side. An outgoing message
 * is drawn immediately and reconciled when its snapshot arrives, so the conversation
 * never stalls on a round trip. Long-pressing a bubble opens the delete/report sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    peerUid: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = koinViewModel<ChatViewModel>(
        parameters = { parametersOf(chatId, peerUid) }
    ),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-scroll to the newest message.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    // Show errors in snackbar. The message is resolved from resources here rather
    // than carried in the state, so it follows the app's language.
    val sendErrorText = state.sendError?.let { stringResource(it.messageRes()) }
    LaunchedEffect(sendErrorText) {
        sendErrorText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val reportSubmittedText = stringResource(Res.string.report_submitted)
    LaunchedEffect(state.reportSubmitted) {
        if (state.reportSubmitted) {
            snackbarHostState.showSnackbar(reportSubmittedText)
            viewModel.clearReportSubmitted()
        }
    }

    state.actionTarget?.let { target ->
        MessageActionSheet(
            message = target,
            onDismiss = viewModel::closeActionSheet,
            onDelete = { viewModel.onDeleteMessage(target) },
            onReport = { viewModel.onReportMessage(target) },
            onRetry = { viewModel.onRetry(target) },
            onDiscard = { viewModel.onDiscardFailed(target) },
        )
    }

    if (state.reportTarget != null) {
        ReportReasonDialog(
            onDismiss = viewModel::onReportDismissed,
            onReasonSelected = viewModel::onReportReasonSelected,
        )
    }

    Scaffold(
        containerColor = NeonColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                peerName = state.peerName,
                peerPhotoUrl = state.peerPhotoUrl,
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeonColors.Background)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                HorizontalDivider(color = NeonColors.DividerColor)
                ChatComposer(
                    text = state.composerText,
                    onTextChange = viewModel::onComposerTextChange,
                    onSend = viewModel::onSend,
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = NeonColors.Primary)
                }
            }

            state.messages.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.chat_empty),
                        color = NeonColors.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onLongPress = { viewModel.onMessageLongPressed(message) },
                        )
                    }
                }
            }
        }
    }
}

// ── Top bar ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    peerName: String?,
    peerPhotoUrl: String?,
    onNavigateBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = peerPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeonColors.Card),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = peerName ?: stringResource(Res.string.chat_title_fallback),
                    color = NeonColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.common_back),
                    tint = NeonColors.TextPrimary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonColors.Background),
    )
}

// ── Message bubble ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: ChatMessage, onLongPress: () -> Unit) {
    val isMe = message.isMine
    val failed = message.status == MessageStatus.FAILED
    val bubbleColor = when {
        failed -> NeonColors.Error.copy(alpha = 0.12f)
        isMe -> NeonColors.Primary.copy(alpha = 0.10f)
        else -> NeonColors.Card
    }
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMe) 16.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 16.dp,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                // Long press is the only entry point to delete/report, so the whole
                // bubble is the target rather than a separate affordance that would
                // clutter every message.
                .combinedClickable(onLongClick = onLongPress, onClick = {})
                .padding(12.dp),
        ) {
            if (message.isDeleted) {
                Text(
                    text = stringResource(Res.string.chat_message_deleted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonColors.TextTertiary,
                    fontStyle = FontStyle.Italic,
                )
            } else {
                // Translated text is the hero line when available.
                message.translatedText?.let { translated ->
                    Text(
                        text = translated,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NeonColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // Original text — either hero (if no translation) or secondary.
                Text(
                    text = message.text,
                    style = if (message.translatedText != null) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (message.translatedText != null) {
                        NeonColors.TextTertiary
                    } else {
                        NeonColors.TextPrimary
                    },
                )
            }

            Spacer(Modifier.height(4.dp))
            MessageFooter(message = message, modifier = Modifier.align(Alignment.End))
        }
    }
}

/**
 * The line under a bubble: the clock once delivered, or the delivery state while it
 * is still in flight.
 *
 * A pending message has no server timestamp yet and a failed one may never get one,
 * so showing a clock for either would be a claim the app cannot back up.
 */
@Composable
private fun MessageFooter(message: ChatMessage, modifier: Modifier = Modifier) {
    when (message.status) {
        MessageStatus.SENDING -> Text(
            text = stringResource(Res.string.chat_status_sending),
            style = MaterialTheme.typography.labelSmall,
            color = NeonColors.TextTertiary,
            modifier = modifier,
        )

        MessageStatus.FAILED -> Text(
            text = stringResource(Res.string.chat_status_failed),
            style = MaterialTheme.typography.labelSmall,
            color = NeonColors.Error,
            fontWeight = FontWeight.Medium,
            modifier = modifier,
        )

        MessageStatus.SENT -> {
            // Empty for a document whose timestamp never landed — "00:00" would be
            // indistinguishable from a real midnight message.
            val clock = formatClockTime(message.timestamp)
            if (clock.isNotEmpty()) {
                Text(
                    text = clock,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonColors.TextTertiary,
                    modifier = modifier,
                )
            }
        }
    }
}

// ── Long-press actions ──────────────────────────────────────────────────

/**
 * Actions offered for one message.
 *
 * The set is derived from who sent it and whether it made it to the server: you can
 * retract your own delivered message, retry or discard your own failed one, and
 * report someone else's. Reporting your own message is not offered — it would only
 * ever be a mis-tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = NeonColors.Card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            when {
                message.status == MessageStatus.FAILED -> {
                    SheetAction(Res.string.chat_action_retry, onRetry)
                    SheetAction(Res.string.chat_action_discard, onDiscard, destructive = true)
                }

                message.isMine -> {
                    SheetAction(Res.string.chat_action_delete, onDelete, destructive = true)
                }

                else -> {
                    SheetAction(Res.string.chat_action_report, onReport, destructive = true)
                }
            }
            SheetAction(Res.string.chat_action_cancel, onDismiss)
        }
    }
}

@Composable
private fun SheetAction(
    label: StringResource,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.bodyLarge,
        color = if (destructive) NeonColors.Error else NeonColors.TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickableCompat(onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

/** A plain clickable; extracted so every sheet row has the same hit area and ripple. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

// ── Report reasons ──────────────────────────────────────────────────────

/**
 * Reason picker for a reported message.
 *
 * Reuses the profile report vocabulary so a moderator sees one consistent set of
 * reasons regardless of what was reported. "Inappropriate photo" is left out — it
 * cannot apply to a text message.
 */
@Composable
private fun ReportReasonDialog(
    onDismiss: () -> Unit,
    onReasonSelected: (String) -> Unit,
) {
    // Each reason carries the key stored in the report document alongside its label.
    // Pairing them here rather than mapping a StringResource back to a key keeps the
    // stored vocabulary from depending on resource-object identity.
    val reasons = listOf(
        Res.string.report_reason_spam to "spam",
        Res.string.report_reason_harassment to "harassment",
        Res.string.report_reason_other to "other",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeonColors.Card,
        title = {
            Text(
                text = stringResource(Res.string.chat_report_title),
                color = NeonColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.chat_report_subtitle),
                    color = NeonColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                reasons.forEach { (label, key) ->
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NeonColors.TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            // The stored reason is the language-independent key, not
                            // the localized label: a moderator must read one
                            // vocabulary, not the reporter's language.
                            .combinedClickableCompat { onReasonSelected(key) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.chat_action_cancel), color = NeonColors.TextSecondary)
            }
        },
    )
}

/** The localized sentence shown for each way a send can fail. */
private fun ChatSendError.messageRes(): StringResource = when (this) {
    ChatSendError.NOT_MATCHED -> Res.string.chat_error_not_matched
    ChatSendError.RATE_LIMITED -> Res.string.chat_error_rate_limited
    ChatSendError.GENERIC -> Res.string.chat_error_send_failed
}

// ── Composer ────────────────────────────────────────────────────────────

@Composable
private fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    // Never gated on an in-flight send: with optimistic delivery the previous message
    // is already on screen, so blocking the button would stop the user typing the next
    // one for no reason the conversation can show.
    val canSend = text.isNotBlank()

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = stringResource(Res.string.chat_composer_placeholder),
                    color = NeonColors.TextTertiary,
                )
            },
            shape = RoundedCornerShape(20.dp),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = NeonColors.TextPrimary,
                unfocusedTextColor = NeonColors.TextPrimary,
                focusedBorderColor = NeonColors.Primary,
                unfocusedBorderColor = NeonColors.CardBorder,
                cursorColor = NeonColors.Primary,
                focusedContainerColor = NeonColors.InputBackground,
                unfocusedContainerColor = NeonColors.InputBackground,
            ),
        )

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canSend) BrandGradient else SolidColor(NeonColors.InputBackground)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onSend, enabled = canSend) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(Res.string.chat_send_cd),
                    tint = if (canSend) Color.White else NeonColors.TextTertiary,
                )
            }
        }
    }
}
