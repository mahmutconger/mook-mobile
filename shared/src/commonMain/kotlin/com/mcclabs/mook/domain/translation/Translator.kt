package com.mcclabs.mook.domain.translation

/**
 * Translates a single piece of text from one language into another.
 *
 * The interface lives in `domain` on purpose: everything above it (the demo engine,
 * the view-model, the UI) depends only on this contract, never on Ktor, Firebase or
 * any concrete transport. That keeps the logic layer free of platform imports and
 * lets [com.mcclabs.mook.data.translation.FakeTranslator] stand in for the network
 * in unit tests and Compose previews.
 *
 * Implementations MUST NOT log the text they are given: everything passing through
 * here is user-authored content and is treated as sensitive.
 */
interface Translator {

    /**
     * Translates [text] into [target].
     *
     * @param text   The text to translate. Implementations trim it before sending.
     * @param source Source language code, or `null` to let the provider auto-detect.
     * @param target Target language code. Codes come from
     *   [com.mcclabs.mook.domain.model.Languages] (DeepL spelling, e.g. `TR`, `EN-US`).
     * @return The translated text, never blank.
     * @throws TranslationException with a typed [TranslationError] for every failure.
     *   Implementations never leak provider or transport exceptions to callers.
     */
    suspend fun translate(text: String, source: String?, target: String): String
}

/**
 * The complete set of translation failures the UI knows how to render.
 *
 * Every one of these maps to a user-visible, recoverable message with a retry
 * affordance — there is no "unknown crash" path.
 */
enum class TranslationError {
    /** No usable connection: request never reached the backend. */
    NETWORK,

    /** The backend accepted the request but did not answer in time. */
    TIMEOUT,

    /** Too many requests — the caller should back off and retry shortly. */
    RATE_LIMITED,

    /** Empty text, or text longer than the demo's per-message limit. */
    INVALID_INPUT,

    /** The Firebase session is missing or rejected; the user must sign in again. */
    UNAUTHENTICATED,

    /** Anything else: provider outage, bad response shape, unexpected status. */
    SERVER,
}

/**
 * The only exception type a [Translator] is allowed to throw.
 *
 * Deliberately carries no `cause` and no message beyond the enum name, so a stack
 * trace or crash report can never end up containing the user's message text.
 */
class TranslationException(val error: TranslationError) : Exception(error.name)
