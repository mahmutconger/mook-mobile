package com.mcclabs.mook.feature.walktalkdemo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.translation.Translator
import com.mcclabs.mook.util.buildWalkTalkSsoEntryUrl
import kotlinx.coroutines.flow.StateFlow

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
) : ViewModel() {

    private val engine = LiveTranslationEngine(
        translator = translator,
        scope = viewModelScope,
    )

    val state: StateFlow<ChatDemoUiState> = engine.state

    /**
     * The URL the CTA opens. Built once, carries no identity or secret — Mook's
     * existing SSO handshake mints the token after WalkTalk is open.
     */
    val walkTalkEntryUrl: String = buildWalkTalkSsoEntryUrl()

    fun onComposerTextChange(text: String) = engine.onComposerTextChange(text)

    fun onActiveSideChange(side: ChatSide) = engine.onActiveSideChange(side)

    fun onLanguageChange(side: ChatSide, language: Language) = engine.onLanguageChange(side, language)

    fun onSwapLanguages() = engine.onSwapLanguages()

    fun onSend() = engine.onSend()

    fun onRetryMessage(messageId: Long) = engine.onRetryMessage(messageId)

    fun onRetryPreview() = engine.onRetryPreview()
}
