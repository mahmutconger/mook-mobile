package com.mcclabs.mook.feature.eula

import com.mcclabs.mook.domain.auth.LogoutUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.repository.AuthRepository
import kotlinx.coroutines.delay
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
    /** Kullanıcı metni sonuna kadar kaydırdı mı (metin ekrana sığıyorsa hemen `true`). */
    val hasReadToEnd: Boolean = false,
    /** Onay kutusu açılmadan önce kalan en kısa okuma süresi (saniye). */
    val secondsLeft: Int = EulaGateViewModel.MIN_READ_SECONDS,
) {
    /** Okuma zorunluluğu: sonuna kadar kaydırılmadan ve süre dolmadan kabul edilemez. */
    val canAccept: Boolean get() = hasReadToEnd && secondsLeft == 0
}

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
                startReadingTimer()
            }
        }
    }

    /** Metin göründükten sonra en kısa okuma süresini geri sayar. */
    private fun startReadingTimer() {
        viewModelScope.launch {
            while (_state.value.secondsLeft > 0) {
                delay(1_000)
                _state.update { it.copy(secondsLeft = (it.secondsLeft - 1).coerceAtLeast(0)) }
            }
        }
    }

    /** Ekran, metnin sonuna ulaşıldığını bildirir. */
    fun onReadToEnd() {
        _state.update { it.copy(hasReadToEnd = true) }
    }

    fun onAcceptedChange(accepted: Boolean) {
        // Okunmadan işaretlenemez (kaldırmak her zaman serbesttir).
        if (accepted && !_state.value.canAccept) return
        _state.update { it.copy(accepted = accepted) }
    }

    fun onContinue() {
        val state = _state.value
        if (!state.accepted || !state.canAccept || state.isSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            authRepository.acceptEula()
            _events.emit(EulaGateEvent.Proceed)
        }
    }

    companion object {
        /** Metin kısa olsa bile ekranda kalması gereken en kısa okuma süresi. */
        const val MIN_READ_SECONDS: Int = 8
    }

    fun logout() {
        viewModelScope.launch {
            // Katı çıkış sırası: FCM jetonu → RevenueCat → signOut (bkz. LogoutUseCase).
            logoutUseCase()
            _events.emit(EulaGateEvent.LoggedOut)
        }
    }
}
