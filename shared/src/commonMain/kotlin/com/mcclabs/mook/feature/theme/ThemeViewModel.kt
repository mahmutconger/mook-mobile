package com.mcclabs.mook.feature.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.ThemeRepository
import com.mcclabs.mook.domain.theme.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ThemeUiState(
    val preference: ThemePreference = ThemePreference.System,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/** Kullanıcı olayları depoya gider; bütün ekranlar deponun aynı akışını gözlemler. */
class ThemeViewModel(private val repository: ThemeRepository) : ViewModel() {
    private val state = MutableStateFlow(
        ThemeUiState(repository.preference.value ?: ThemePreference.System, repository.preference.value == null)
    )
    val uiState = state.asStateFlow()
    private var selectionRevision = 0L

    init {
        viewModelScope.launch {
            repository.preference.collect { preference ->
                if (preference != null) state.update { it.copy(preference = preference, isLoading = false) }
            }
        }
        viewModelScope.launch {
            try {
                repository.initialize()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(isLoading = false, errorMessage = "Tema tercihi okunamadı. Sistem teması kullanılıyor.") }
            }
        }
    }

    fun selectTheme(preference: ThemePreference) {
        val revision = ++selectionRevision
        state.update { it.copy(errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.setPreference(preference)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (revision == selectionRevision) {
                    state.update { it.copy(errorMessage = "Tema tercihi kaydedilemedi. Lütfen yeniden deneyin.") }
                }
            }
        }
    }
}
