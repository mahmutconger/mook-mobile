package com.mcclabs.mook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.AuthRepository
import com.mcclabs.mook.domain.repository.SettingsRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class SettingsEvent {
    object NavigateToLogin : SettingsEvent()
    object AccountDeleted : SettingsEvent()
}

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            val visible = settingsRepository.getDiscoverVisible()
            val isDark = settingsRepository.getIsDarkMode()
            _state.update {
                it.copy(
                    email = Firebase.auth.currentUser?.email.orEmpty(),
                    discoverVisible = visible,
                    isDarkMode = isDark,
                    ageRangeStart = settings.ageRangeStart,
                    ageRangeEnd = settings.ageRangeEnd,
                    isLoading = false
                )
            }
        }
    }

    fun onDiscoverVisibleChange(visible: Boolean) {
        // Reflect the toggle immediately, then persist; UI shouldn't wait on the round trip.
        _state.update { it.copy(discoverVisible = visible) }
        viewModelScope.launch {
            settingsRepository.setDiscoverVisible(visible)
        }
    }

    fun onDarkModeChange(isDark: Boolean) {
        _state.update { it.copy(isDarkMode = isDark) }
        viewModelScope.launch {
            settingsRepository.setIsDarkMode(isDark)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _events.emit(SettingsEvent.NavigateToLogin)
        }
    }

    /** Shows the confirmation dialog before permanently deleting the account. */
    fun onDeleteAccountClick() {
        _state.update { it.copy(showDeleteConfirmDialog = true) }
    }

    /** User dismissed the delete confirmation dialog without confirming. */
    fun onDeleteDismiss() {
        _state.update { it.copy(showDeleteConfirmDialog = false) }
    }

    /**
     * Permanently deletes the account after the user confirms.
     *
     * On success emits [SettingsEvent.AccountDeleted] so the UI navigates to
     * Login and clears the back stack.
     */
    fun onDeleteConfirm() {
        _state.update { it.copy(showDeleteConfirmDialog = false, isLoading = true) }
        viewModelScope.launch {
            val result = authRepository.deleteAccount()
            when (result) {
                is com.mcclabs.mook.domain.model.AuthResult.Success -> {
                    _events.emit(SettingsEvent.AccountDeleted)
                }
                is com.mcclabs.mook.domain.model.AuthResult.Error -> {
                    _state.update { it.copy(isLoading = false, deleteError = result.message) }
                }
            }
        }
    }
}
