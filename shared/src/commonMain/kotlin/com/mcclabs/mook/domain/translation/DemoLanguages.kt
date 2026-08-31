package com.mcclabs.mook.domain.translation

import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.model.Languages

/**
 * The language pair offered inside the live-translation demo.
 *
 * There is exactly one language catalogue in this app — [Languages], whose codes are
 * already DeepL's spelling and are shared with WalkTalk and with the room picker.
 * This object does not define new codes; it only picks an ordered, demo-sized subset
 * out of that catalogue, so a code can never drift between the demo and the rest of
 * the app. `DemoLanguagesTest` asserts that every code here still resolves through
 * [Languages.fromCode].
 */
object DemoLanguages {

    /** Longest message the demo will send in one go, in characters. */
    const val MAX_INPUT_CHARS: Int = 500

    /**
     * Codes offered in the demo's language chips, in display order.
     *
     * Note: Arabic (`AR`) is intentionally absent — it is not in [Languages], and
     * adding it here alone would fork the catalogue. Add it to [Languages] first if
     * WalkTalk starts offering it.
     */
    private val CODES: List<String> = listOf(
        "TR", "EN-US", "EN-GB", "DE", "FR", "ES", "IT",
        "PT-BR", "RU", "JA", "KO", "ZH", "PL", "NL", "UK",
    )

    /** The demo's selectable languages, resolved from the shared catalogue. */
    val OPTIONS: List<Language> = CODES.mapNotNull(Languages::fromCode)

    /** Left participant's default language: Turkish. */
    val DEFAULT_LEFT: Language = Languages.fromCode("TR") ?: Languages.DEFAULT

    /** Right participant's default language: American English. */
    val DEFAULT_RIGHT: Language = Languages.DEFAULT
}
