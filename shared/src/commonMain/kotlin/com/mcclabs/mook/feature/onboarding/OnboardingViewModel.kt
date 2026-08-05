package com.mcclabs.mook.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.model.ProficiencyLevel
import com.mcclabs.mook.domain.repository.LanguageRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val languageRepository: LanguageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<OnboardingNavigationEvent>()
    val navigationEvent: SharedFlow<OnboardingNavigationEvent> = _navigationEvent.asSharedFlow()

    sealed class OnboardingNavigationEvent {
        data object NavigateToLogin : OnboardingNavigationEvent()
    }

    init {
        loadLanguages()
    }

    private fun loadLanguages() {
        val languages = languageRepository.getAvailableLanguages()
        _uiState.update { it.copy(availableLanguages = languages) }
    }

    fun selectNativeLanguage(language: Language) {
        _uiState.update {
            it.copy(
                selectedNativeLanguage = language,
                canContinue = canContinue(language, it.selectedTargetLanguage)
            )
        }
    }

    fun selectTargetLanguage(language: Language) {
        _uiState.update {
            it.copy(
                selectedTargetLanguage = language,
                canContinue = canContinue(it.selectedNativeLanguage, language)
            )
        }
    }

    fun setProficiencyLevel(level: ProficiencyLevel) {
        _uiState.update { it.copy(proficiencyLevel = level) }
    }

    fun onContinue() {
        viewModelScope.launch {
            _navigationEvent.emit(OnboardingNavigationEvent.NavigateToLogin)
        }
    }

    private fun canContinue(native: Language?, target: Language?): Boolean {
        return native != null && target != null && native.code != target.code
    }
}
