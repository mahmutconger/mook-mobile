package com.mcclabs.mook.feature.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.model.AuthResult
import com.mcclabs.mook.domain.model.Country
import com.mcclabs.mook.domain.model.Gender
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.repository.AuthRepository
import com.mcclabs.mook.domain.repository.RemoteConfigRepository
import com.mcclabs.mook.domain.validation.InputValidator
import com.mcclabs.mook.util.getAvailableCountries
import com.mcclabs.mook.util.getCurrentRegionCode
import com.mcclabs.mook.util.isOfMinimumAge
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.reg_error_select_birth
import mook.shared.generated.resources.reg_error_select_country
import mook.shared.generated.resources.reg_error_select_gender
import mook.shared.generated.resources.reg_error_confirm_password
import mook.shared.generated.resources.reg_error_passwords_mismatch
import mook.shared.generated.resources.reg_error_avatar_required
import mook.shared.generated.resources.reg_error_discovery_photo_required
import mook.shared.generated.resources.legal_terms_error
import mook.shared.generated.resources.legal_eula_error

class RegistrationViewModel(
    private val authRepository: AuthRepository,
    // Gereksinim 2.13 (Faz 4): onboarding tamamlandığında "show_onboarding_trial_offer"
    // Remote Config bayrağını okumak için — bkz. completeProfile().
    private val remoteConfigRepository: RemoteConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<RegistrationNavigationEvent>()
    val navigationEvent: SharedFlow<RegistrationNavigationEvent> = _navigationEvent.asSharedFlow()

    sealed class RegistrationNavigationEvent {
        data object NavigateToHome : RegistrationNavigationEvent()

        /**
         * Gereksinim 2.13 (Faz 4): "show_onboarding_trial_offer" Remote Config bayrağı
         * açıkken profil onboarding'i bittiği anda ateşlenir — normal ana ekran akışı
         * yerine kullanıcı doğrudan, Deneme paketi ön-seçili olarak Paywall'a gider.
         */
        data object NavigateToPaywallWithTrial : RegistrationNavigationEvent()
    }

    init {
        loadCountries()
    }

    /**
     * Populates the country dropdown from the device's ISO regions and
     * pre-selects the device's current region (falls back to no selection).
     */
    private fun loadCountries() {
        val countries = getAvailableCountries()
        val currentCode = getCurrentRegionCode()
        val defaultCountry = currentCode?.let { code ->
            countries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
        _uiState.update {
            it.copy(
                availableCountries = countries,
                selectedCountry = it.selectedCountry ?: defaultCountry
            )
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update {
            it.copy(email = email, emailError = null, generalError = null)
        }
    }

    fun onPasswordChange(password: String) {
        _uiState.update {
            it.copy(password = password, passwordError = null, generalError = null)
        }
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update {
            it.copy(confirmPassword = confirmPassword, confirmPasswordError = null, generalError = null)
        }
    }

    fun onDisplayNameChange(name: String) {
        _uiState.update {
            it.copy(displayName = name, displayNameError = null, generalError = null)
        }
    }

    fun onBioChange(bio: String) {
        _uiState.update {
            it.copy(bio = bio, bioError = null, generalError = null)
        }
    }

    fun onBirthDateChange(millis: Long?) {
        _uiState.update {
            it.copy(birthDateMillis = millis, birthDateError = null, generalError = null)
        }
    }

    fun onGenderChange(gender: Gender) {
        _uiState.update {
            it.copy(gender = gender, genderError = null, generalError = null)
        }
    }

    fun onLanguageChange(language: Language) {
        _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun onCountryChange(country: Country) {
        _uiState.update { it.copy(selectedCountry = country, countryError = null) }
    }

    /**
     * Advances to the next wizard step if the current one validates. Step 4 registers
     * via [completeProfile] instead, so this only handles steps 1, 2, and 3.
     */
    fun onNextStep() {
        when (_uiState.value.currentStep) {
            1 -> if (validateStep1()) _uiState.update { it.copy(currentStep = 2) }
            2 -> if (validateStep2()) _uiState.update { it.copy(currentStep = 3) }
            3 -> if (validateStep3()) _uiState.update { it.copy(currentStep = 4) }
        }
    }

    /** Goes back one step. At step 1 the screen exits to Login instead. */
    fun onPreviousStep() {
        _uiState.update { if (it.currentStep > 1) it.copy(currentStep = it.currentStep - 1) else it }
    }

    /** Step 1 — Birth date (Neutral Age Gate). */
    private fun validateStep1(): Boolean {
        val millis = _uiState.value.birthDateMillis
        if (millis == null) {
            viewModelScope.launch {
                val msg = getString(Res.string.reg_error_select_birth)
                _uiState.update { it.copy(birthDateError = msg) }
            }
            return false
        }

        if (!isOfMinimumAge(millis)) {
            _uiState.update { it.copy(isAgeBlocked = true) }
            return false
        }

        return true
    }

    /** Step 2 — language (always defaulted) and country. */
    private fun validateStep2(): Boolean {
        if (_uiState.value.selectedCountry == null) {
            viewModelScope.launch {
                val msg = getString(Res.string.reg_error_select_country)
                _uiState.update { it.copy(countryError = msg) }
            }
            return false
        }
        return true
    }

    /** Step 3 — full name and gender. */
    private fun validateStep3(): Boolean {
        val state = _uiState.value
        val nameValidation = InputValidator.validateDisplayName(state.displayName)
        val genderMissing = state.gender == null

        if (!nameValidation.isValid || genderMissing) {
            viewModelScope.launch {
                val genderMsg = if (genderMissing) getString(Res.string.reg_error_select_gender) else null
                _uiState.update {
                    it.copy(
                        displayNameError = nameValidation.errorMessage,
                        genderError = genderMsg
                    )
                }
            }
            return false
        }
        return true
    }

    fun onDiscoverVisibleChange(visible: Boolean) {
        _uiState.update { it.copy(discoverVisible = visible) }
    }

    fun onTermsAcceptedChange(accepted: Boolean) {
        _uiState.update { it.copy(termsAccepted = accepted, termsError = null, generalError = null) }
    }

    fun onEulaToggle(accepted: Boolean) {
        _uiState.update { it.copy(acceptedEula = accepted, generalError = null) }
    }

    fun toggleInterest(interest: String) {
        _uiState.update { state ->
            val updatedInterests = if (interest in state.selectedInterests) {
                state.selectedInterests - interest
            } else {
                state.selectedInterests + interest
            }
            state.copy(selectedInterests = updatedInterests)
        }
    }

    fun onAvatarSelected(uri: String) {
        _uiState.update { it.copy(avatarUri = uri, generalError = null) }
    }

    fun onDiscoveryPhotoSelected(uri: String) {
        _uiState.update { state ->
            state.copy(
                discoveryPhotos = state.discoveryPhotos + uri,
                generalError = null
            )
        }
    }

    fun completeProfile() {
        val currentState = _uiState.value

        // Neutral Age Gate (defense-in-depth): never create an account for a missing
        // or under-18 date of birth, even if the step-1 gate was somehow bypassed.
        val birthMillis = currentState.birthDateMillis
        if (birthMillis == null || !isOfMinimumAge(birthMillis)) {
            _uiState.update { it.copy(isAgeBlocked = true, isLoading = false) }
            return
        }

        val emailValidation = InputValidator.validateEmail(currentState.email)
        val passwordValidation = InputValidator.validatePassword(currentState.password)
        val nameValidation = InputValidator.validateDisplayName(currentState.displayName)
        val bioValidation = InputValidator.validateBio(currentState.bio)

        // Confirm password must match.
        val confirmPasswordEmpty = currentState.confirmPassword.isEmpty()
        val confirmPasswordMismatch = currentState.confirmPassword != currentState.password
        // Gender is required.
        val genderMissing = currentState.gender == null
        // Terms of Use acceptance is required.
        val termsMissing = !currentState.termsAccepted

        if (!emailValidation.isValid ||
            !passwordValidation.isValid ||
            !nameValidation.isValid ||
            !bioValidation.isValid ||
            confirmPasswordEmpty || confirmPasswordMismatch ||
            genderMissing ||
            termsMissing ||
            !currentState.acceptedEula
        ) {
            viewModelScope.launch {
                val confirmMsg = when {
                    confirmPasswordEmpty -> getString(Res.string.reg_error_confirm_password)
                    confirmPasswordMismatch -> getString(Res.string.reg_error_passwords_mismatch)
                    else -> null
                }
                val genderMsg = if (genderMissing) getString(Res.string.reg_error_select_gender) else null
                val termsMsg = if (termsMissing) getString(Res.string.legal_terms_error) else null
                val generalMsg = if (!currentState.acceptedEula) getString(Res.string.legal_eula_error) else null
                _uiState.update {
                    it.copy(
                        emailError = emailValidation.errorMessage,
                        passwordError = passwordValidation.errorMessage,
                        confirmPasswordError = confirmMsg,
                        displayNameError = nameValidation.errorMessage,
                        bioError = bioValidation.errorMessage,
                        genderError = genderMsg,
                        termsError = termsMsg,
                        generalError = generalMsg
                    )
                }
            }
            return
        }

        if (currentState.avatarUri == null) {
            viewModelScope.launch {
                val msg = getString(Res.string.reg_error_avatar_required)
                _uiState.update { it.copy(generalError = msg) }
            }
            return
        }

        if (currentState.discoveryPhotos.isEmpty()) {
            viewModelScope.launch {
                val msg = getString(Res.string.reg_error_discovery_photo_required)
                _uiState.update { it.copy(generalError = msg) }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalError = null) }

            // 1. Register the user
            val registerResult = authRepository.register(
                email = currentState.email,
                password = currentState.password,
                displayName = currentState.displayName
            )

            when (registerResult) {
                is AuthResult.Success -> {
                    val userId = registerResult.data.id

                    // 2. Complete their profile
                    val profileResult = authRepository.completeProfile(
                        userId = userId,
                        displayName = currentState.displayName,
                        bio = currentState.bio,
                        interests = currentState.selectedInterests.toList(),
                        avatarUri = currentState.avatarUri,
                        discoveryPhotos = currentState.discoveryPhotos,
                        gender = currentState.gender,
                        birthDateMillis = currentState.birthDateMillis,
                        languageCode = currentState.selectedLanguage.code,
                        countryCode = currentState.selectedCountry?.code,
                        discoverVisible = currentState.discoverVisible
                    )

                    when (profileResult) {
                        is AuthResult.Success -> {
                            _uiState.update { it.copy(isLoading = false) }
                            // Gereksinim 2.13 (Faz 4): Remote Config bayrağı açıksa profil
                            // tamamlanır tamamlanmaz doğrudan Deneme paketi ön-seçili Paywall'a
                            // yönlendir; kapalıysa (ya da okunamazsa — güvenli varsayılan)
                            // normal akış (ana ekran) değişmeden devam eder.
                            val event = if (remoteConfigRepository.shouldShowOnboardingTrialOffer()) {
                                RegistrationNavigationEvent.NavigateToPaywallWithTrial
                            } else {
                                RegistrationNavigationEvent.NavigateToHome
                            }
                            _navigationEvent.emit(event)
                        }
                        is AuthResult.Error -> {
                            _uiState.update {
                                it.copy(isLoading = false, generalError = profileResult.message)
                            }
                        }
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, generalError = registerResult.message)
                    }
                }
            }
        }
    }
}
