package com.mcclabs.mook.feature.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.consent.ConsentRepository
import com.mcclabs.mook.domain.consent.requiresDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Kullanıcının onay ekranındaki (henüz kaydedilmemiş) seçimleri. */
data class ConsentChoices(
    val crossBorderTransfer: Boolean = false,
    val personalizedAds: Boolean = false,
)

/**
 * KVKK onay ekranının tek durum kaynağı (UDF).
 *
 * @property requiresDecision Kullanıcı mevcut politika sürümü için henüz karar vermedi;
 *   uygulama kökü ekranı zorunlu olarak gösterir.
 * @property isEditorOpen Kullanıcı ekranı Ayarlar'dan isteğe bağlı olarak açtı.
 * @property choices Anahtarların anlık durumu. KVKK gereği açık rıza önceden işaretlenmiş
 *   kutularla alınamaz; ilk gösterimde ikisi de KAPALI başlar.
 */
data class PrivacyConsentUiState(
    val requiresDecision: Boolean = false,
    val isEditorOpen: Boolean = false,
    val choices: ConsentChoices = ConsentChoices(),
)

class PrivacyConsentViewModel(
    private val consentRepository: ConsentRepository,
) : ViewModel() {

    private val editorOpen = MutableStateFlow(false)
    private val choices = MutableStateFlow(ConsentChoices())

    val state: StateFlow<PrivacyConsentUiState> = combine(
        consentRepository.state,
        editorOpen,
        choices,
    ) { consent, open, current ->
        PrivacyConsentUiState(
            requiresDecision = consent.requiresDecision,
            isEditorOpen = open,
            choices = current,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PrivacyConsentUiState())

    /** Ayarlar'dan açılış: anahtarlar kullanıcının KAYITLI kararıyla doldurulur. */
    fun openEditor() {
        val saved = consentRepository.state.value
        choices.value = ConsentChoices(
            crossBorderTransfer = saved.crossBorderTransferAccepted,
            personalizedAds = saved.personalizedAdsAllowed,
        )
        editorOpen.value = true
    }

    fun onCrossBorderTransferToggled(enabled: Boolean) {
        choices.update { it.copy(crossBorderTransfer = enabled) }
    }

    fun onPersonalizedAdsToggled(enabled: Boolean) {
        choices.update { it.copy(personalizedAds = enabled) }
    }

    fun acceptAll() = save(ConsentChoices(crossBorderTransfer = true, personalizedAds = true))

    /** Reddetmek kabul etmek kadar kolay olmalıdır; uygulamanın temel işlevi etkilenmez. */
    fun rejectAll() = save(ConsentChoices(crossBorderTransfer = false, personalizedAds = false))

    fun saveChoices() = save(choices.value)

    /** Yalnızca isteğe bağlı (Ayarlar) modda kapatılabilir; zorunlu modda karar beklenir. */
    fun dismissEditor() {
        editorOpen.value = false
    }

    private fun save(decision: ConsentChoices) {
        consentRepository.recordDecision(
            crossBorderTransferAccepted = decision.crossBorderTransfer,
            personalizedAdsAllowed = decision.personalizedAds,
        )
        choices.value = decision
        editorOpen.value = false
    }
}
