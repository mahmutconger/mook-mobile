package com.mcclabs.mook.feature.settings

import com.mcclabs.mook.util.getCurrentTimeMillis
import mook.shared.generated.resources.settings_incognito_error
import com.mcclabs.mook.domain.repository.IncognitoUpdateResult
import com.mcclabs.mook.domain.auth.LogoutUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.account.AccountDeletionResult
import com.mcclabs.mook.domain.account.DeleteAccountUseCase
import com.mcclabs.mook.domain.billing.PurchaseOutcome
import com.mcclabs.mook.domain.billing.PriceChangeCheckResult
import com.mcclabs.mook.domain.billing.PriceChangeConfirmationUseCase
import com.mcclabs.mook.domain.privacy.DataExportRequestResult
import com.mcclabs.mook.domain.privacy.ExportUserDataUseCase
import com.mcclabs.mook.domain.billing.RestoreSubscriptionUseCase
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.feature.billing.toLocalizedMessage
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
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.paywall_restore_pending
import mook.shared.generated.resources.paywall_restore_success
import org.jetbrains.compose.resources.getString

sealed class SettingsEvent {
    object NavigateToLogin : SettingsEvent()
    object AccountDeleted : SettingsEvent()
    /** Gizli mod Premium ayrıcalığıdır; Premium olmayan kullanıcı Paywall'a yönlendirilir. */
    object NavigateToPaywall : SettingsEvent()
}

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val subscriptions: SubscriptionRepository,
    private val restoreSubscriptionUseCase: RestoreSubscriptionUseCase,
    private val priceChangeConfirmationUseCase: PriceChangeConfirmationUseCase,
    private val exportUserDataUseCase: ExportUserDataUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        load()
        viewModelScope.launch {
            subscriptions.state.collect { entitlement ->
                _state.update {
                    it.copy(
                        subscription = entitlement,
                        subscriptionStatus = SubscriptionStatusPresenter.present(entitlement, getCurrentTimeMillis()),
                    )
                }
            }
        }
        viewModelScope.launch {
            subscriptions.refresh()
            // Gereksinim 2.3/2.4: taze bir `refresh()`'TEN SONRA kontrol edilir ki
            // `activeProductIdentifier` (yalnızca canlı `CustomerInfo`'dan gelir)
            // henüz boşken yanlışlıkla "karşılaştırılamadı" sonucuna düşülmesin.
            checkPriceChange()
        }
    }

    /**
     * Gereksinim 2.3: bu cihazda en son satın alındığından bu yana fiyat ARTIŞI olup
     * olmadığını kontrol eder. Yalnızca kesin bir artış ([PriceChangeCheckResult.Increased])
     * durumu UI'ya yansıtılır — bkz. `SettingsUiState.priceChangeNotice` KDoc'u.
     */
    private suspend fun checkPriceChange() {
        val result = priceChangeConfirmationUseCase()
        _state.update { it.copy(priceChangeNotice = result as? PriceChangeCheckResult.Increased) }
    }

    private fun load() {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            val visible = settingsRepository.getDiscoverVisible()
            val incognito = settingsRepository.getIncognito()
            val isDark = settingsRepository.getIsDarkMode()
            val lang = settingsRepository.getAppLanguage()
            _state.update {
                it.copy(
                    email = authRepository.currentUserEmail().orEmpty(),
                    discoverVisible = visible,
                    incognitoEnabled = incognito,
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

    /**
     * Gizli mod anahtarı. Premium değilse anahtar değişmez ve kullanıcı Paywall'a yönlendirilir;
     * Premium'da iyimser güncelleme yapılır ve sunucu reddederse eski değere dönülür.
     */
    fun onIncognitoChange(enabled: Boolean) {
        val current = _state.value
        if (current.isIncognitoUpdating) return
        if (enabled && !current.subscription.limits.incognito) {
            viewModelScope.launch { _events.emit(SettingsEvent.NavigateToPaywall) }
            return
        }
        _state.update { it.copy(incognitoEnabled = enabled, isIncognitoUpdating = true, incognitoMessage = null) }
        viewModelScope.launch {
            when (val result = settingsRepository.setIncognito(enabled)) {
                is IncognitoUpdateResult.Updated ->
                    _state.update { it.copy(incognitoEnabled = result.enabled, isIncognitoUpdating = false) }
                IncognitoUpdateResult.UpgradeRequired -> {
                    _state.update { it.copy(incognitoEnabled = false, isIncognitoUpdating = false) }
                    _events.emit(SettingsEvent.NavigateToPaywall)
                }
                IncognitoUpdateResult.Failed -> _state.update {
                    it.copy(
                        incognitoEnabled = !enabled,
                        isIncognitoUpdating = false,
                        incognitoMessage = getString(Res.string.settings_incognito_error),
                    )
                }
            }
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
        // Gereksinim 2.1: restore mantığı artık RestoreSubscriptionUseCase'de tek bir yerde.
        val message = when (val outcome = restoreSubscriptionUseCase()) {
            PurchaseOutcome.Success -> getString(Res.string.paywall_restore_success)
            PurchaseOutcome.Cancelled -> null
            PurchaseOutcome.Pending -> getString(Res.string.paywall_restore_pending)
            // Geri yükleme bir plan değişikliği planlamaz; tamlık için başarı gibi ele alınır.
            is PurchaseOutcome.ChangeScheduled -> getString(Res.string.paywall_restore_success)
            is PurchaseOutcome.Error -> outcome.error.toLocalizedMessage()
        }
        _state.update { it.copy(isRestoringPurchases = false, restorePurchasesMessage = message) }
    }

    fun logout() {
        viewModelScope.launch {
            // Katı çıkış sırası: FCM jetonu → RevenueCat → signOut (bkz. LogoutUseCase).
            logoutUseCase()
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
            applyDeletionResult(deleteAccountUseCase())
        }
    }

    /**
     * Gereksinim 1.8: kullanıcı [DeleteAccountUiState.ActiveSubscriptionWarning] uyarısını
     * gördükten sonra yine de silmeye devam etmeyi onayladı — kontrol bilerek atlanır.
     */
    fun onDeleteConfirmDespiteActiveSubscription() {
        if (_state.value.deleteAccount is DeleteAccountUiState.Loading) return
        _state.update { it.copy(deleteAccount = DeleteAccountUiState.Loading) }
        viewModelScope.launch {
            applyDeletionResult(deleteAccountUseCase(acknowledgedActiveSubscription = true))
        }
    }

    /**
     * Gereksinim 5 (Faz 6, KVKK Madde 11): kullanıcının tüm verisinin bir JSON dosyasına
     * derlenip e-posta ile bir indirme bağlantısının gönderilmesini TETİKLER. Silme
     * akışının aksine (bkz. `onDeleteAccountClick`) geri alınamaz bir eylem DEĞİLDİR --
     * bu yüzden bir onay iletişim kutusu GEREKMEZ.
     */
    fun onExportDataClick() {
        if (_state.value.exportData is ExportDataUiState.Loading) return
        _state.update { it.copy(exportData = ExportDataUiState.Loading) }
        viewModelScope.launch {
            when (val result = exportUserDataUseCase()) {
                is DataExportRequestResult.Requested -> {
                    _state.update { it.copy(exportData = ExportDataUiState.Requested) }
                }
                is DataExportRequestResult.Failed -> {
                    _state.update { it.copy(exportData = ExportDataUiState.Error(result.message)) }
                }
            }
        }
    }

    private suspend fun applyDeletionResult(result: AccountDeletionResult) {
        when (result) {
            is AccountDeletionResult.Deleted -> {
                _state.update { it.copy(deleteAccount = DeleteAccountUiState.Success) }
                _events.emit(SettingsEvent.AccountDeleted)
            }
            is AccountDeletionResult.Failed -> {
                _state.update { it.copy(deleteAccount = DeleteAccountUiState.Error(result.message)) }
            }
            is AccountDeletionResult.ActiveSubscriptionWarning -> {
                _state.update {
                    it.copy(
                        deleteAccount = DeleteAccountUiState.ActiveSubscriptionWarning(
                            tier = result.tier,
                            expiresAtMillis = result.expiresAtMillis,
                        ),
                    )
                }
            }
        }
    }
}
