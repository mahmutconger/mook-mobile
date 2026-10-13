package com.mcclabs.mook.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Scaffold boşluklarını uygular ve alt bileşenlerin aynı boşlukları tekrar eklemesini önler. */
fun Modifier.scaffoldPadding(padding: PaddingValues): Modifier =
    padding(padding).consumeWindowInsets(padding)

/**
 * Bütün ekranların ortak güvenli alanı. Sistem çubukları, ekran kesikleri ve klavye
 * boşlukları burada ayrılır; ekranlar yalnızca kendi araç çubuklarının boşluğunu uygular.
 * Pencere arka planı sistem çubuklarının arkasında çizilmeye devam eder.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable BoxScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("appScaffold"),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = contentWindowInsets,
    ) { innerPadding ->
        Box(
            modifier = Modifier.scaffoldPadding(innerPadding).fillMaxSize().testTag("appContent"),
            content = content,
        )
    }
}
