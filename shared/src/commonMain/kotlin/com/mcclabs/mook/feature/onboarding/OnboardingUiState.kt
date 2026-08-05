package com.mcclabs.mook.feature.onboarding

import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.model.ProficiencyLevel

data class OnboardingUiState(
    val availableLanguages: List<Language> = emptyList(),
    val selectedNativeLanguage: Language? = null,
    val selectedTargetLanguage: Language? = null,
    val proficiencyLevel: ProficiencyLevel = ProficiencyLevel.BEGINNER,
    val currentStep: Int = 0,
    val totalSteps: Int = 3,
    val isLoading: Boolean = false,
    val canContinue: Boolean = false
)
