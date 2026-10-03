package com.mcclabs.mook.ads

import androidx.compose.runtime.collectAsState
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.OnPaidEventListener
import com.google.firebase.auth.FirebaseAuth
import com.mcclabs.mook.domain.analytics.AdFormat
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.AdConfigRepository
import com.mcclabs.mook.domain.billing.AdRemoteConfig
import com.mcclabs.mook.domain.analytics.AnalyticsEvent
import com.mcclabs.mook.domain.analytics.AdFailureStep
import com.mcclabs.mook.domain.billing.LikeInterstitialGateway
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.koinInject
import kotlin.coroutines.resume
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.ads_sponsored
import org.jetbrains.compose.resources.stringResource

// Live production AdMob unit IDs; debug builds intentionally use Google's sample units.
private const val LIVE_INTERSTITIAL_AD_UNIT = "ca-app-pub-5294841790884915/7393075679"
private const val LIVE_NATIVE_AD_UNIT = "ca-app-pub-5294841790884915/5775821806"

private const val GOOGLE_TEST_INTERSTITIAL_AD_UNIT = "ca-app-pub-3940256099942544/1033173712"
private const val GOOGLE_TEST_NATIVE_AD_UNIT = "ca-app-pub-3940256099942544/2247696110"
private const val INTERSTITIAL_PREFS = "mook_interstitial_frequency"
/** Analitik yerleşim adları (ad_shown / ad_not_ready / ad_failed ve gelir olayları). */
private const val PLACEMENT_INTERSTITIAL = "discover_like_interstitial"
private const val PLACEMENT_PROFILE_VISIT = "profile_visit_interstitial"

private fun isDebuggable(context: android.content.Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

private fun configuredUnit(debug: Boolean, test: String, live: String): String? =
    if (debug) test else live.takeIf(String::isNotBlank)

/**
 * Gereksinim 4 (Faz 6, TFUA/NPA): [AdMobConsentManager.npaRequestExtras] taşınan
 * kişiselleştirilmemiş reklam (NPA) bayrağını bu isteğe ekler -- KVKK onayı reddedildiyse
 * (bkz. [com.mcclabs.mook.domain.consent.ConsentState.personalizedAdsAllowed]) reklam SDK'sı
 * kullanıcıyı hedeflemek için profil verisi KULLANMAZ. HER `AdRequest.Builder()` çağrısı
 * bunu uygulamalıdır (bkz. çağıran yerler).
 */
private fun AdRequest.Builder.withConsentExtras(): AdRequest.Builder =
    addNetworkExtrasBundle(AdMobAdapter::class.java, AdMobConsentManager.npaRequestExtras())

@Composable
actual fun NativeAdFeedCard(isFree: Boolean, modifier: Modifier, placement: String) {
    val context = LocalContext.current
    val analyticsRepository = koinInject<AnalyticsRepository>()
    val canRequestAds by remember {
        derivedStateOf { AdMobConsentManager.canRequestAds }
    }
    // Remote Config kill-switch'i: kapalıysa yerel reklam ne yüklenir ne gösterilir.
    val adConfig by koinInject<AdConfigRepository>().config.collectAsState()
    val nativeActive = adConfig.isNativeActive
    val adUnitId = configuredUnit(isDebuggable(context), GOOGLE_TEST_NATIVE_AD_UNIT, LIVE_NATIVE_AD_UNIT)
    val sponsoredLabel = stringResource(Res.string.ads_sponsored)
    var loadedAd by remember { mutableStateOf<NativeAd?>(null) }
    val isVisible = remember { booleanArrayOf(true) }

    LaunchedEffect(isFree, canRequestAds, adUnitId, nativeActive) {
        if (!nativeActive || !isFree || !canRequestAds || adUnitId == null || loadedAd != null) return@LaunchedEffect
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                if (isVisible[0]) {
                    // Gereksinim 2.10 (Faz 4): AdMob'un GERÇEK ödenen değeri (eCPM'in kaynağı) bildirdiği tek an — ARPDAU analizi için burada yakalanır.
                    ad.setOnPaidEventListener(OnPaidEventListener { adValue ->
                        analyticsRepository.logAdRevenuePaid(
                            placement = placement,
                            format = AdFormat.NATIVE,
                            valueMicros = adValue.valueMicros,
                            currencyCode = adValue.currencyCode,
                            precisionType = adValue.precisionType,
                        )
                    })
                    loadedAd = ad
                } else {
                    ad.destroy()
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdImpression() {
                    analyticsRepository.track(AnalyticsEvent.AdShown(placement, AdFormat.NATIVE))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    android.util.Log.w("MookAds", "Native ad failed to load: ${error.message}")
                    analyticsRepository.track(
                        AnalyticsEvent.AdFailed(placement, AdFormat.NATIVE, AdFailureStep.LOAD, error.code, error.message),
                    )
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().withConsentExtras().build())
    }
    DisposableEffect(loadedAd) {
        isVisible[0] = true
        onDispose {
            isVisible[0] = false
            loadedAd?.destroy()
        }
    }

    if (!nativeActive || !isFree || !canRequestAds || loadedAd == null) return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { createNativeAdView(context, loadedAd!!, sponsoredLabel) },
        update = { bindNativeAd(it, loadedAd!!, sponsoredLabel) },
    )
}

private fun createNativeAdView(context: android.content.Context, ad: NativeAd, sponsoredLabel: String): NativeAdView {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()
    val nativeView = NativeAdView(context)
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(29, 29, 38))
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(70, 70, 84))
        }
    }
    val attribution = TextView(context).apply {
        text = sponsoredLabel
        textSize = 11f
        setTextColor(Color.rgb(190, 190, 202))
    }
    val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val icon = ImageView(context)
    header.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
    val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), 0, 0, 0)
    }
    val headline = TextView(context).apply {
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        maxLines = 2
    }
    val advertiser = TextView(context).apply {
        textSize = 12f
        setTextColor(Color.rgb(186, 186, 199))
        maxLines = 1
    }
    textColumn.addView(headline)
    textColumn.addView(advertiser)
    header.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

    val body = TextView(context).apply {
        textSize = 13f
        setTextColor(Color.rgb(216, 216, 224))
        maxLines = 3
        setPadding(0, dp(8), 0, dp(8))
    }
    val callToAction = Button(context).apply {
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(104, 74, 255))
    }
    content.addView(attribution)
    content.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(8)
    })
    content.addView(body)
    content.addView(callToAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
    nativeView.addView(
        content,
        android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    nativeView.headlineView = headline
    nativeView.advertiserView = advertiser
    nativeView.bodyView = body
    nativeView.iconView = icon
    nativeView.callToActionView = callToAction
    bindNativeAd(nativeView, ad, sponsoredLabel)
    return nativeView
}

private fun bindNativeAd(view: NativeAdView, ad: NativeAd, sponsoredLabel: String) {
    view.headlineView?.let { (it as TextView).text = ad.headline }
    view.advertiserView?.let {
        (it as TextView).text = ad.advertiser ?: sponsoredLabel
    }
    view.bodyView?.let {
        (it as TextView).text = ad.body.orEmpty()
        it.visibility = if (ad.body.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
    }
    view.iconView?.let {
        val iconView = it as ImageView
        iconView.setImageDrawable(ad.icon?.drawable)
        iconView.visibility = if (ad.icon?.drawable == null) android.view.View.GONE else android.view.View.VISIBLE
    }
    view.callToActionView?.let {
        val cta = it as Button
        cta.text = ad.callToAction.orEmpty()
        cta.visibility = if (ad.callToAction.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
    }
    view.setNativeAd(ad)
}

/** Enforces the report's onboarding, cadence, cooldown, and frequency caps before a like. */
private object InterstitialAdController {
    private var loadedAd: InterstitialAd? = null
    /**
     * Önceden yüklenmiş reklamın hangi kişiselleştirme onayıyla istendiği. Kullanıcı KVKK
     * tercihini değiştirirse eski onayla yüklenmiş reklam GÖSTERİLMEZ, yenisi istenir.
     */
    private var loadedWithPersonalizedAds: Boolean? = null
    private var loading = false
    private var sessionAds = 0
    /** Gösterilmekte olan geçiş reklamının yerleşimi (gelir analitiği için). */
    private var currentPlacement = PLACEMENT_INTERSTITIAL
    // Gereksinim 2.10 (Faz 4): createLikeInterstitialGateway() DI'dan tam olarak bir kez
    // ayarlar — bu tekil `object` uygulama ömrü boyunca yaşadığından `var` olarak tutulur.
    private var analyticsRepository: AnalyticsRepository? = null
    // Remote Config: sıklık sınırları ve kill-switch sabitler yerine buradan okunur.
    private var adConfigRepository: AdConfigRepository? = null

    fun configure(repository: AnalyticsRepository, configRepository: AdConfigRepository) {
        analyticsRepository = repository
        adConfigRepository = configRepository
    }

    private val config: AdRemoteConfig
        get() = adConfigRepository?.config?.value ?: AdRemoteConfig.DEFAULT

    private fun track(event: AnalyticsEvent) {
        analyticsRepository?.track(event)
    }

    /**
     * Uid'ye göre ayrıştırılmış sıklık kayıtları: son gösterim anı, günlük sayaç ve beğeni
     * temposu. Gün değiştiyse günlük sayaç sıfırlanır.
     */
    private class FrequencyStore(private val preferences: android.content.SharedPreferences, uid: String) {
        val actionsKey = "actions_$uid"
        private val dailyCountKey = "daily_count_$uid"
        private val dailyKeyKey = "daily_key_$uid"
        private val lastShownKey = "last_shown_$uid"

        init {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            if (preferences.getString(dailyKeyKey, null) != today) {
                preferences.edit().putString(dailyKeyKey, today).putInt(dailyCountKey, 0).apply()
            }
        }

        val actions: Int get() = preferences.getInt(actionsKey, 0)
        val dailyAds: Int get() = preferences.getInt(dailyCountKey, 0)
        val lastShown: Long? get() = preferences.getLong(lastShownKey, 0L).takeIf { it > 0L }

        /** Bir geçiş reklamı gösterildi: tüm geçiş reklamlarının ortak sayaçları güncellenir. */
        fun recordShown(resetLikeCadence: Boolean) {
            val editor = preferences.edit()
                .putLong(lastShownKey, System.currentTimeMillis())
                .putInt(dailyCountKey, dailyAds + 1)
            if (resetLikeCadence) editor.putInt(actionsKey, 0)
            editor.apply()
        }
    }

    private fun frequencyStore(activity: android.app.Activity): FrequencyStore = FrequencyStore(
        activity.getSharedPreferences(INTERSTITIAL_PREFS, android.content.Context.MODE_PRIVATE),
        FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous",
    )

    private fun registeredAtMillis(): Long? = FirebaseAuth.getInstance().currentUser?.metadata?.creationTimestamp

    suspend fun attemptShowAfterTransition(isFree: Boolean, likesEver: Int): LikeInterstitialAttempt {
        if (!isFree) return LikeInterstitialAttempt()
        val currentConfig = config
        if (!currentConfig.isInterstitialActive) {
            // Kill-switch: önceden yüklenmiş reklam da atılır; sıklık sayacı ilerlemez.
            loadedAd = null
            return LikeInterstitialAttempt()
        }
        val activity = AdMobActivityHolder.activity ?: return LikeInterstitialAttempt()
        if (!AdMobConsentManager.canRequestAds) return LikeInterstitialAttempt()

        val store = frequencyStore(activity)
        val eligible = currentConfig.interstitialPolicy().canShow(
            nowMillis = System.currentTimeMillis(),
            registeredAtMillis = registeredAtMillis(),
            likesEver = likesEver,
            actionsSinceLastAd = store.actions + 1,
            lastAdAtMillis = store.lastShown,
            sessionAds = sessionAds,
            dailyAds = store.dailyAds,
        )
        if (!eligible) return LikeInterstitialAttempt(countSuccessfulLike = true)

        // Kaçan reklam fırsatı 3 beğenilik tempoyu ilerletmez ve kullanıcı eylemini asla geciktirmez.
        val ad = takeLoadedAd(PLACEMENT_INTERSTITIAL) ?: return LikeInterstitialAttempt()
        val shown = showAndAwaitDismiss(ad, activity, store, PLACEMENT_INTERSTITIAL, resetLikeCadence = true)
        return LikeInterstitialAttempt(adShown = shown)
    }

    /**
     * Profil ziyareti geçiş reklamı (her 3. ziyaret). Beğeni temposu UYGULANMAZ (o kural
     * ziyaret sayacındadır), ancak tüm geçiş reklamlarının ortak sınırları geçerlidir: ilk 24
     * saat, en kısa aralık, oturum ve gün tavanı.
     */
    suspend fun showProfileVisitInterstitial(isFree: Boolean): Boolean {
        if (!isFree) return false
        val currentConfig = config
        if (!currentConfig.isInterstitialActive) {
            loadedAd = null
            return false
        }
        val activity = AdMobActivityHolder.activity ?: return false
        if (!AdMobConsentManager.canRequestAds) return false

        val store = frequencyStore(activity)
        val eligible = currentConfig.interstitialPolicy().canShowAtTransition(
            nowMillis = System.currentTimeMillis(),
            registeredAtMillis = registeredAtMillis(),
            lastAdAtMillis = store.lastShown,
            sessionAds = sessionAds,
            dailyAds = store.dailyAds,
        )
        if (!eligible) return false
        val ad = takeLoadedAd(PLACEMENT_PROFILE_VISIT) ?: return false
        return showAndAwaitDismiss(ad, activity, store, PLACEMENT_PROFILE_VISIT, resetLikeCadence = false)
    }

    /** Hazır reklamı alır; yoksa `ad_not_ready` kaydeder ve bir sonraki fırsat için yükler. */
    private fun takeLoadedAd(placement: String): InterstitialAd? {
        // Onay değiştiyse (ör. kişiselleştirilmiş reklam izni yeni verildi/geri alındı) eski
        // onayla yüklenmiş reklam atılır; yeni istek güncel NPA bayrağıyla yapılır.
        if (loadedAd != null && loadedWithPersonalizedAds != AdMobConsentManager.state.value.personalizedAdsAllowed) {
            loadedAd = null
        }
        val ad = loadedAd
        if (ad == null) {
            track(AnalyticsEvent.AdNotReady(placement, AdFormat.INTERSTITIAL))
            loadIfNeeded()
            return null
        }
        loadedAd = null
        return ad
    }

    /** Reklamı gösterir ve kapatılana kadar bekler; gerçekten gösterildiyse `true`. */
    private suspend fun showAndAwaitDismiss(
        ad: InterstitialAd,
        activity: android.app.Activity,
        store: FrequencyStore,
        placement: String,
        resetLikeCadence: Boolean,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        var shown = false
        // Gelir olayının (OnPaidEventListener) doğru yerleşime yazılması için.
        currentPlacement = placement
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                shown = true
                track(AnalyticsEvent.AdShown(placement, AdFormat.INTERSTITIAL))
                sessionAds += 1
                store.recordShown(resetLikeCadence)
            }

            override fun onAdDismissedFullScreenContent() {
                if (continuation.isActive) continuation.resume(shown)
                loadIfNeeded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                android.util.Log.w("MookAds", "Interstitial failed to show: ${error.message}")
                track(AnalyticsEvent.AdFailed(placement, AdFormat.INTERSTITIAL, AdFailureStep.SHOW, error.code, error.message))
                if (continuation.isActive) continuation.resume(false)
                loadIfNeeded()
            }
        }
        try {
            ad.show(activity)
        } catch (error: Exception) {
            android.util.Log.w("MookAds", "Interstitial show threw", error)
            track(AnalyticsEvent.AdFailed(placement, AdFormat.INTERSTITIAL, AdFailureStep.SHOW, errorMessage = error.message))
            if (continuation.isActive) continuation.resume(false)
            loadIfNeeded()
        }
    }

    fun recordAction(isFree: Boolean, attempt: LikeInterstitialAttempt, succeeded: Boolean) {
        if (!isFree || !succeeded || !attempt.countSuccessfulLike) return
        val activity = AdMobActivityHolder.activity ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val preferences = activity.getSharedPreferences(INTERSTITIAL_PREFS, android.content.Context.MODE_PRIVATE)
        preferences.edit().putInt("actions_$uid", preferences.getInt("actions_$uid", 0) + 1).apply()
    }

    fun onDiscoveryVisible(isFree: Boolean) {
        if (!isFree || !config.isInterstitialActive) {
            loadedAd = null
            return
        }
        loadIfNeeded()
    }

    fun onDiscoveryHidden() = Unit

    private fun loadIfNeeded() {
        if (loading || loadedAd != null || !AdMobConsentManager.canRequestAds || !config.isInterstitialActive) return
        val activity = AdMobActivityHolder.activity ?: return
        val unitId = configuredUnit(
            isDebuggable(activity), GOOGLE_TEST_INTERSTITIAL_AD_UNIT, LIVE_INTERSTITIAL_AD_UNIT,
        ) ?: return
        loading = true
        val requestedWithPersonalizedAds = AdMobConsentManager.state.value.personalizedAdsAllowed
        InterstitialAd.load(
            activity,
            unitId,
            AdRequest.Builder().withConsentExtras().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    // Gereksinim 2.10 (Faz 4): AdMob'un GERÇEK ödenen değeri (eCPM'in kaynağı) bildirdiği tek an — ARPDAU analizi için burada yakalanır.
                    ad.setOnPaidEventListener(OnPaidEventListener { adValue ->
                        analyticsRepository?.logAdRevenuePaid(
                            placement = currentPlacement,
                            format = AdFormat.INTERSTITIAL,
                            valueMicros = adValue.valueMicros,
                            currencyCode = adValue.currencyCode,
                            precisionType = adValue.precisionType,
                        )
                    })
                    loading = false
                    loadedAd = ad
                    loadedWithPersonalizedAds = requestedWithPersonalizedAds
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    android.util.Log.w("MookAds", "Interstitial failed to load: ${error.message}")
                    track(
                        AnalyticsEvent.AdFailed(PLACEMENT_INTERSTITIAL, AdFormat.INTERSTITIAL, AdFailureStep.LOAD, error.code, error.message),
                    )
                }
            },
        )
    }

}

/** Gereksinim 2.15: AdMob SDK'sına bağımlı gerçek sağlayıcı — bkz. [LikeInterstitialGateway] KDoc'u. */
actual fun createLikeInterstitialGateway(
    analyticsRepository: AnalyticsRepository,
    adConfigRepository: AdConfigRepository,
): LikeInterstitialGateway {
    InterstitialAdController.configure(analyticsRepository, adConfigRepository)
    return object : LikeInterstitialGateway {
        override suspend fun attemptShowAfterTransition(isFree: Boolean, likesEver: Int): LikeInterstitialAttempt =
            InterstitialAdController.attemptShowAfterTransition(isFree, likesEver)

        override fun recordAction(isFree: Boolean, attempt: LikeInterstitialAttempt, succeeded: Boolean) {
            InterstitialAdController.recordAction(isFree, attempt, succeeded)
        }

        override suspend fun showProfileVisitInterstitial(isFree: Boolean): Boolean =
            InterstitialAdController.showProfileVisitInterstitial(isFree)
    }
}

actual fun onDiscoveryAdsVisible(isFree: Boolean) {
    InterstitialAdController.onDiscoveryVisible(isFree)
}

actual fun onDiscoveryAdsHidden() {
    InterstitialAdController.onDiscoveryHidden()
}
