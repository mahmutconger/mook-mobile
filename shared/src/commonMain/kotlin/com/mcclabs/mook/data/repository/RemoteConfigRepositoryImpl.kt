package com.mcclabs.mook.data.repository

import com.mcclabs.mook.domain.repository.RemoteConfigRepository
import com.mcclabs.mook.util.Log
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.remoteconfig.remoteConfig

/** Gereksinim 2.13 (Faz 4): bkz. [RemoteConfigRepository] KDoc'u. */
private const val KEY_SHOW_ONBOARDING_TRIAL_OFFER = "show_onboarding_trial_offer"

/**
 * [RemoteConfigRepository]'nin `dev.gitlive.firebase.remoteconfig` (KMP Firebase
 * sarmalayıcısı) üzerinden gerçek implementasyonu — `UpdateRepositoryImpl`de zaten
 * kurulmuş olan `fetchAndActivate()` desenini birebir izler.
 */
class RemoteConfigRepositoryImpl : RemoteConfigRepository {

    override suspend fun shouldShowOnboardingTrialOffer(): Boolean {
        return try {
            val remoteConfig = Firebase.remoteConfig
            remoteConfig.fetchAndActivate()
            remoteConfig.getValue(KEY_SHOW_ONBOARDING_TRIAL_OFFER).asBoolean()
        } catch (e: Exception) {
            // Güvenli varsayılan: okuma başarısızsa özelliği KAPALI kabul et (bkz.
            // arayüzdeki "güvenli varsayılan ilkesi" KDoc'u).
            Log.e("Remote Config okunamadı: $KEY_SHOW_ONBOARDING_TRIAL_OFFER", e)
            false
        }
    }
}
