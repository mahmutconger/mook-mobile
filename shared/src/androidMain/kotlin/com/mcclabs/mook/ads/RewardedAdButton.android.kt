package com.mcclabs.mook.ads

import com.mcclabs.mook.domain.analytics.AdFailureStep
import com.mcclabs.mook.domain.analytics.AnalyticsEvent
import com.mcclabs.mook.domain.billing.AdConfigRepository
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.domain.analytics.AdFormat
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.RewardType
import com.mcclabs.mook.domain.billing.SsvConfirmationPolicy
import com.mcclabs.mook.domain.billing.SsvConfirmationResult
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.ads_privacy_options
import mook.shared.generated.resources.ads_reward_button
import mook.shared.generated.resources.ads_reward_daily_limit_reached
import mook.shared.generated.resources.ads_reward_done
import mook.shared.generated.resources.ads_reward_error
import mook.shared.generated.resources.ads_reward_liked_me_button
import mook.shared.generated.resources.ads_reward_liked_me_done
import mook.shared.generated.resources.ads_reward_loading
import mook.shared.generated.resources.ads_reward_no_fill
import mook.shared.generated.resources.ads_reward_room_button
import mook.shared.generated.resources.ads_reward_room_done
import mook.shared.generated.resources.ads_reward_waiting
import mook.shared.generated.resources.error_offline
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val LIVE_REWARDED_AD_UNIT = "ca-app-pub-5294841790884915/4818972517"
private const val GOOGLE_TEST_REWARDED_AD_UNIT = "ca-app-pub-3940256099942544/5224354917"

/** Zaman aşımı mantığı platformdan bağımsızdır (bkz. Gereksinim 1.4). */
private val ssvConfirmationPolicy = SsvConfirmationPolicy()

/**
 * Remote Config'deki `rewarded_ad_frequency_limit` için cihaz üzerindeki günlük sayaç
 * (kullanıcı başına, yerel takvim günü). Ödül HAKLARI sunucuda (AdMob SSV) ayrıca sınırlanır;
 * bu sayaç yalnızca bir günde gösterilen toplam ödüllü REKLAM sayısını uzaktan kısabilmek içindir.
 */
private object RewardedAdDailyCounter {
    private const val PREFS = "mook_rewarded_frequency"

    private fun today(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private fun prefs(context: android.content.Context) =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    fun shownToday(context: android.content.Context, uid: String): Int {
        val preferences = prefs(context)
        return if (preferences.getString("day_$uid", null) == today()) preferences.getInt("count_$uid", 0) else 0
    }

    fun increment(context: android.content.Context, uid: String) {
        val next = shownToday(context, uid) + 1
        prefs(context).edit().putString("day_$uid", today()).putInt("count_$uid", next).apply()
    }
}

/** Düğme ve sonuç metinleri ödül türüne göre seçilir. */
private fun RewardType.buttonLabel(): StringResource = when (this) {
    RewardType.BONUS_LIKES -> Res.string.ads_reward_button
    RewardType.LIKED_ME_UNLOCK -> Res.string.ads_reward_liked_me_button
    RewardType.ROOM_SWITCH -> Res.string.ads_reward_room_button
}

private fun RewardType.doneLabel(): StringResource = when (this) {
    RewardType.BONUS_LIKES -> Res.string.ads_reward_done
    RewardType.LIKED_ME_UNLOCK -> Res.string.ads_reward_liked_me_done
    RewardType.ROOM_SWITCH -> Res.string.ads_reward_room_done
}

/** Gelir analitiğinde yerleşim adı (ARPDAU kırılımı için). */
private fun RewardType.analyticsPlacement(): String = when (this) {
    RewardType.BONUS_LIKES -> "rewarded_like"
    RewardType.LIKED_ME_UNLOCK -> "rewarded_liked_me"
    RewardType.ROOM_SWITCH -> "rewarded_room_switch"
}

/** `getUsage` yanıtında bu ödülün SSV ile artırılan sayacı. */
private fun RewardType.earnedIn(usage: DailyUsage): Int = when (this) {
    RewardType.BONUS_LIKES -> usage.rewardedLikes
    RewardType.LIKED_ME_UNLOCK -> usage.rewardedLikedMeUnlocks
    RewardType.ROOM_SWITCH -> usage.rewardedRoomSwitches
}

/**
 * Üç ödül türü için ortak ödüllü reklam akışı:
 * 1. Sunucuya uygunluk sorulur (`canEarnReward`) — ödülü verilemeyecek reklam ASLA izletilmez.
 * 2. Önceden yüklenmiş reklam (yoksa o an yüklenen) KVKK tercihine göre NPA bayrağıyla istenir
 *    ve SSV `custom_data` = [RewardType.ssvCustomData] ile gösterilir.
 * 3. Ödül YEREL olarak verilmez: sunucudaki sayaç (AdMob SSV) artana kadar beklenir; ancak
 *    o zaman [onRewardConfirmed] çağrılır.
 * Yükleniyor / reklam yok / günlük limit / çevrimdışı / doğrulama zaman aşımı durumları
 * kullanıcıya ayrı, yerelleştirilmiş mesajlarla gösterilir.
 */
@Composable
actual fun RewardedAdButton(
    rewardType: RewardType,
    enabled: Boolean,
    onRewardConfirmed: () -> Unit,
) {
    val context = LocalContext.current
    val activity = AdMobActivityHolder.activity
    val scope = rememberCoroutineScope()
    val connectivityObserver = koinInject<ConnectivityObserver>()
    val analyticsRepository = koinInject<AnalyticsRepository>()
    // Remote Config: ödüllü reklam kill-switch'i ve günlük sıklık sınırı.
    val adConfig by koinInject<AdConfigRepository>().config.collectAsState()
    val isOnline by connectivityObserver.isOnline.collectAsState()
    val consentAllowsAds by remember { derivedStateOf { AdMobConsentManager.canRequestAds } }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Reklam önceden yüklenir; böylece "doldurma yok" durumu kullanıcı dokunmadan bilinir.
    var preloadedAd by remember { mutableStateOf<RewardedAd?>(null) }
    var noFill by remember { mutableStateOf(false) }
    val adUnit = if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        GOOGLE_TEST_REWARDED_AD_UNIT
    } else {
        LIVE_REWARDED_AD_UNIT
    }

    val buttonLabel = stringResource(if (working) Res.string.ads_reward_loading else rewardType.buttonLabel())
    val errorLabel = stringResource(Res.string.ads_reward_error)
    val dailyLimitLabel = stringResource(Res.string.ads_reward_daily_limit_reached)
    val noFillLabel = stringResource(Res.string.ads_reward_no_fill)
    val waitingLabel = stringResource(Res.string.ads_reward_waiting)
    val doneLabel = stringResource(rewardType.doneLabel())
    val offlineLabel = stringResource(Res.string.error_offline)

    val rewardedActive = adConfig.isRewardedActive
    LaunchedEffect(enabled, isOnline, consentAllowsAds, activity, preloadedAd, rewardedActive) {
        if (!rewardedActive || !enabled || !isOnline || !consentAllowsAds || activity == null || preloadedAd != null) return@LaunchedEffect
        runCatching { loadRewardedAd(activity, adUnit, rewardType, analyticsRepository) }
            .onSuccess { ad ->
                noFill = false
                preloadedAd = ad
            }
            .onFailure { noFill = true }
    }

    // Kill-switch kapalıysa ödüllü reklam düğmesi hiç gösterilmez.
    if (!rewardedActive) return

    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            enabled = enabled && consentAllowsAds && !working && !noFill && activity != null,
            onClick = {
                val currentActivity = AdMobActivityHolder.activity ?: return@TextButton
                if (!isOnline) {
                    message = offlineLabel
                    return@TextButton
                }
                val uid = Firebase.auth.currentUser?.uid
                if (uid.isNullOrBlank()) {
                    message = errorLabel
                    return@TextButton
                }
                scope.launch {
                    working = true
                    message = null
                    try {
                        val eligibility = appHttpsCallable("canEarnReward")
                            .invoke(RewardEligibilityRequest(rewardType.ssvCustomData))
                            .data<RewardEligibility>()
                        if (!eligibility.eligible ||
                            !adConfig.allowsRewardedAd(RewardedAdDailyCounter.shownToday(currentActivity, uid))
                        ) {
                            message = dailyLimitLabel
                            return@launch
                        }
                        val ad = preloadedAd ?: run {
                            // Kullanıcı hazır olmayan bir reklam istedi; anında yükleme denenir.
                            analyticsRepository.track(AnalyticsEvent.AdNotReady(rewardType.analyticsPlacement(), AdFormat.REWARDED))
                            loadRewardedAd(currentActivity, adUnit, rewardType, analyticsRepository)
                        }
                        preloadedAd = null
                        ad.setServerSideVerificationOptions(
                            ServerSideVerificationOptions.Builder()
                                .setUserId(uid)
                                .setCustomData(rewardType.ssvCustomData)
                                .build(),
                        )
                        val shown = ad.showAndAwaitReward(
                            activity = currentActivity,
                            onShown = {
                                RewardedAdDailyCounter.increment(currentActivity, uid)
                                analyticsRepository.track(AnalyticsEvent.AdShown(rewardType.analyticsPlacement(), AdFormat.REWARDED))
                            },
                            onShowFailed = { error ->
                                analyticsRepository.track(
                                    AnalyticsEvent.AdFailed(
                                        rewardType.analyticsPlacement(), AdFormat.REWARDED, AdFailureStep.SHOW, error.code, error.message,
                                    ),
                                )
                            },
                        )
                        if (!shown) {
                            message = errorLabel
                            return@launch
                        }
                        // İyimser durum: "doğrulanıyor" gösterilir, hak yerelde VERİLMEZ.
                        message = waitingLabel
                        val confirmation = ssvConfirmationPolicy.await {
                            val usage = appHttpsCallable("getUsage").invoke().data<DailyUsage>()
                            rewardType.earnedIn(usage) > eligibility.earnedToday
                        }
                        analyticsRepository.track(
                            AnalyticsEvent.RewardedEarned(
                                rewardType = rewardType,
                                placement = rewardType.analyticsPlacement(),
                                verified = confirmation == SsvConfirmationResult.Confirmed,
                            ),
                        )
                        when (confirmation) {
                            SsvConfirmationResult.Confirmed -> {
                                message = doneLabel
                                onRewardConfirmed()
                            }
                            // Doğrulama 10 sn'de gelmedi: en olası sebep sunucuda günlük tavana
                            // o arada ulaşılmasıdır. Sunucu SSV'yi geç işlerse gerçek bakiye bir
                            // sonraki senkronizasyonda yansır.
                            SsvConfirmationResult.TimedOut -> message = dailyLimitLabel
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        message = errorLabel
                        android.util.Log.w("MookAds", "Ödüllü reklam akışı başarısız (${rewardType.name})", error)
                    } finally {
                        working = false
                    }
                }
            },
        ) {
            if (working) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
            Text(buttonLabel)
        }
        when {
            message != null -> Text(message!!, modifier = Modifier.padding(horizontal = 12.dp))
            noFill && enabled -> Text(noFillLabel, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
actual fun AdPrivacyOptionsEntry() {
    val activity = AdMobActivityHolder.activity
    val required by remember { derivedStateOf { AdMobConsentManager.privacyOptionsRequired } }
    if (required && activity != null) {
        TextButton(onClick = { AdMobConsentManager.showPrivacyOptions(activity) }) {
            Text(stringResource(Res.string.ads_privacy_options))
        }
    }
}

private suspend fun loadRewardedAd(
    activity: android.app.Activity,
    adUnit: String,
    rewardType: RewardType,
    analyticsRepository: AnalyticsRepository,
): RewardedAd = suspendCoroutine { continuation ->
    RewardedAd.load(
        activity,
        adUnit,
        // KVKK: kişiselleştirilmiş reklam izni yoksa NPA bayrağı eklenir (tüm reklam
        // istekleriyle aynı kural; bkz. AdMobConsentManager.npaRequestExtras).
        AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, AdMobConsentManager.npaRequestExtras())
            .build(),
        object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                // ARPDAU analizi: AdMob'un bildirdiği GERÇEK ödenen değer.
                ad.setOnPaidEventListener(OnPaidEventListener { adValue ->
                    analyticsRepository.logAdRevenuePaid(
                        placement = rewardType.analyticsPlacement(),
                        format = AdFormat.REWARDED,
                        valueMicros = adValue.valueMicros,
                        currencyCode = adValue.currencyCode,
                        precisionType = adValue.precisionType,
                    )
                })
                continuation.resume(ad)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                analyticsRepository.track(
                    AnalyticsEvent.AdFailed(rewardType.analyticsPlacement(), AdFormat.REWARDED, AdFailureStep.LOAD, error.code, error.message),
                )
                continuation.resumeWith(Result.failure(IllegalStateException(error.message)))
            }
        },
    )
}

private suspend fun RewardedAd.showAndAwaitReward(
    activity: android.app.Activity,
    onShown: () -> Unit,
    onShowFailed: (AdError) -> Unit,
): Boolean =
    suspendCoroutine { continuation ->
        var earned = false
        fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                onShown()
            }

            override fun onAdDismissedFullScreenContent() {
                continuation.resume(earned)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onShowFailed(error)
                continuation.resume(false)
            }
        }
        show(activity) { earned = true }
    }

@Serializable
private data class RewardEligibilityRequest(val reward: String)

@Serializable
private data class RewardEligibility(
    val eligible: Boolean = false,
    val earnedToday: Int = 0,
    val grantPerAd: Int = 0,
    val dailyCap: Int = 0,
    val adsRemainingToday: Int = 0,
)

@Serializable
private data class DailyUsage(
    val rewardedLikes: Int = 0,
    val rewardedLikedMeUnlocks: Int = 0,
    val rewardedRoomSwitches: Int = 0,
)
