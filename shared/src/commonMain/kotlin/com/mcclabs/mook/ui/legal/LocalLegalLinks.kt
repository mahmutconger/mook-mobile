package com.mcclabs.mook.ui.legal

import androidx.compose.runtime.staticCompositionLocalOf
import com.mcclabs.mook.domain.legal.LegalLinks
import com.mcclabs.mook.domain.legal.LegalLinksResolver

/**
 * Seçili uygulama diline göre yasal bağlantılar. `App.kt`, yerel ayarı geçersiz kılan aynı
 * `appLanguage` değeriyle sağlar; ekranlar URL'leri yalnızca buradan okur.
 */
val LocalLegalLinks = staticCompositionLocalOf<LegalLinks> { LegalLinksResolver.english }
