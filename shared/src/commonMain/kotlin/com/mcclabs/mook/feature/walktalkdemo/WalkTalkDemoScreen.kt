package com.mcclabs.mook.feature.walktalkdemo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcclabs.mook.data.translation.FakeTranslator
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.translation.DemoLanguages
import com.mcclabs.mook.domain.translation.TranslationError
import com.mcclabs.mook.ui.components.NeonPrimaryButton
import com.mcclabs.mook.ui.theme.BrandGradient
import com.mcclabs.mook.ui.theme.NeonColors
import com.mcclabs.mook.util.rememberWalkTalkChatOpener
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.wtdemo_back_cd
import mook.shared.generated.resources.wtdemo_composer_hint
import mook.shared.generated.resources.wtdemo_cta
import mook.shared.generated.resources.wtdemo_cta_caption
import mook.shared.generated.resources.wtdemo_error_invalid_input
import mook.shared.generated.resources.wtdemo_error_network
import mook.shared.generated.resources.wtdemo_error_rate_limited
import mook.shared.generated.resources.wtdemo_error_server
import mook.shared.generated.resources.wtdemo_error_timeout
import mook.shared.generated.resources.wtdemo_error_unauthenticated
import mook.shared.generated.resources.wtdemo_language_menu_cd
import mook.shared.generated.resources.wtdemo_pitch
import mook.shared.generated.resources.wtdemo_preview_label
import mook.shared.generated.resources.wtdemo_retry
import mook.shared.generated.resources.wtdemo_send_cd
import mook.shared.generated.resources.wtdemo_side_left
import mook.shared.generated.resources.wtdemo_side_right
import mook.shared.generated.resources.wtdemo_swap_cd
import mook.shared.generated.resources.wtdemo_title
import mook.shared.generated.resources.wtdemo_translating
import mook.shared.generated.resources.wtdemo_writer_cd
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Canlı çevirili sohbet" — the in-app WalkTalk showcase.
 *
 * Two participants, each with their own language, one shared transcript. Whatever the
 * active participant types is translated into the other one's language and posted as a
 * bubble whose hero line is the translation. The other participant is simulated locally:
 * no networking beyond the translation proxy, no accounts, nothing written to Firestore.
 *
 * The screen is a pure render of [ChatDemoUiState]; every decision lives in
 * [LiveTranslationEngine].
 */
@Composable
fun WalkTalkDemoScreen(
    onNavigateBack: () -> Unit,
    viewModel: WalkTalkDemoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val openWalkTalk = rememberWalkTalkChatOpener()

    LaunchedEffect(viewModel) {
        viewModel.openWalkTalk.collect { url -> openWalkTalk(url) }
    }

    WalkTalkDemoContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onComposerTextChange = viewModel::onComposerTextChange,
        onActiveSideChange = viewModel::onActiveSideChange,
        onLanguageChange = viewModel::onLanguageChange,
        onSwapLanguages = viewModel::onSwapLanguages,
        onSend = viewModel::onSend,
        onRetryMessage = viewModel::onRetryMessage,
        onRetryPreview = viewModel::onRetryPreview,
        onOpenWalkTalk = viewModel::onOpenWalkTalkClicked,
    )
}

/**
 * Stateless body of the demo, so it can be rendered from a preview or a screenshot
 * test without Koin, a view-model or a translator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkTalkDemoContent(
    state: ChatDemoUiState,
    onNavigateBack: () -> Unit,
    onComposerTextChange: (String) -> Unit,
    onActiveSideChange: (ChatSide) -> Unit,
    onLanguageChange: (ChatSide, Language) -> Unit,
    onSwapLanguages: () -> Unit,
    onSend: () -> Unit,
    onRetryMessage: (Long) -> Unit,
    onRetryPreview: () -> Unit,
    onOpenWalkTalk: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Keep the newest bubble in view, including while a seed line resolves.
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.translation) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = NeonColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.wtdemo_title),
                        color = NeonColors.Primary,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.wtdemo_back_cd),
                            tint = NeonColors.TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonColors.Background),
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
                PreviewStrip(
                    preview = state.preview,
                    targetLanguage = state.targetLanguage,
                    onRetry = onRetryPreview,
                )
                Composer(
                    text = state.composerText,
                    activeLanguage = state.activeLanguage,
                    canSend = state.canSend,
                    isOverLimit = state.isOverLimit,
                    onTextChange = onComposerTextChange,
                    onSend = onSend,
                )
                CtaBar(onOpenWalkTalk = onOpenWalkTalk)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Pitch()
            ParticipantRow(
                state = state,
                onActiveSideChange = onActiveSideChange,
                onLanguageChange = onLanguageChange,
                onSwapLanguages = onSwapLanguages,
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onRetry = { onRetryMessage(message.id) },
                        errorText = { it.displayText() },
                    )
                }
            }

            if (state.isTranslating) {
                TypingIndicator(
                    languageName = state.targetLanguage.name,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/** One-line value proposition under the app bar. */
@Composable
private fun Pitch() {
    Text(
        text = stringResource(Res.string.wtdemo_pitch),
        style = MaterialTheme.typography.bodyMedium,
        color = NeonColors.TextSecondary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** The two participants, their languages, and the swap control between them. */
@Composable
private fun ParticipantRow(
    state: ChatDemoUiState,
    onActiveSideChange: (ChatSide) -> Unit,
    onLanguageChange: (ChatSide, Language) -> Unit,
    onSwapLanguages: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ParticipantChip(
            label = stringResource(Res.string.wtdemo_side_left),
            language = state.leftLanguage,
            isActive = state.activeSide == ChatSide.LEFT,
            onSelect = { onActiveSideChange(ChatSide.LEFT) },
            onLanguageChange = { onLanguageChange(ChatSide.LEFT, it) },
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onSwapLanguages) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = stringResource(Res.string.wtdemo_swap_cd),
                tint = NeonColors.Primary,
            )
        }

        ParticipantChip(
            label = stringResource(Res.string.wtdemo_side_right),
            language = state.rightLanguage,
            isActive = state.activeSide == ChatSide.RIGHT,
            onSelect = { onActiveSideChange(ChatSide.RIGHT) },
            onLanguageChange = { onLanguageChange(ChatSide.RIGHT, it) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A participant's card: tap the body to make them the writer, tap the chevron to change
 * their language. Two separate targets so each carries its own accessible action.
 */
@Composable
private fun ParticipantChip(
    label: String,
    language: Language,
    isActive: Boolean,
    onSelect: () -> Unit,
    onLanguageChange: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val writerCd = stringResource(Res.string.wtdemo_writer_cd, label)
    val menuCd = stringResource(Res.string.wtdemo_language_menu_cd, label)

    Row(
        modifier = modifier
            .clip(shape)
            .background(if (isActive) NeonColors.Primary.copy(alpha = 0.12f) else NeonColors.Card)
            .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSelect)
                .semantics { contentDescription = writerCd },
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) NeonColors.Primary else NeonColors.TextTertiary,
            )
            Text(
                text = "${language.flagEmoji} ${language.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = NeonColors.TextPrimary,
                maxLines = 1,
            )
        }

        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = menuCd,
                    tint = NeonColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(NeonColors.Surface),
            ) {
                DemoLanguages.OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "${option.flagEmoji} ${option.name}",
                                color = if (option.code == language.code) {
                                    NeonColors.Primary
                                } else {
                                    NeonColors.TextPrimary
                                },
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onLanguageChange(option)
                        },
                    )
                }
            }
        }
    }
}

/** Debounced preview of what the other participant is about to read. */
@Composable
private fun PreviewStrip(
    preview: LivePreview,
    targetLanguage: Language,
    onRetry: () -> Unit,
) {
    if (preview is LivePreview.Idle) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    Res.string.wtdemo_preview_label,
                    "${targetLanguage.flagEmoji} ${targetLanguage.name}",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = NeonColors.TextTertiary,
            )
            when (preview) {
                is LivePreview.InFlight -> Text(
                    text = stringResource(Res.string.wtdemo_translating),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonColors.TextSecondary,
                )

                is LivePreview.Ready -> Text(
                    text = preview.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonColors.TextPrimary,
                )

                is LivePreview.Failed -> Text(
                    text = preview.error.displayText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonColors.Error,
                )

                LivePreview.Idle -> Unit
            }
        }

        if (preview is LivePreview.Failed) {
            TextButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = NeonColors.Primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.wtdemo_retry),
                    color = NeonColors.Primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Text field plus send action, labelled with the language the writer is using. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    text: String,
    activeLanguage: Language,
    canSend: Boolean,
    isOverLimit: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = stringResource(Res.string.wtdemo_composer_hint, activeLanguage.name),
                    color = NeonColors.TextTertiary,
                )
            },
            isError = isOverLimit,
            supportingText = if (isOverLimit) {
                {
                    Text(
                        text = stringResource(
                            Res.string.wtdemo_error_invalid_input,
                            DemoLanguages.MAX_INPUT_CHARS,
                        ),
                        color = NeonColors.Error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else {
                null
            },
            shape = RoundedCornerShape(20.dp),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = NeonColors.TextPrimary,
                unfocusedTextColor = NeonColors.TextPrimary,
                focusedBorderColor = NeonColors.Primary,
                unfocusedBorderColor = NeonColors.CardBorder,
                errorBorderColor = NeonColors.Error,
                cursorColor = NeonColors.Primary,
                focusedContainerColor = NeonColors.InputBackground,
                unfocusedContainerColor = NeonColors.InputBackground,
                errorContainerColor = NeonColors.InputBackground,
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
                    contentDescription = stringResource(Res.string.wtdemo_send_cd),
                    tint = if (canSend) Color.White else NeonColors.TextTertiary,
                )
            }
        }
    }
}

/** The conversion block: one sentence of why, one gradient button. */
@Composable
private fun CtaBar(onOpenWalkTalk: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.wtdemo_cta_caption),
            style = MaterialTheme.typography.labelSmall,
            color = NeonColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        NeonPrimaryButton(
            text = stringResource(Res.string.wtdemo_cta),
            onClick = onOpenWalkTalk,
        )
    }
}

/** Single place where a [TranslationError] becomes something a person can read and act on. */
@Composable
internal fun TranslationError.displayText(): String = when (this) {
    TranslationError.NETWORK -> stringResource(Res.string.wtdemo_error_network)
    TranslationError.TIMEOUT -> stringResource(Res.string.wtdemo_error_timeout)
    TranslationError.RATE_LIMITED -> stringResource(Res.string.wtdemo_error_rate_limited)
    TranslationError.INVALID_INPUT ->
        stringResource(Res.string.wtdemo_error_invalid_input, DemoLanguages.MAX_INPUT_CHARS)
    TranslationError.UNAUTHENTICATED -> stringResource(Res.string.wtdemo_error_unauthenticated)
    TranslationError.SERVER -> stringResource(Res.string.wtdemo_error_server)
}

// ------------------------------------------------------------------- previews

private fun previewMessage(
    id: Long,
    side: ChatSide,
    original: String,
    translation: BubbleTranslation,
): DemoMessage = DemoMessage(
    id = id,
    side = side,
    originalText = original,
    sourceCode = if (side == ChatSide.LEFT) "TR" else "EN-US",
    targetCode = if (side == ChatSide.LEFT) "EN-US" else "TR",
    translation = translation,
)

/** The happy path: a resolved bubble, one in flight, and a live preview. */
@Preview
@Composable
private fun WalkTalkDemoContentPreview() {
    WalkTalkDemoContent(
        state = ChatDemoUiState(
            isSeeding = false,
            composerText = "Merhaba",
            preview = LivePreview.Ready("Hello"),
            messages = listOf(
                previewMessage(
                    0,
                    ChatSide.LEFT,
                    "Merhaba! Ben Türkçe yazıyorum, sen kendi dilinde okuyorsun.",
                    BubbleTranslation.Done(
                        "Hi! I'm writing in Turkish and you're reading it in your own language.",
                    ),
                ),
                previewMessage(
                    1,
                    ChatSide.RIGHT,
                    "That is wild — I do not speak a word of Turkish.",
                    BubbleTranslation.Done("Bu inanılmaz — tek kelime Türkçe bilmiyorum."),
                ),
                previewMessage(2, ChatSide.LEFT, "Aynen.", BubbleTranslation.InFlight),
            ),
        ),
        onNavigateBack = {},
        onComposerTextChange = {},
        onActiveSideChange = {},
        onLanguageChange = { _, _ -> },
        onSwapLanguages = {},
        onSend = {},
        onRetryMessage = {},
        onRetryPreview = {},
        onOpenWalkTalk = {},
    )
}

/** The degraded path: the network is gone, and every state stays recoverable. */
@Preview
@Composable
private fun WalkTalkDemoContentOfflinePreview() {
    WalkTalkDemoContent(
        state = ChatDemoUiState(
            isSeeding = false,
            composerText = "Nasılsın?",
            preview = LivePreview.Failed(TranslationError.NETWORK),
            messages = listOf(
                previewMessage(
                    0,
                    ChatSide.LEFT,
                    "Merhaba!",
                    BubbleTranslation.Failed(TranslationError.NETWORK),
                ),
            ),
        ),
        onNavigateBack = {},
        onComposerTextChange = {},
        onActiveSideChange = {},
        onLanguageChange = { _, _ -> },
        onSwapLanguages = {},
        onSend = {},
        onRetryMessage = {},
        onRetryPreview = {},
        onOpenWalkTalk = {},
    )
}

/**
 * The live screen driven by [FakeTranslator] — the same [LiveTranslationEngine] the app
 * runs, with the network swapped out, so the preview exercises debounce and the seeded
 * conversation for real.
 */
@Preview
@Composable
private fun WalkTalkDemoContentFakeTranslatorPreview() {
    val scope = rememberCoroutineScope()
    val engine = remember {
        LiveTranslationEngine(translator = FakeTranslator(latencyMillis = 600), scope = scope)
    }
    val state by engine.state.collectAsState()

    WalkTalkDemoContent(
        state = state,
        onNavigateBack = {},
        onComposerTextChange = engine::onComposerTextChange,
        onActiveSideChange = engine::onActiveSideChange,
        onLanguageChange = engine::onLanguageChange,
        onSwapLanguages = engine::onSwapLanguages,
        onSend = engine::onSend,
        onRetryMessage = engine::onRetryMessage,
        onRetryPreview = engine::onRetryPreview,
        onOpenWalkTalk = {},
    )
}
