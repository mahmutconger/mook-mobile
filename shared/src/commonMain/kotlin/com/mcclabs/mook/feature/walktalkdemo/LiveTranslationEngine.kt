package com.mcclabs.mook.feature.walktalkdemo

import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.translation.DemoLanguages
import com.mcclabs.mook.domain.translation.TranslationError
import com.mcclabs.mook.domain.translation.TranslationException
import com.mcclabs.mook.domain.translation.Translator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * All of the demo's behaviour, with no Compose, Firebase or platform dependency.
 *
 * Split out of the view-model so every tricky part — debounce, the generation-token
 * guard, error mapping, retry — is testable by handing it a `TestScope` and a
 * [com.mcclabs.mook.data.translation.FakeTranslator].
 *
 * **Staleness.** Two independent guards, because cancellation alone is not enough:
 * a request already inside the provider call can still return after a newer one.
 *  - The debounced preview carries a monotonically increasing generation; a result is
 *    applied only while its generation is still the current one.
 *  - Each bubble carries an [DemoMessage.attempt]; a result is applied only if the
 *    message still has the attempt the request was issued for.
 * In both cases the previous [Job] is also cancelled, so the common path does not
 * even reach the provider.
 *
 * Nothing in this class logs message text.
 *
 * @param translator    The (injected) translation contract.
 * @param scope         Scope owning every request; the view-model passes `viewModelScope`,
 *                      tests pass a `TestScope` so virtual time drives the debounce.
 * @param debounceMillis Idle time before a keystroke turns into a preview request.
 * @param seed          Opening conversation; pass an empty list to start blank.
 */
class LiveTranslationEngine(
    private val translator: Translator,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val maxInputChars: Int = DemoLanguages.MAX_INPUT_CHARS,
    seed: List<SeedLine> = DemoSeed.DEFAULT,
) {

    private val _state = MutableStateFlow(ChatDemoUiState(isSeeding = seed.isNotEmpty()))

    /** The single source of truth the UI renders. */
    val state: StateFlow<ChatDemoUiState> = _state.asStateFlow()

    private var nextMessageId: Long = 0L

    /** Generation of the newest composer input; guards the debounced preview. */
    private var previewGeneration: Long = 0L
    private var previewJob: Job? = null

    /** Last successful preview, reused on send so one keystroke costs one call, not two. */
    private var previewCache: PreviewCache? = null

    private val messageJobs = mutableMapOf<Long, Job>()

    init {
        if (seed.isNotEmpty()) seedConversation(seed)
    }

    // ---------------------------------------------------------------- composing

    /**
     * Records a keystroke and schedules the debounced preview.
     *
     * Every call invalidates the previous generation, so a burst of typing issues
     * exactly one request — the trailing one.
     */
    fun onComposerTextChange(text: String) {
        _state.update { it.copy(composerText = text) }

        val generation = ++previewGeneration
        previewJob?.cancel()
        previewJob = null

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            previewCache = null
            _state.update { it.copy(preview = LivePreview.Idle) }
            return
        }
        if (trimmed.length > maxInputChars) {
            previewCache = null
            _state.update { it.copy(preview = LivePreview.Failed(TranslationError.INVALID_INPUT)) }
            return
        }

        val source = _state.value.activeLanguage.code
        val target = _state.value.targetLanguage.code

        previewCache?.let { cached ->
            if (cached.matches(trimmed, source, target)) {
                _state.update { it.copy(preview = LivePreview.Ready(cached.translated)) }
                return
            }
        }

        previewJob = scope.launch {
            delay(debounceMillis)
            if (generation != previewGeneration) return@launch
            _state.update { it.copy(preview = LivePreview.InFlight) }

            val outcome = translateCatching(trimmed, source, target)

            // Generation guard: a slower earlier response must never win.
            if (generation != previewGeneration) return@launch
            when (outcome) {
                is TranslationOutcome.Success -> {
                    previewCache = PreviewCache(trimmed, source, target, outcome.text)
                    _state.update { it.copy(preview = LivePreview.Ready(outcome.text)) }
                }

                is TranslationOutcome.Failure ->
                    _state.update { it.copy(preview = LivePreview.Failed(outcome.error)) }
            }
        }
    }

    /** Switches which participant is composing. Clears the preview, which belonged to the other pair. */
    fun onActiveSideChange(side: ChatSide) {
        if (side == _state.value.activeSide) return
        invalidatePreview()
        _state.update { it.copy(activeSide = side, preview = LivePreview.Idle) }
        // Re-run the debounce for whatever is already typed, now in the new direction.
        _state.value.composerText.takeIf { it.isNotBlank() }?.let(::onComposerTextChange)
    }

    /**
     * Changes one participant's language.
     *
     * Existing bubbles keep the languages they were sent with — a transcript is a
     * record, not a live re-render.
     */
    fun onLanguageChange(side: ChatSide, language: Language) {
        val current = _state.value
        if (current.languageOf(side).code == language.code) return
        // Refuse a pair that would translate a language into itself.
        if (current.languageOf(side.opposite).code == language.code) return

        invalidatePreview()
        _state.update {
            if (side == ChatSide.LEFT) it.copy(leftLanguage = language, preview = LivePreview.Idle)
            else it.copy(rightLanguage = language, preview = LivePreview.Idle)
        }
        _state.value.composerText.takeIf { it.isNotBlank() }?.let(::onComposerTextChange)
    }

    /** Swaps the two participants' languages. */
    fun onSwapLanguages() {
        val current = _state.value
        invalidatePreview()
        _state.update {
            it.copy(
                leftLanguage = current.rightLanguage,
                rightLanguage = current.leftLanguage,
                preview = LivePreview.Idle,
            )
        }
        _state.value.composerText.takeIf { it.isNotBlank() }?.let(::onComposerTextChange)
    }

    // -------------------------------------------------------------------- send

    /**
     * Posts the composer's content as a bubble from the active side.
     *
     * If the debounced preview already resolved this exact text for this exact pair,
     * the bubble opens already translated — no second request, no flicker.
     */
    fun onSend() {
        val current = _state.value
        val trimmed = current.composerText.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.length > maxInputChars) {
            _state.update { it.copy(preview = LivePreview.Failed(TranslationError.INVALID_INPUT)) }
            return
        }

        val source = current.activeLanguage.code
        val target = current.targetLanguage.code
        val cached = previewCache?.takeIf { it.matches(trimmed, source, target) }

        val message = DemoMessage(
            id = nextMessageId++,
            side = current.activeSide,
            originalText = trimmed,
            sourceCode = source,
            targetCode = target,
            translation = cached
                ?.let { BubbleTranslation.Done(it.translated) }
                ?: BubbleTranslation.InFlight,
        )

        invalidatePreview()
        _state.update {
            it.copy(
                messages = it.messages + message,
                composerText = "",
                preview = LivePreview.Idle,
            )
        }

        if (cached == null) launchTranslation(message)
    }

    /** Re-issues the translation for a failed bubble. */
    fun onRetryMessage(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val retried = message.copy(
            attempt = message.attempt + 1,
            translation = BubbleTranslation.InFlight,
        )
        replaceMessage(retried)
        launchTranslation(retried)
    }

    /** Re-issues the debounced preview after a failure, without waiting for a keystroke. */
    fun onRetryPreview() {
        val text = _state.value.composerText
        if (text.isBlank()) return
        previewCache = null
        onComposerTextChange(text)
    }

    // ----------------------------------------------------------------- internal

    private fun seedConversation(seed: List<SeedLine>) {
        val current = _state.value
        val seeded = seed.map { line ->
            DemoMessage(
                id = nextMessageId++,
                side = line.side,
                originalText = line.text,
                sourceCode = current.languageOf(line.side).code,
                targetCode = current.languageOf(line.side.opposite).code,
                translation = BubbleTranslation.InFlight,
            )
        }
        _state.update { it.copy(messages = seeded) }

        scope.launch {
            // Sequential on purpose: three parallel calls on a cold start is a burst the
            // proxy's per-uid rate limit would rather not see, and the staggered arrival
            // reads as a conversation filling in.
            seeded.forEach { message -> translateInto(message) }
            _state.update { it.copy(isSeeding = false) }
        }
    }

    private fun launchTranslation(message: DemoMessage) {
        messageJobs.remove(message.id)?.cancel()
        val job = scope.launch { translateInto(message) }
        messageJobs[message.id] = job
        // Only clear the entry if it is still this job's — a retry may already have
        // replaced it, and dropping the newer job's handle would leak it past cancellation.
        job.invokeOnCompletion {
            if (messageJobs[message.id] === job) messageJobs.remove(message.id)
        }
    }

    private suspend fun translateInto(message: DemoMessage) {
        val resolved = when (val outcome = translateCatching(message.originalText, message.sourceCode, message.targetCode)) {
            is TranslationOutcome.Success -> BubbleTranslation.Done(outcome.text)
            is TranslationOutcome.Failure -> BubbleTranslation.Failed(outcome.error)
        }
        _state.update { state ->
            state.copy(
                messages = state.messages.map { existing ->
                    // Attempt guard: drop a response belonging to a superseded retry.
                    if (existing.id == message.id && existing.attempt == message.attempt) {
                        existing.copy(translation = resolved)
                    } else {
                        existing
                    }
                },
            )
        }
    }

    /**
     * Runs a translation and normalises every outcome into a [TranslationError].
     *
     * `CancellationException` is rethrown so structured concurrency keeps working;
     * anything unexpected becomes [TranslationError.SERVER] rather than a crash.
     */
    private suspend fun translateCatching(
        text: String,
        source: String,
        target: String,
    ): TranslationOutcome = try {
        TranslationOutcome.Success(translator.translate(text, source, target))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: TranslationException) {
        TranslationOutcome.Failure(failure.error)
    } catch (unexpected: Throwable) {
        // A Translator implementation is contractually not supposed to get here, but a
        // demo screen must never crash on one that misbehaves.
        TranslationOutcome.Failure(TranslationError.SERVER)
    }

    /** Internal, exhaustive result of one translation attempt. */
    private sealed interface TranslationOutcome {
        data class Success(val text: String) : TranslationOutcome
        data class Failure(val error: TranslationError) : TranslationOutcome
    }

    private fun invalidatePreview() {
        previewGeneration++
        previewJob?.cancel()
        previewJob = null
        previewCache = null
    }

    private fun replaceMessage(message: DemoMessage) {
        _state.update { state ->
            state.copy(messages = state.messages.map { if (it.id == message.id) message else it })
        }
    }

    private data class PreviewCache(
        val text: String,
        val source: String,
        val target: String,
        val translated: String,
    ) {
        fun matches(text: String, source: String, target: String): Boolean =
            this.text == text && this.source == source && this.target == target
    }

    companion object {
        /** Idle time before typing turns into a request. Inside the 350–500 ms band. */
        const val DEFAULT_DEBOUNCE_MILLIS: Long = 400L
    }
}
