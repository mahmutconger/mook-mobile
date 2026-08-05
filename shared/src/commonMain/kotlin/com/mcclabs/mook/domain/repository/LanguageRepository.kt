package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.Language

/**
 * Repository interface for accessing available language data.
 *
 * Provides the list of languages supported by the application for
 * native and target language selection during onboarding.
 */
interface LanguageRepository {

    /**
     * Returns the complete list of languages available for selection.
     *
     * @return A list of [Language] objects representing all supported languages.
     */
    fun getAvailableLanguages(): List<Language>
}
