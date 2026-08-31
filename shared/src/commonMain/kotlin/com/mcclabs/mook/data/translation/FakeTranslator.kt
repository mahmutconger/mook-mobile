package com.mcclabs.mook.data.translation

import com.mcclabs.mook.domain.translation.DemoLanguages
import com.mcclabs.mook.domain.translation.TranslationError
import com.mcclabs.mook.domain.translation.TranslationException
import com.mcclabs.mook.domain.translation.Translator
import kotlinx.coroutines.delay

/**
 * A deterministic, offline [Translator] for Compose previews and unit tests.
 *
 * It answers from a tiny fixed dictionary covering the seed conversation, and falls
 * back to a marker form (`[EN-US] merhaba`) for anything else — so a preview always
 * renders something plausible and a test can assert an exact string without a
 * network, a clock or a real provider.
 *
 * Lives in `commonMain` rather than `commonTest` because Compose previews compile
 * against main; it holds no keys and reaches nothing.
 *
 * @param latencyMillis Artificial delay per call, so previews and tests can exercise
 *   the "translating…" state. `0` answers immediately.
 * @param failWith When non-null, every call fails with this error instead — the quick
 *   way to preview or test a failure state.
 */
class FakeTranslator(
    private val latencyMillis: Long = 0L,
    private val failWith: TranslationError? = null,
    private val maxInputChars: Int = DemoLanguages.MAX_INPUT_CHARS,
) : Translator {

    override suspend fun translate(text: String, source: String?, target: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > maxInputChars) {
            throw TranslationException(TranslationError.INVALID_INPUT)
        }
        if (latencyMillis > 0) delay(latencyMillis)
        failWith?.let { throw TranslationException(it) }

        return DICTIONARY[target]?.get(trimmed.lowercase())
            ?: "[$target] $trimmed"
    }

    private companion object {
        /** target code -> (lowercased original -> translation). */
        val DICTIONARY: Map<String, Map<String, String>> = mapOf(
            "EN-US" to mapOf(
                "merhaba! ben türkçe yazıyorum, sen kendi dilinde okuyorsun." to
                    "Hi! I'm writing in Turkish and you're reading it in your own language.",
                "aynen. çeviri arada, biz fark etmeden çalışıyor." to
                    "Exactly. The translation sits in between and you never notice it.",
                "merhaba" to "hello",
                "nasılsın?" to "how are you?",
            ),
            "TR" to mapOf(
                "that is wild — i do not speak a word of turkish." to
                    "Bu inanılmaz — tek kelime Türkçe bilmiyorum.",
                "hello" to "merhaba",
                "how are you?" to "nasılsın?",
            ),
        )
    }
}
