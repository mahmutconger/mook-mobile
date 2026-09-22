package com.mcclabs.mook.feature.walktalkdemo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.data.sso.WALKTALK_SSO_CLIENT_ID
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.sso.GenerateAuthStateUseCase
import com.mcclabs.mook.domain.translation.Translator
import com.mcclabs.mook.util.buildWalkTalkSsoEntryUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Lifecycle host for [LiveTranslationEngine].
 *
 * Holds no logic of its own beyond ownership: the engine gets `viewModelScope`, so
 * every in-flight translation is cancelled when the screen goes away, and the whole
 * behaviour stays testable without a `ViewModel` (see `LiveTranslationEngineTest`).
 *
 * @param translator Injected by Koin — the Firebase-backed implementation in the app,
 *   a [com.mcclabs.mook.data.translation.FakeTranslator] in previews and tests.
 */
class WalkTalkDemoViewModel(
    translator: Translator,
    private val generateAuthState: GenerateAuthStateUseCase,
) : ViewModel() {

    private val engine = LiveTranslationEngine(
        translator = translator,
        scope = viewModelScope,
    )

    val state: StateFlow<ChatDemoUiState> = engine.state

    private val _openWalkTalk = Channel<String>(Channel.BUFFERED)

    /** CTA'ya her dokunuşta açılacak WalkTalk adresi; her seferinde yeni bir state taşır. */
    val openWalkTalk: Flow<String> = _openWalkTalk.receiveAsFlow()

    /**
     * Mook'un başlattığı SSO akışı için state üretir. State saklanamazsa kullanıcı yine de
     * WalkTalk'a gider: akış WalkTalk'un kendi başlattığı akış gibi devam eder ve güvenlik
     * beyaz liste ile onay ekranında korunur. Bir depolama hatası dönüşümü engellememeli.
     */
    fun onOpenWalkTalkClicked() {
        viewModelScope.launch {
            val state = try {
                generateAuthState(WALKTALK_SSO_CLIENT_ID)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            _openWalkTalk.send(buildWalkTalkSsoEntryUrl(mookState = state))
        }
    }

    fun onComposerTextChange(text: String) = engine.onComposerTextChange(text)

    fun onActiveSideChange(side: ChatSide) = engine.onActiveSideChange(side)

    fun onLanguageChange(side: ChatSide, language: Language) = engine.onLanguageChange(side, language)

    fun onSwapLanguages() = engine.onSwapLanguages()

    fun onSend() = engine.onSend()

    fun onRetryMessage(messageId: Long) = engine.onRetryMessage(messageId)

    fun onRetryPreview() = engine.onRetryPreview()
}
