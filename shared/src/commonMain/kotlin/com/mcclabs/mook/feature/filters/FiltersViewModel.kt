package com.mcclabs.mook.feature.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.model.MatchSettings
import com.mcclabs.mook.domain.repository.LanguageRepository
import com.mcclabs.mook.domain.repository.SettingsRepository
import com.mcclabs.mook.util.getAvailableCountries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FiltersViewModel(
    private val settingsRepository: SettingsRepository,
    private val languageRepository: LanguageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FiltersUiState(isLoading = true))
    val state: StateFlow<FiltersUiState> = _state.asStateFlow()

    init {
        // The full ISO country list (localized) and the language list are the
        // same sources registration uses, so filters stay in sync with sign-up.
        val countries = getAvailableCountries()
        val languages = languageRepository.getAvailableLanguages().map { it.name }
        _state.update { it.copy(availableCountries = countries, availableLanguages = languages) }

        viewModelScope.launch {
            try {
                val settings = settingsRepository.getSettings()
                _state.update { it.copy(settings = settings, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun updateAgeRange(start: Int, end: Int) {
        _state.update {
            it.copy(settings = it.settings.copy(ageRangeStart = start, ageRangeEnd = end))
        }
    }

    fun addCountry(country: String) {
        if (country == "Any") {
            _state.update { it.copy(settings = it.settings.copy(targetCountries = emptyList())) }
            return
        }
        val current = _state.value.settings.targetCountries
        if (!current.contains(country)) {
            _state.update { it.copy(settings = it.settings.copy(targetCountries = current + country)) }
        }
    }

    fun removeCountry(country: String) {
        val current = _state.value.settings.targetCountries
        _state.update { it.copy(settings = it.settings.copy(targetCountries = current - country)) }
    }

    fun addLanguage(language: String) {
        if (language == "Any") {
            _state.update { it.copy(settings = it.settings.copy(targetLanguages = emptyList())) }
            return
        }
        val current = _state.value.settings.targetLanguages
        if (!current.contains(language)) {
            _state.update { it.copy(settings = it.settings.copy(targetLanguages = current + language)) }
        }
    }

    fun removeLanguage(language: String) {
        val current = _state.value.settings.targetLanguages
        _state.update { it.copy(settings = it.settings.copy(targetLanguages = current - language)) }
    }

    fun onReset() {
        _state.update { it.copy(settings = MatchSettings()) }
    }

    fun onApply(navigateBack: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                settingsRepository.saveSettings(_state.value.settings)
                navigateBack()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
}
