package com.mcclabs.mook.data.consent

import com.mcclabs.mook.domain.consent.ConsentRepository
import com.mcclabs.mook.domain.consent.ConsentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual fun createConsentRepository(): ConsentRepository = NoOpConsentRepository

/**
 * iOS reklam/analiz entegrasyonu bu fazın kapsamı DIŞINDADIR (bkz. görev tanımı: "Focus
 * EXCLUSIVELY on Android"). Bu, yalnızca `expect`/`actual` sözleşmesini KMP derlemesi için
 * yerine getiren güvenli, hiçbir şey yapmayan (no-op) bir gövdedir -- Gereksinim 3'ün
 * KAPSAMINDA DEĞİLDİR; iOS reklam/analiz teklifi canlıya alındığında gerçek bir UMP/ATT
 * tabanlı implementasyonla değiştirilmelidir.
 */
private object NoOpConsentRepository : ConsentRepository {
    private val mutableState = MutableStateFlow(ConsentState())
    override val state: StateFlow<ConsentState> = mutableState

    override fun refreshConsent() = Unit
    override fun setCrossBorderTransferAccepted(accepted: Boolean) = Unit
    override fun setPersonalizedAdsAllowed(accepted: Boolean) = Unit
    // iOS'ta henüz reklam/Analytics geçidi yok; durum hiç "çözülmüş" olmadığından onay ekranı
    // da gösterilmez (bkz. ConsentState.requiresDecision).
    override fun recordDecision(crossBorderTransferAccepted: Boolean, personalizedAdsAllowed: Boolean) = Unit
    override fun showPrivacyOptionsForm() = Unit
}
