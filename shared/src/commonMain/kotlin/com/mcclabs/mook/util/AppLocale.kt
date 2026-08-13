package com.mcclabs.mook.util

/**
 * Applies the in-app UI language identified by [languageTag] (e.g. "en", "tr").
 *
 * Compose Multiplatform resolves `stringResource` from the platform locale, so the
 * app language is applied by overriding that locale and recomposing the tree.
 *
 * - Android: takes effect immediately (paired with a `key(language)` recomposition).
 * - iOS: recorded for the next launch; iOS applies the language on relaunch.
 */
expect fun applyAppLocale(languageTag: String)
