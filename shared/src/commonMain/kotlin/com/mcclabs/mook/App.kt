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
import com.mcclabs.mook.domain.repository.SettingsRepository
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

        MookTheme {
            AppNavGraph()
            com.mcclabs.mook.feature.update.GlobalUpdateWrapper()
        }
    }
}