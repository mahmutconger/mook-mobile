package com.mcclabs.mook.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.FirebaseApp
import com.mcclabs.mook.domain.consent.ConsentPolicy
import com.mcclabs.mook.domain.consent.ConsentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Gereksinim 3 (Faz 6, KVKK/GDPR): Android uygulamasının UMP-önce-reklam ve UMP-önce-analiz
 * başlatma sırasını TEK BAŞINA sahiplenir.
 *
 * Hem AdMob HEM DE Firebase Analytics başlatılması, UMP'nin onay bilgisini ÇÖZMESİNE
 * ([ConsentInformation.canRequestAds]) kadar ERTELENİR -- daha önce yalnızca AdMob başlatması
 * bekletiliyordu; bu artık Analytics için de geçerlidir (bkz. [updatePermissionAndInitialize]).
 *
 * Türkiye'deki (KVKK) kullanıcılar için UMP'nin kendi IAB TCF akışı (öncelikle AB/İngiltere
 * bölgesel yasalarını hedefler) tek başına yeterli değildir; bu yüzden [crossBorderTransferAccepted]
 * ve [personalizedAdsAllowed] KALICI olarak (SharedPreferences, [PlatformPendingActionQueue.android.kt]
 * ile AYNI "Context'e Firebase ContentProvider üzerinden eriş" deseni) ayrıca saklanır ve
 * [com.mcclabs.mook.domain.consent.ConsentRepository] üzerinden dış dünyaya sunulur -- bkz. o
 * arayüzün KDoc'u.
 *
 * `canRequestAds`/`privacyOptionsRequired` (Compose `mutableStateOf`) MEVCUT reklam render
 * kodundaki (`AdFormats.android.kt`) doğrudan okumaları BOZMAMAK için OLDUĞU GİBİ bırakıldı;
 * [state] (yeni, [StateFlow] tabanlı) bunlarla PARALEL güncellenir ve
 * [com.mcclabs.mook.domain.consent.ConsentRepository]'nin DI'dan enjekte edilebilen tek
 * doğruluk kaynağıdır.
 */
object AdMobConsentManager {
    private var adsInitialized = false
    private var analyticsInitialized = false

    var canRequestAds by mutableStateOf(false)
        private set
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    private val mutableState = MutableStateFlow(ConsentState())
    val state: StateFlow<ConsentState> = mutableState.asStateFlow()

    private val prefs: SharedPreferences by lazy {
        FirebaseApp.getInstance().applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        mutableState.update {
            it.copy(
                crossBorderTransferAccepted = readBoolean(KEY_CROSS_BORDER, default = false),
                personalizedAdsAllowed = readBoolean(KEY_PERSONALIZED_ADS, default = false),
                decisionPolicyVersion = readPolicyVersion(),
            )
        }
    }

    fun requestConsent(activity: Activity) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updatePermissionAndInitialize(activity, consentInformation)
                }
            },
            {
                // A transient update failure may still leave a valid consent from a previous
                // session. UMP's canRequestAds() reflects that persisted state.
                updatePermissionAndInitialize(activity, consentInformation)
            },
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    /**
     * Gereksinim 3 (KVKK): sınır ötesi veri aktarımı (Firebase/RevenueCat/DeepL) onayını
     * KALICI olarak kaydeder ve [state]'i günceller. `false` iken Analytics toplama KAPALI
     * kalır (bkz. [applyAnalyticsGate]) -- temel eşleşme işlevi ETKİLENMEZ.
     */
    fun setCrossBorderTransferAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_CROSS_BORDER, accepted).apply()
        mutableState.update { it.copy(crossBorderTransferAccepted = accepted) }
        applyAnalyticsGate()
    }

    /** Gereksinim 3: kişiselleştirilmiş reklam onayını KALICI olarak kaydeder ve [state]'i günceller. */
    fun setPersonalizedAdsAllowed(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_PERSONALIZED_ADS, accepted).apply()
        mutableState.update { it.copy(personalizedAdsAllowed = accepted) }
        // Consent Mode reklam sinyalleri (ad_user_data / ad_personalization) da anında güncellenir.
        applyAnalyticsGate()
    }

    /**
     * KVKK onay ekranının kararını TEK bir SharedPreferences işleminde (iki izin + politika
     * sürümü birlikte) kalıcılaştırır ve tepkisel olarak uygular:
     * - Analytics toplama ve Consent Mode sinyalleri [applyAnalyticsGate] ile hemen güncellenir.
     * - AdMob istekleri NPA bayrağını her istekte [npaRequestExtras]'tan okuduğu için bir
     *   sonraki istek yeni kararı kullanır; önceden yüklenmiş geçiş reklamı eski onayla
     *   yüklendiyse gösterilmeden atılır (bkz. AdFormats.android.kt).
     */
    fun recordDecision(crossBorderTransferAccepted: Boolean, personalizedAdsAllowed: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CROSS_BORDER, crossBorderTransferAccepted)
            .putBoolean(KEY_PERSONALIZED_ADS, personalizedAdsAllowed)
            .putInt(KEY_POLICY_VERSION, ConsentPolicy.CURRENT_VERSION)
            .apply()
        mutableState.update {
            it.copy(
                crossBorderTransferAccepted = crossBorderTransferAccepted,
                personalizedAdsAllowed = personalizedAdsAllowed,
                decisionPolicyVersion = ConsentPolicy.CURRENT_VERSION,
            )
        }
        applyAnalyticsGate()
    }

    private fun readPolicyVersion(): Int? =
        runCatching { prefs.getInt(KEY_POLICY_VERSION, NO_DECISION) }.getOrDefault(NO_DECISION)
            .takeIf { it != NO_DECISION }

    /**
     * Gereksinim 4 (Faz 6, TFUA): tüm reklam isteklerine eklenecek ek paket -- [personalizedAdsAllowed]
     * `false` ise Google'ın Non-Personalized-Ads (NPA) bayrağını taşır. `AdFormats.android.kt`
     * içindeki HER `AdRequest.Builder()` çağrısı bunu `addNetworkExtrasBundle(AdMobAdapter::class.java, ...)`
     * ile eklemelidir.
     */
    fun npaRequestExtras(): Bundle = Bundle().apply {
        if (!state.value.personalizedAdsAllowed) putString("npa", "1")
    }

    private fun updatePermissionAndInitialize(
        activity: Activity,
        consentInformation: ConsentInformation,
    ) {
        privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        canRequestAds = consentInformation.canRequestAds()
        mutableState.update {
            it.copy(
                isResolved = true,
                adsAllowed = canRequestAds,
                privacyOptionsRequired = privacyOptionsRequired,
            )
        }
        if (canRequestAds && !adsInitialized) {
            adsInitialized = true
            // Gereksinim 4 (Faz 6, TFUA): WalkMatch kayıt sırasında 18 yaş doğrulaması yapar --
            // bu yüzden bayrak BİLİNÇLİ olarak ve HER ZAMAN AÇIKÇA `false` olarak ayarlanır
            // (Google'ın varsayılan "belirtilmemiş" durumuna asla bırakılmaz), 18+ uygulama
            // politikasına uyumu netleştirir.
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTagForUnderAgeOfConsent(TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
                    // Marka güvenliği: 18+ bir tanışma uygulamasında bile reklam içeriği en
                    // fazla "T" (Genç, 13+) derecesiyle sınırlanır — para kazanma raporundaki
                    // "İçerik: max_ad_content_rating = T" kuralı. Ayar SDK başlatılmadan ÖNCE ve
                    // global olarak uygulanır; sonraki TÜM reklam istekleri (geçiş, native,
                    // ödüllü) bundan etkilenir.
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
                    .build(),
            )
            MobileAds.initialize(activity.applicationContext) { }
        }
        applyAnalyticsGate()
    }

    /**
     * Gereksinim 3 (Faz 6): Analytics toplaması UMP onayı ÇÖZÜLENE ve KVKK'nin sınır ötesi
     * aktarım onayı VERİLENE kadar KAPALI tutulur -- Firebase Consent Mode v2 sinyalleri de
     * (analytics_storage/ad_storage/ad_user_data/ad_personalization) aynı anda güncellenir.
     */
    private fun applyAnalyticsGate() {
        val current = mutableState.value
        val analyticsAllowed = current.isResolved && current.crossBorderTransferAccepted
        mutableState.update { it.copy(analyticsAllowed = analyticsAllowed) }
        if (!analyticsInitialized && !analyticsAllowed) {
            // Henüz hiç başlatılmadıysa ve onay yoksa toplamayı aktif ETME -- yalnızca daha
            // sonra kapatmak (varsayılan açık davranışı tersine çevirmek) yerine hiç açmamak.
            runCatching {
                FirebaseAnalytics.getInstance(FirebaseApp.getInstance().applicationContext)
                    .setAnalyticsCollectionEnabled(false)
            }
        }
        val analyticsInstance = runCatching {
            FirebaseAnalytics.getInstance(FirebaseApp.getInstance().applicationContext)
        }.getOrNull() ?: return
        analyticsInstance.setAnalyticsCollectionEnabled(analyticsAllowed)
        if (analyticsAllowed) analyticsInitialized = true
        val personalizationStatus =
            if (current.personalizedAdsAllowed) FirebaseAnalytics.ConsentStatus.GRANTED
            else FirebaseAnalytics.ConsentStatus.DENIED
        val analyticsStatus =
            if (analyticsAllowed) FirebaseAnalytics.ConsentStatus.GRANTED
            else FirebaseAnalytics.ConsentStatus.DENIED
        runCatching {
            analyticsInstance.setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to analyticsStatus,
                    FirebaseAnalytics.ConsentType.AD_STORAGE to personalizationStatus,
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to personalizationStatus,
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to personalizationStatus,
                ),
            )
        }
    }

    private fun readBoolean(key: String, default: Boolean): Boolean =
        runCatching { prefs.getBoolean(key, default) }.getOrDefault(default)

    private const val PREFS_NAME = "mook_kvkk_consent"
    private const val KEY_CROSS_BORDER = "cross_border_transfer_accepted"
    private const val KEY_PERSONALIZED_ADS = "personalized_ads_allowed"
    private const val KEY_POLICY_VERSION = "consent_policy_version"
    private const val NO_DECISION = -1
}

object AdMobActivityHolder {
    @Volatile
    var activity: Activity? = null
}
