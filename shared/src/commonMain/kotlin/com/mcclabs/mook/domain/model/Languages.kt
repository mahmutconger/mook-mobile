package com.mcclabs.mook.domain.model

/**
 * The fixed catalogue of 31 languages offered in the app.
 *
 * Codes match DeepL's translation language codes (in parity with the Android
 * build). Display names are shown in each language's own script; the default
 * selection is [DEFAULT] (American English, "EN-US").
 */
object Languages {

    /**
     * Sentinel room code for the "language-independent" room, where everyone is shown
     * regardless of native language. Not a real ISO code, so [fromCode] returns null and
     * the Discover query skips the language filter for it.
     */
    const val LANGUAGE_INDEPENDENT_ROOM_CODE: String = "ALL"

    val ALL: List<Language> = listOf(
        Language(code = "BG", name = "Български", flagEmoji = "🇧🇬"),
        Language(code = "CS", name = "Čeština", flagEmoji = "🇨🇿"),
        Language(code = "DA", name = "Dansk", flagEmoji = "🇩🇰"),
        Language(code = "DE", name = "Deutsch", flagEmoji = "🇩🇪"),
        Language(code = "ET", name = "Eesti", flagEmoji = "🇪🇪"),
        Language(code = "EL", name = "Ελληνικά", flagEmoji = "🇬🇷"),
        Language(code = "EN-US", name = "English (American)", flagEmoji = "🇺🇸"),
        Language(code = "EN-GB", name = "English (British)", flagEmoji = "🇬🇧"),
        Language(code = "ES", name = "Español", flagEmoji = "🇪🇸"),
        Language(code = "FI", name = "Suomi", flagEmoji = "🇫🇮"),
        Language(code = "FR", name = "Français", flagEmoji = "🇫🇷"),
        Language(code = "HU", name = "Magyar", flagEmoji = "🇭🇺"),
        Language(code = "ID", name = "Bahasa Indonesia", flagEmoji = "🇮🇩"),
        Language(code = "IT", name = "Italiano", flagEmoji = "🇮🇹"),
        Language(code = "JA", name = "日本語", flagEmoji = "🇯🇵"),
        Language(code = "KO", name = "한국어", flagEmoji = "🇰🇷"),
        Language(code = "LT", name = "Lietuvių", flagEmoji = "🇱🇹"),
        Language(code = "LV", name = "Latviešu", flagEmoji = "🇱🇻"),
        Language(code = "NB", name = "Norsk (Bokmål)", flagEmoji = "🇳🇴"),
        Language(code = "NL", name = "Nederlands", flagEmoji = "🇳🇱"),
        Language(code = "PL", name = "Polski", flagEmoji = "🇵🇱"),
        Language(code = "PT-BR", name = "Português (Brasil)", flagEmoji = "🇧🇷"),
        Language(code = "PT-PT", name = "Português (Portugal)", flagEmoji = "🇵🇹"),
        Language(code = "RO", name = "Română", flagEmoji = "🇷🇴"),
        Language(code = "RU", name = "Русский", flagEmoji = "🇷🇺"),
        Language(code = "SK", name = "Slovenčina", flagEmoji = "🇸🇰"),
        Language(code = "SL", name = "Slovenščina", flagEmoji = "🇸🇮"),
        Language(code = "SV", name = "Svenska", flagEmoji = "🇸🇪"),
        Language(code = "TR", name = "Türkçe", flagEmoji = "🇹🇷"),
        Language(code = "UK", name = "Українська", flagEmoji = "🇺🇦"),
        Language(code = "ZH", name = "中文", flagEmoji = "🇨🇳")
    )

    /** Default language selection: American English. */
    val DEFAULT: Language = ALL.first { it.code == "EN-US" }

    /** Resolves a stored language [code] back to its [Language], or `null` if unknown. */
    fun fromCode(code: String?): Language? {
        if (code.isNullOrBlank()) return null
        return ALL.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
