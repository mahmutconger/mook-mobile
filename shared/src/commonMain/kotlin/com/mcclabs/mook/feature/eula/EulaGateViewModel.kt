package com.mcclabs.mook.feature.eula

import com.mcclabs.mook.domain.auth.LogoutUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EulaGateUiState(
    /** True while checking the stored acceptance; keeps the EULA hidden to avoid a flash. */
    val isChecking: Boolean = true,
    val accepted: Boolean = false,
    val isSubmitting: Boolean = false,
)

sealed class EulaGateEvent {
    /** The user has accepted (now or previously); continue into the app. */
    data object Proceed : EulaGateEvent()

    /** The user declined and signed out; return to Login. */
    data object LoggedOut : EulaGateEvent()
}

/**
 * Gates entry to the app on EULA acceptance. If the signed-in user already accepted,
 * it forwards immediately; otherwise it shows the mandatory acceptance UI.
 */
class EulaGateViewModel(
    private val authRepository: AuthRepository,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(EulaGateUiState())
    val state: StateFlow<EulaGateUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EulaGateEvent>()
    val events: SharedFlow<EulaGateEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            if (authRepository.hasAcceptedEula()) {
                _events.emit(EulaGateEvent.Proceed)
            } else {
                _state.update { it.copy(isChecking = false) }
            }
        }
    }

    fun onAcceptedChange(accepted: Boolean) {
        _state.update { it.copy(accepted = accepted) }
    }

    fun onContinue() {
        if (!_state.value.accepted || _state.value.isSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            authRepository.acceptEula()
            _events.emit(EulaGateEvent.Proceed)
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Katı çıkış sırası: FCM jetonu → RevenueCat → signOut (bkz. LogoutUseCase).
            logoutUseCase()
            _events.emit(EulaGateEvent.LoggedOut)
        }
    }
}
