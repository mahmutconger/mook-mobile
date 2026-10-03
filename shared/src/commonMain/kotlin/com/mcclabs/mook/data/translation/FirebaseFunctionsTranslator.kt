package com.mcclabs.mook.data.translation

import com.mcclabs.mook.domain.translation.DemoLanguages
import com.mcclabs.mook.domain.translation.TranslationError
import com.mcclabs.mook.domain.translation.TranslationException
import com.mcclabs.mook.domain.translation.Translator
import com.mcclabs.mook.domain.moderation.BannedUserException
import com.mcclabs.mook.domain.moderation.ModerationGate
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** Request body sent to the `translateText` callable. */
@Serializable
internal data class TranslateRequest(
    val text: String,
    val source: String? = null,
    val target: String,
)

/** Response body returned by the `translateText` callable. */
@Serializable
internal data class TranslateResponse(
    val translatedText: String,
    val detectedSource: String? = null,
)

/**
 * The production [Translator]: a thin client over a Firebase callable function that
 * proxies DeepL server-side.
 *
 * **Why a callable and not a raw HTTPS POST.** The DeepL key must never reach the
 * client, so a proxy is mandatory either way. Mook already proxies through callables
 * (`generateSsoToken`), and a callable attaches the caller's Firebase ID token
 * automatically — so the backend can rate-limit per uid without this class ever
 * touching a token. The transport contract is otherwise identical to a plain
 * `POST {endpoint} { text, source, target } -> { translatedText, detectedSource }`;
 * swapping in a Ktor implementation means writing another [Translator] and changing
 * one line of Koin wiring.
 *
 * Nothing here logs [TranslateRequest.text] or the translated result.
 *
 * @param functions    Injected so tests and emulator runs can supply their own instance.
 * @param callableName Name of the deployed function; see `functions/index.js`.
 * @param maxInputChars Client-side guard so obviously oversized input never costs a call.
 */
class FirebaseFunctionsTranslator(
    private val functions: FirebaseFunctions = Firebase.functions,
    private val callableName: String = CALLABLE_NAME,
    private val maxInputChars: Int = DemoLanguages.MAX_INPUT_CHARS,
) : Translator {

    override suspend fun translate(text: String, source: String?, target: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > maxInputChars) {
            throw TranslationException(TranslationError.INVALID_INPUT)
        }
        if (target.isBlank()) {
            throw TranslationException(TranslationError.INVALID_INPUT)
        }
        // Gereksinim 1.13: bu sınıf, test edilebilirlik için kendi `functions` örneğini
        // DI ile aldığından `appHttpsCallable()` sarmalayıcısını KULLANAMAZ (o, yalnızca
        // global `Firebase.functions` tekilini sarar) — bu yüzden aynı global kapı burada
        // ayrıca, doğrudan kontrol edilir.
        if (ModerationGate.isBanned.value) throw BannedUserException()

        val response = try {
            functions
                .httpsCallable(callableName)
                .invoke(TranslateRequest(text = trimmed, source = source, target = target))
                .data<TranslateResponse>()
        } catch (cancellation: CancellationException) {
            // Cooperative cancellation is not a translation failure — let it propagate
            // so the caller's structured concurrency still works.
            throw cancellation
        } catch (error: Throwable) {
            throw TranslationException(mapCallableErrorCode(callableErrorCode(error)))
        }

        val translated = response.translatedText.trim()
        if (translated.isEmpty()) {
            // A blank body would render as an empty bubble; treat it as a server fault
            // so the user gets the recoverable "try again" state instead.
            throw TranslationException(TranslationError.SERVER)
        }
        return translated
    }

    private companion object {
        const val CALLABLE_NAME = "translateText"
    }
}

/**
 * Extracts the Firebase status code from [error], or `null` when it is not a callable
 * failure (a serialization fault, for instance).
 *
 * Isolated into one expression so the single point of coupling to the gitlive SDK's
 * exception shape is easy to find and adjust.
 */
private fun callableErrorCode(error: Throwable): String? =
    (error as? FirebaseFunctionsException)?.code?.name
