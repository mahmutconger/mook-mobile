package com.mcclabs.mook

import androidx.compose.runtime.Composable
import coil3.EventListener
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.NullRequestDataException
import coil3.request.crossfade
import com.mcclabs.mook.di.appModule
import com.mcclabs.mook.navigation.AppNavGraph
import com.mcclabs.mook.ui.theme.MookTheme
import com.mcclabs.mook.util.Log
import org.koin.compose.KoinApplication

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.mcclabs.mook.domain.repository.SettingsRepository
import com.mcclabs.mook.util.applyAppLocale
import org.koin.compose.koinInject

@Composable
fun App(onDarkModeChange: (Boolean) -> Unit = {}) {
    // Coil only auto-registers a network fetcher on JVM, so profile photos served over
    // https would not load on iOS unless the fetcher is added explicitly here.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }

    KoinApplication(application = {
        modules(appModule)
    }) {
        val settingsRepository = koinInject<SettingsRepository>()
        val isDark by settingsRepository.observeIsDarkMode().collectAsState(initial = false)

        LaunchedEffect(isDark) {
            onDarkModeChange(isDark)
        }

        // Prime the stored language so it applies on startup, not just after the user
        // opens Settings.
        LaunchedEffect(Unit) {
            runCatching { settingsRepository.getAppLanguage() }
        }
        val appLanguage by settingsRepository.observeAppLanguage().collectAsState(initial = "en")

        // Override the platform locale before the keyed subtree composes, so every
        // stringResource resolves in the selected language. The key() forces a full
        // recomposition whenever the language changes (Android: live; iOS: next launch).
        applyAppLocale(appLanguage)
        key(appLanguage) {
            MookTheme {
                AppNavGraph()
                com.mcclabs.mook.feature.update.GlobalUpdateWrapper()
            }
        }
    }
}