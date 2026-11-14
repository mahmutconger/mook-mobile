package com.mcclabs.mook.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug derlemesi: Play Integrity, Play dışından kurulan APK'ya jeton vermediği için Debug
 * sağlayıcısı kullanılır. İlk açılışta logcat'e (etiket `DebugAppCheckProvider`) bir debug
 * jetonu yazılır; bu jeton Firebase konsolunda App Check → Mook (Android) → "Debug
 * jetonlarını yönet" altına eklenmeden sunucu istekleri yine 401 döner.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
