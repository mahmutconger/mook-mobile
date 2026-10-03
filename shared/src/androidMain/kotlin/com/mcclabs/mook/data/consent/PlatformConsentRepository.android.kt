package com.mcclabs.mook.data.consent

import com.mcclabs.mook.ads.AdMobActivityHolder
import com.mcclabs.mook.ads.AdMobConsentManager
import com.mcclabs.mook.domain.consent.ConsentRepository
import com.mcclabs.mook.domain.consent.ConsentState
import com.mcclabs.mook.util.Log
import kotlinx.coroutines.flow.StateFlow

actual fun createConsentRepository(): ConsentRepository = AndroidConsentRepository

/**
 * Gereksinim 3 (Faz 6): [ConsentRepository]'nin Android implementasyonu -- [AdMobConsentManager]'ı
 * (UMP + Firebase Consent Mode v2 + KVKK'ye özgü kalıcı onaylar) sarmalar. UMP SDK'sı bir
 * [android.app.Activity] gerektirdiğinden [AdMobActivityHolder.activity] (MainActivity'nin
 * `onResume`'da ayarladığı) kullanılır; henüz bir Activity yoksa (ör. arka planda bir
 * ViewModel önyükleniyorsa) çağrılar sessizce atlanır -- bir sonraki `onResume`'da
 * `MainActivity.onCreate`'in doğrudan çağrısı zaten [AdMobConsentManager.requestConsent]'i tetikler.
 */
private object AndroidConsentRepository : ConsentRepository {
    override val state: StateFlow<ConsentState> = AdMobConsentManager.state

    override fun refreshConsent() {
        val activity = AdMobActivityHolder.activity
        if (activity == null) {
            Log.e("ConsentRepository.refreshConsent: henüz bir Activity yok, atlanıyor")
            return
        }
        AdMobConsentManager.requestConsent(activity)
    }

    override fun setCrossBorderTransferAccepted(accepted: Boolean) {
        AdMobConsentManager.setCrossBorderTransferAccepted(accepted)
    }

    override fun setPersonalizedAdsAllowed(accepted: Boolean) {
        AdMobConsentManager.setPersonalizedAdsAllowed(accepted)
    }

    override fun recordDecision(crossBorderTransferAccepted: Boolean, personalizedAdsAllowed: Boolean) {
        AdMobConsentManager.recordDecision(crossBorderTransferAccepted, personalizedAdsAllowed)
    }

    override fun showPrivacyOptionsForm() {
        val activity = AdMobActivityHolder.activity ?: return
        AdMobConsentManager.showPrivacyOptions(activity)
    }
}
