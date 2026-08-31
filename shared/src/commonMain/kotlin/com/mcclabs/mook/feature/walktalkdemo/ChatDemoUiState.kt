package com.mcclabs.mook.feature.walktalkdemo

import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.translation.DemoLanguages
import com.mcclabs.mook.domain.translation.TranslationError

/** Which of the two demo participants a message belongs to. */
enum class ChatSide {
    LEFT,
    RIGHT;

    /** The participant on the receiving end of a message from this side. */
    val opposite: ChatSide get() = if (this == LEFT) RIGHT else LEFT
}

/**
 * Translation status of a single bubble.
 *
 * A bubble is never rendered without one of these three states, which is what keeps
 * the "never show a blank bubble" rule structural rather than a convention.
 */
sealed interface BubbleTranslation {
    /** Request in flight — the bubble shows a subtle "translating…" shimmer. */
    data object InFlight : BubbleTranslation

    /** Resolved translation, rendered as the bubble's hero line. */
    data class Done(val text: String) : BubbleTranslation

    /** Recoverable failure; the bubble shows the reason plus a retry affordance. */
    data class Failed(val error: TranslationError) : BubbleTranslation
}

/**
 * One message in the demo transcript.
 *
 * [sourceCode] / [targetCode] are captured at post time and never change afterwards:
 * changing a participant's language must not rewrite history, only affect what is
 * sent next.
 *
 * @param attempt Bumped on every retry. A response only lands if it still carries the
 *   attempt that is current for this message — the per-bubble generation token.
 */
data class DemoMessage(
    val id: Long,
    val side: ChatSide,
    val originalText: String,
    val sourceCode: String,
    val targetCode: String,
    val translation: BubbleTranslation,
    val attempt: Int = 0,
)

/**
 * The debounced preview shown under the composer while the user types: what the other
 * participant is about to read.
 */
sealed interface LivePreview {
    /** Nothing typed, or the field was just cleared. */
    data object Idle : LivePreview

    /** Debounce elapsed, request in flight. */
    data object InFlight : LivePreview

    /** Preview ready for the current composer text. */
    data class Ready(val text: String) : LivePreview

    /** Preview failed; sending still works and will retry on its own. */
    data class Failed(val error: TranslationError) : LivePreview
}

/**
 * The complete, immutable state of the demo screen.
 *
 * The UI is a pure function of this value — no composable reads the translator, a
 * repository or a clock directly.
 */
data class ChatDemoUiState(
    val leftLanguage: Language = DemoLanguages.DEFAULT_LEFT,
    val rightLanguage: Language = DemoLanguages.DEFAULT_RIGHT,
    val activeSide: ChatSide = ChatSide.LEFT,
    val composerText: String = "",
    val preview: LivePreview = LivePreview.Idle,
    val messages: List<DemoMessage> = emptyList(),
    val isSeeding: Boolean = true,
) {
    /** Language the active participant writes in. */
    val activeLanguage: Language
        get() = languageOf(activeSide)

    /** Language the message will be translated into. */
    val targetLanguage: Language
        get() = languageOf(activeSide.opposite)

    fun languageOf(side: ChatSide): Language =
        if (side == ChatSide.LEFT) leftLanguage else rightLanguage

    /** `true` while any bubble is still resolving — drives the typing indicator. */
    val isTranslating: Boolean
        get() = messages.any { it.translation is BubbleTranslation.InFlight }

    /** Composer content is postable (non-blank and within the length limit). */
    val canSend: Boolean
        get() = composerText.trim().let { it.isNotEmpty() && it.length <= DemoLanguages.MAX_INPUT_CHARS }

    /** `true` once the user has gone past the per-message character budget. */
    val isOverLimit: Boolean
        get() = composerText.trim().length > DemoLanguages.MAX_INPUT_CHARS
}
