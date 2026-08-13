package com.mcclabs.mook.util

import java.util.Locale

/**
 * Overrides the JVM default locale, which Compose's `Locale.current` (and therefore
 * `stringResource`) reads on the next recomposition.
 */
actual fun applyAppLocale(languageTag: String) {
    val locale = Locale.forLanguageTag(languageTag)
    Locale.setDefault(locale)
}
