package com.mcclabs.mook.walktalkdemo

import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.domain.translation.DemoLanguages
import com.mcclabs.mook.domain.translation.TranslationError
import com.mcclabs.mook.domain.translation.TranslationException
import com.mcclabs.mook.domain.translation.Translator
import com.mcclabs.mook.feature.walktalkdemo.BubbleTranslation
import com.mcclabs.mook.feature.walktalkdemo.ChatSide
import com.mcclabs.mook.feature.walktalkdemo.DemoSeed
import com.mcclabs.mook.feature.walktalkdemo.LivePreview
import com.mcclabs.mook.feature.walktalkdemo.LiveTranslationEngine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val DEBOUNCE = 400L

/**
 * A [Translator] whose every call parks until the test resumes it by index.
 *
 * Deliberately built on `suspendCoroutine` rather than `suspendCancellableCoroutine`:
 * that is what lets a test deliver a response to a request the engine has already
 * cancelled, which is the only way to prove the generation guard does real work rather
 * than being redundant with cancellation.
 */
private class ManualTranslator : Translator {
    private val pending = mutableListOf<Continuation<String>>()

    /** Every text handed to [translate], in order; indexes line up with the resume calls. */
    val requested = mutableListOf<String>()

    override suspend fun translate(text: String, source: String?, target: String): String {
        requested += text
        return suspendCoroutine { continuation -> pending += continuation }
    }

    fun resumeRequest(index: Int, translated: String) = pending[index].resume(translated)

    fun failRequest(index: Int, error: TranslationError) =
        pending[index].resumeWithException(TranslationException(error))
}

/** A [Translator] driven by virtual time, for everything that is not a race. */
private class ScriptedTranslator(
    private val plan: (text: String, callIndex: Int) -> Script,
) : Translator {

    data class Script(
        val delayMillis: Long = 0L,
        val result: String? = null,
        val error: TranslationError? = null,
        val cancellable: Boolean = true,
    )

    val requested = mutableListOf<String>()

    override suspend fun translate(text: String, source: String?, target: String): String {
        val script = plan(text, requested.size)
        requested += text
        if (script.delayMillis > 0) {
            if (script.cancellable) {
                delay(script.delayMillis)
            } else {
                withContext(NonCancellable) { delay(script.delayMillis) }
            }
        }
        script.error?.let { throw TranslationException(it) }
        return script.result ?: "<$target> $text"
    }
}

class LiveTranslationEngineTest {

    // ------------------------------------------------------------------ debounce

    @Test
    fun noRequestIsIssuedBeforeTheDebounceElapses() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("merhaba")
        advanceTimeBy(DEBOUNCE - 1)
        runCurrent()

        assertTrue(translator.requested.isEmpty(), "Debounce must not have fired yet")

        advanceUntilIdle()
        assertEquals(listOf("merhaba"), translator.requested)
    }

    @Test
    fun rapidTypingIssuesOnlyTheTrailingTranslation() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("m")
        advanceTimeBy(100); runCurrent()
        engine.onComposerTextChange("me")
        advanceTimeBy(100); runCurrent()
        engine.onComposerTextChange("mer")
        advanceTimeBy(100); runCurrent()
        engine.onComposerTextChange("merhaba")
        advanceUntilIdle()

        assertEquals(listOf("merhaba"), translator.requested)
        assertEquals(LivePreview.Ready("<EN-US> merhaba"), engine.state.value.preview)
    }

    @Test
    fun clearingTheComposerCancelsThePendingPreview() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("merhaba")
        advanceTimeBy(100); runCurrent()
        engine.onComposerTextChange("")
        advanceUntilIdle()

        assertTrue(translator.requested.isEmpty())
        assertEquals(LivePreview.Idle, engine.state.value.preview)
    }

    // ---------------------------------------------------------- generation token

    @Test
    fun aSlowOlderPreviewResponseNeverOverwritesANewerOne() = runTest {
        val translator = ManualTranslator()
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("bir")
        advanceTimeBy(DEBOUNCE + 1); runCurrent()
        assertEquals(listOf("bir"), translator.requested)

        // The user keeps typing while the first request is still out.
        engine.onComposerTextChange("iki")
        advanceTimeBy(DEBOUNCE + 1); runCurrent()
        assertEquals(listOf("bir", "iki"), translator.requested)

        // The newer request answers first and lands.
        translator.resumeRequest(1, "TWO")
        runCurrent()
        assertEquals(LivePreview.Ready("TWO"), engine.state.value.preview)

        // The older one answers late. It must be dropped, not rendered.
        translator.resumeRequest(0, "ONE")
        advanceUntilIdle()
        assertEquals(LivePreview.Ready("TWO"), engine.state.value.preview)
    }

    @Test
    fun aSlowOlderBubbleResponseNeverOverwritesARetry() = runTest {
        val translator = ManualTranslator()
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("selam")
        engine.onSend()
        runCurrent()
        assertEquals(listOf("selam"), translator.requested)

        val messageId = engine.state.value.messages.single().id
        engine.onRetryMessage(messageId)
        runCurrent()
        assertEquals(listOf("selam", "selam"), translator.requested)

        translator.resumeRequest(1, "NEW")
        runCurrent()
        assertEquals(
            BubbleTranslation.Done("NEW"),
            engine.state.value.messages.single().translation,
        )

        translator.resumeRequest(0, "OLD")
        advanceUntilIdle()
        assertEquals(
            BubbleTranslation.Done("NEW"),
            engine.state.value.messages.single().translation,
        )
    }

    // ---------------------------------------------------------------------- send

    @Test
    fun sendReusesTheResolvedPreviewInsteadOfTranslatingTwice() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("merhaba")
        advanceUntilIdle()
        engine.onSend()
        advanceUntilIdle()

        assertEquals(1, translator.requested.size, "Preview and send must share one call")

        val message = engine.state.value.messages.single()
        assertEquals(BubbleTranslation.Done("<EN-US> merhaba"), message.translation)
        assertEquals(ChatSide.LEFT, message.side)
        assertEquals("TR", message.sourceCode)
        assertEquals("EN-US", message.targetCode)
        assertEquals("", engine.state.value.composerText)
        assertEquals(LivePreview.Idle, engine.state.value.preview)
    }

    @Test
    fun sendBeforeTheDebounceFiresStillTranslatesExactlyOnce() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("merhaba")
        engine.onSend() // user hits send before the preview request goes out
        advanceUntilIdle()

        assertEquals(listOf("merhaba"), translator.requested)
        assertEquals(
            BubbleTranslation.Done("<EN-US> merhaba"),
            engine.state.value.messages.single().translation,
        )
    }

    @Test
    fun sendFromTheRightSideTranslatesIntoTheLeftLanguage() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onActiveSideChange(ChatSide.RIGHT)
        engine.onComposerTextChange("hello")
        engine.onSend()
        advanceUntilIdle()

        val message = engine.state.value.messages.single()
        assertEquals(ChatSide.RIGHT, message.side)
        assertEquals("EN-US", message.sourceCode)
        assertEquals("TR", message.targetCode)
        assertEquals(BubbleTranslation.Done("<TR> hello"), message.translation)
    }

    // ----------------------------------------------------------- failure + retry

    @Test
    fun aFailedBubbleShowsTheMappedErrorAndRecoversOnRetry() = runTest {
        val translator = ScriptedTranslator { _, callIndex ->
            if (callIndex == 0) {
                ScriptedTranslator.Script(error = TranslationError.NETWORK)
            } else {
                ScriptedTranslator.Script(result = "recovered")
            }
        }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("selam")
        engine.onSend()
        advanceUntilIdle()

        val message = engine.state.value.messages.single()
        assertEquals(BubbleTranslation.Failed(TranslationError.NETWORK), message.translation)

        engine.onRetryMessage(message.id)
        advanceUntilIdle()

        assertEquals(
            BubbleTranslation.Done("recovered"),
            engine.state.value.messages.single().translation,
        )
        assertEquals(1, engine.state.value.messages.single().attempt)
    }

    @Test
    fun everyTranslationErrorSurfacesAsItself() = runTest {
        TranslationError.entries.forEach { error ->
            val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script(error = error) }
            val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

            engine.onComposerTextChange("selam")
            engine.onSend()
            advanceUntilIdle()

            assertEquals(
                BubbleTranslation.Failed(error),
                engine.state.value.messages.single().translation,
                "Error $error must reach the bubble unchanged",
            )
        }
    }

    @Test
    fun aFailedPreviewCanBeRetriedWithoutTyping() = runTest {
        val translator = ScriptedTranslator { _, callIndex ->
            if (callIndex == 0) {
                ScriptedTranslator.Script(error = TranslationError.TIMEOUT)
            } else {
                ScriptedTranslator.Script(result = "hello")
            }
        }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("merhaba")
        advanceUntilIdle()
        assertEquals(LivePreview.Failed(TranslationError.TIMEOUT), engine.state.value.preview)

        engine.onRetryPreview()
        advanceUntilIdle()
        assertEquals(LivePreview.Ready("hello"), engine.state.value.preview)
    }

    @Test
    fun anUnexpectedThrowableBecomesARecoverableServerError() = runTest {
        val translator = object : Translator {
            override suspend fun translate(text: String, source: String?, target: String): String =
                throw IllegalStateException("boom")
        }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("selam")
        engine.onSend()
        advanceUntilIdle()

        assertEquals(
            BubbleTranslation.Failed(TranslationError.SERVER),
            engine.state.value.messages.single().translation,
        )
    }

    // ------------------------------------------------------------------- limits

    @Test
    fun overlongInputIsRejectedWithoutSpendingACall() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("a".repeat(DemoLanguages.MAX_INPUT_CHARS + 1))
        advanceUntilIdle()

        assertTrue(translator.requested.isEmpty())
        assertEquals(LivePreview.Failed(TranslationError.INVALID_INPUT), engine.state.value.preview)
        assertFalse(engine.state.value.canSend)
        assertTrue(engine.state.value.isOverLimit)

        engine.onSend()
        advanceUntilIdle()
        assertTrue(engine.state.value.messages.isEmpty(), "Oversized input must not post a bubble")
    }

    @Test
    fun blankInputDoesNothing() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("   ")
        engine.onSend()
        advanceUntilIdle()

        assertTrue(translator.requested.isEmpty())
        assertTrue(engine.state.value.messages.isEmpty())
    }

    // --------------------------------------------------------- seed + languages

    @Test
    fun theSeededConversationIsTranslatedThroughTheRealContract() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = DemoSeed.DEFAULT)

        assertTrue(engine.state.value.isSeeding)
        advanceUntilIdle()

        val messages = engine.state.value.messages
        assertEquals(DemoSeed.DEFAULT.size, messages.size)
        assertTrue(messages.all { it.translation is BubbleTranslation.Done })
        assertFalse(engine.state.value.isSeeding)
        assertEquals(DemoSeed.DEFAULT.size, translator.requested.size)
    }

    @Test
    fun changingALanguageDoesNotRewriteTheTranscript() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onComposerTextChange("merhaba")
        engine.onSend()
        advanceUntilIdle()

        val german = requireNotNull(Languages.fromCode("DE"))
        engine.onLanguageChange(ChatSide.RIGHT, german)
        advanceUntilIdle()

        val message = engine.state.value.messages.single()
        assertEquals("EN-US", message.targetCode, "History keeps the pair it was sent with")
        assertEquals("DE", engine.state.value.rightLanguage.code)

        engine.onComposerTextChange("tekrar")
        engine.onSend()
        advanceUntilIdle()
        assertEquals("DE", engine.state.value.messages.last().targetCode)
    }

    @Test
    fun aLanguageCannotBeSetToTheOtherSidesLanguage() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        val turkish = requireNotNull(Languages.fromCode("TR"))
        engine.onLanguageChange(ChatSide.RIGHT, turkish)
        advanceUntilIdle()

        assertEquals("EN-US", engine.state.value.rightLanguage.code)
    }

    @Test
    fun swappingLanguagesFlipsBothSides() = runTest {
        val translator = ScriptedTranslator { _, _ -> ScriptedTranslator.Script() }
        val engine = LiveTranslationEngine(translator, this, DEBOUNCE, seed = emptyList())

        engine.onSwapLanguages()
        advanceUntilIdle()

        assertEquals("EN-US", engine.state.value.leftLanguage.code)
        assertEquals("TR", engine.state.value.rightLanguage.code)
    }
}
