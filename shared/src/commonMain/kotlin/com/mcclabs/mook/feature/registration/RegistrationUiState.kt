package com.mcclabs.mook.feature.registration

import com.mcclabs.mook.domain.model.Country
import com.mcclabs.mook.domain.model.Gender
import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.model.Languages

data class RegistrationUiState(
    // Current wizard step, 1..3.
    val currentStep: Int = 1,
    val displayName: String = "",
    val bio: String = "",
    val avatarUri: String? = null,
    val discoveryPhotos: List<String> = emptyList(),
    val selectedInterests: Set<String> = emptySet(),
    val availableInterests: List<String> = listOf(
        "Travel", "Music", "Tech", "Cooking", "Art", "Fitness"
    ),
    val isLoading: Boolean = false,
    
    // Safety requirement for Google Play (EULA)
    val acceptedEula: Boolean = false,
    val isAgeBlocked: Boolean = false,

    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    // Date of birth as epoch milliseconds (from the calendar DatePicker).
    val birthDateMillis: Long? = null,
    val gender: Gender? = null,
    // Language dropdown — defaults to American English (EN-US).
    val availableLanguages: List<Language> = Languages.ALL,
    val selectedLanguage: Language = Languages.DEFAULT,
    // Country dropdown — populated dynamically from the device's ISO regions.
    val availableCountries: List<Country> = emptyList(),
    val selectedCountry: Country? = null,
    // "Discover'da görünür ol" toggle — on by default.
    val discoverVisible: Boolean = true,
    // Terms of Use acceptance — required to register.
    val termsAccepted: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val displayNameError: String? = null,
    val bioError: String? = null,
    val genderError: String? = null,
    val birthDateError: String? = null,
    val countryError: String? = null,
    val termsError: String? = null,
    val generalError: String? = null
)
