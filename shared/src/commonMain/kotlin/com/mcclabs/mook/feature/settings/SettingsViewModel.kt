package com.mcclabs.mook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.account.DeleteAccountUseCase
import com.mcclabs.mook.domain.billing.PurchaseOutcome
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.model.AuthResult
import com.mcclabs.mook.domain.repository.AuthRepository
import com.mcclabs.mook.domain.repository.SettingsRepository
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
    private val settingsRepository: SettingsRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val subscriptions: SubscriptionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        load()
        viewModelScope.launch {
            subscriptions.state.collect { entitlement ->
                _state.update { it.copy(subscription = entitlement) }
            }
        }
        viewModelScope.launch { subscriptions.refresh() }
    }

    private fun load() {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            val visible = settingsRepository.getDiscoverVisible()
            val isDark = settingsRepository.getIsDarkMode()
            val lang = settingsRepository.getAppLanguage()
            _state.update {
                it.copy(
                    email = authRepository.currentUserEmail().orEmpty(),
                    discoverVisible = visible,
                    isDarkMode = isDark,
                    appLanguage = lang,
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

    fun onThemeChange(isDark: Boolean) {
        _state.update { it.copy(isDarkMode = isDark) }
        viewModelScope.launch {
            settingsRepository.setIsDarkMode(isDark)
        }
    }

    fun onLanguageClick() {
        _state.update { it.copy(showLanguageDialog = true) }
    }

    fun onLanguageDismiss() {
        _state.update { it.copy(showLanguageDialog = false) }
    }

    fun onLanguageSelected(language: String) {
        _state.update { it.copy(appLanguage = language, showLanguageDialog = false) }
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
            // Note: Dynamic UI locale change implementation depends on platform / resources logic
        }
    }

    fun onDarkModeChange(isDark: Boolean) {
        _state.update { it.copy(isDarkMode = isDark) }
        viewModelScope.launch {
            settingsRepository.setIsDarkMode(isDark)
        }
    }

    fun restorePurchases() = viewModelScope.launch {
        if (_state.value.isRestoringPurchases) return@launch
        _state.update { it.copy(isRestoringPurchases = true, restorePurchasesMessage = null) }
        val message = when (val outcome = subscriptions.restore()) {
            PurchaseOutcome.Success -> "Purchases restored."
            PurchaseOutcome.Cancelled -> null
            PurchaseOutcome.Pending -> "Your restoration is pending."
            is PurchaseOutcome.Error -> outcome.message
        }
        _state.update { it.copy(isRestoringPurchases = false, restorePurchasesMessage = message) }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _events.emit(SettingsEvent.NavigateToLogin)
        }
    }

    /** Kalıcı silmeden önce onay iletişim kutusunu gösterir. */
    fun onDeleteAccountClick() {
        _state.update { it.copy(showDeleteConfirmDialog = true) }
    }

    /** Kullanıcı onaylamadan iletişim kutusunu kapattı; hata durumu da sıfırlanır. */
    fun onDeleteDismiss() {
        _state.update {
            it.copy(showDeleteConfirmDialog = false, deleteAccount = DeleteAccountUiState.Idle)
        }
    }

    /**
     * Kullanıcı onayladıktan sonra hesabı kalıcı olarak siler.
     *
     * Durum akışı: Loading → (Success | Error). Başarıda [SettingsEvent.AccountDeleted]
     * yayınlanır ki UI Login'e gitsin ve geri yığınını temizlesin.
     */
    fun onDeleteConfirm() {
        // Silme zaten sürüyorsa çift tetiklemeyi yok say.
        if (_state.value.deleteAccount is DeleteAccountUiState.Loading) return

        // İletişim kutusu açık kalır ki ilerleme (spinner) ve olası hata orada gösterilebilsin.
        _state.update { it.copy(deleteAccount = DeleteAccountUiState.Loading) }
        viewModelScope.launch {
            when (val result = deleteAccountUseCase()) {
                is AuthResult.Success -> {
                    _state.update { it.copy(deleteAccount = DeleteAccountUiState.Success) }
                    _events.emit(SettingsEvent.AccountDeleted)
                }
                is AuthResult.Error -> {
                    _state.update {
                        it.copy(deleteAccount = DeleteAccountUiState.Error(result.message))
                    }
                }
            }
        }
    }
}
