package com.mcclabs.mook.data.repository

import com.mcclabs.mook.domain.model.Language
import com.mcclabs.mook.domain.repository.LanguageRepository

/**
 * In-memory implementation of [LanguageRepository].
 *
 * Provides a hardcoded list of 15 commonly spoken languages for use
 * during onboarding language selection. Replace with an API-backed
 * implementation if dynamic language lists are needed in the future.
 */
class LanguageRepositoryImpl : LanguageRepository {

    /**
     * Returns a hardcoded list of 15 supported languages.
     *
     * Each language includes its ISO 639-1 code, English display name,
     * and a flag emoji representing its primary country of origin.
     *
     * @return An immutable list of [Language] objects.
     */
    override fun getAvailableLanguages(): List<Language> = listOf(
        Language(code = "en", name = "English", flagEmoji = "🇬🇧"),
        Language(code = "es", name = "Spanish", flagEmoji = "🇪🇸"),
        Language(code = "fr", name = "French", flagEmoji = "🇫🇷"),
        Language(code = "de", name = "German", flagEmoji = "🇩🇪"),
        Language(code = "it", name = "Italian", flagEmoji = "🇮🇹"),
        Language(code = "pt", name = "Portuguese", flagEmoji = "🇵🇹"),
        Language(code = "ru", name = "Russian", flagEmoji = "🇷🇺"),
        Language(code = "ja", name = "Japanese", flagEmoji = "🇯🇵"),
        Language(code = "ko", name = "Korean", flagEmoji = "🇰🇷"),
        Language(code = "zh", name = "Chinese", flagEmoji = "🇨🇳"),
        Language(code = "ar", name = "Arabic", flagEmoji = "🇸🇦"),
        Language(code = "tr", name = "Turkish", flagEmoji = "🇹🇷"),
        Language(code = "hi", name = "Hindi", flagEmoji = "🇮🇳"),
        Language(code = "nl", name = "Dutch", flagEmoji = "🇳🇱"),
        Language(code = "sv", name = "Swedish", flagEmoji = "🇸🇪")
    )
}
