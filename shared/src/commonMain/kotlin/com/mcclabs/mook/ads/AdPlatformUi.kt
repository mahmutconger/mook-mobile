package com.mcclabs.mook.ads

import com.mcclabs.mook.domain.billing.AdConfigRepository
import com.mcclabs.mook.domain.billing.RewardType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mcclabs.mook.domain.billing.LikeInterstitialGateway
import com.mcclabs.mook.domain.analytics.AnalyticsRepository

/**
 * Ödüllü reklam düğmesi (beğeni, "beni beğenenler", oda değiştirme). Ödül yalnızca AdMob SSV
 * sunucudaki ilgili sayacı artırdıktan SONRA [onRewardConfirmed] ile bildirilir.
 */
@Composable
expect fun RewardedAdButton(
    rewardType: RewardType,
    enabled: Boolean,
    onRewardConfirmed: () -> Unit,
)

/** Shows the UMP privacy-options entry point only when the user's message requires it. */
@Composable
expect fun AdPrivacyOptionsEntry()

// Banner reklam bileşeni bilinçli olarak YOKTUR: para kazanma kuralları banner reklamı
// hiçbir ekranda kullanmaz (bkz. domain.billing.AdDisplayRules).

/**
 * Akışlarda seyrek kullanılan yerel (native) reklam kartı.
 *
 * @param placement Analitik ve gelir raporlarındaki yerleşim adı (ör. "discover_native",
 *   "liked_me_native").
 */
@Composable
expect fun NativeAdFeedCard(isFree: Boolean, modifier: Modifier = Modifier, placement: String = "discover_native")

/** A pending pre-like ad decision; the action proceeds when no ad can be shown. */
data class LikeInterstitialAttempt(
    val countSuccessfulLike: Boolean = false,
    /** Reklam gerçekten gösterilip kapatıldı mı? (Günlük upsell kartı bunu tetikleyici olarak kullanır.) */
    val adShown: Boolean = false,
)

/**
 * Platforma özgü (AdMob SDK'sına bağımlı) [LikeInterstitialGateway] sağlayıcısı.
 *
 * Gereksinim 2.15: çağrı SIRASI (sunucu yanıtından SONRA) artık bu arayüzü kullanan
 * ViewModel'lerin sorumluluğundadır — bkz. [LikeInterstitialGateway] KDoc'u.
 *
 * Gereksinim 2.10 (Faz 4): [analyticsRepository] Android implementasyonunda,
 * interstitial'ın AdMob'un `OnPaidEventListener` geri çağrısıyla bildirdiği GERÇEK
 * geliri (eCPM'in kaynağı) ARPDAU analizi için kaydetmekte kullanılır.
 *
 * [adConfigRepository]: geçiş reklamı sıklığı ve kill-switch Remote Config'den okunur
 * (bkz. [com.mcclabs.mook.domain.billing.AdRemoteConfig]).
 */
expect fun createLikeInterstitialGateway(
    analyticsRepository: AnalyticsRepository,
    adConfigRepository: AdConfigRepository,
): LikeInterstitialGateway

/** Called when Discovery becomes visible/hidden to safely show a pending interstitial. */
expect fun onDiscoveryAdsVisible(isFree: Boolean)
expect fun onDiscoveryAdsHidden()
