package com.mcclabs.mook.domain.billing

import kotlinx.coroutines.flow.StateFlow

/**
 * Reklam gösterimini yöneten, Firebase Remote Config'den okunan ayarlar.
 *
 * Reklam sıklıkları artık koda gömülü sabitler değildir: AdMob politikası ihlali veya
 * kullanıcı şikâyeti durumunda reklamlar uygulama güncellemesi BEKLENMEDEN uzaktan
 * kapatılabilir (kill-switch) ya da sıklıkları anında ayarlanabilir.
 *
 * Varsayılanlar (Remote Config'e hiç ulaşılamazsa) bugünkü ürün kurallarıyla aynıdır;
 * böylece ağ hatası davranışı DEĞİŞTİRMEZ. Remote Config Anahtar Tanımları için bkz. [Keys].
 */
data class AdRemoteConfig(
    /** Genel kill-switch: `false` ise HİÇBİR reklam biçimi yüklenmez/gösterilmez. */
    val adsEnabled: Boolean = true,
    val interstitialEnabled: Boolean = true,
    val nativeEnabled: Boolean = true,
    val rewardedEnabled: Boolean = true,
    /** İki geçiş (interstitial) reklamı arasındaki en kısa süre (saniye). */
    val interstitialMinIntervalSeconds: Long = 90,
    /** Bir oturumda en fazla geçiş reklamı. */
    val interstitialSessionCap: Int = 6,
    /** Bir günde en fazla geçiş reklamı. */
    val interstitialDailyCap: Int = 20,
    /** Kaç başarılı beğenide bir geçiş reklamı denenir. */
    val interstitialActionsBetweenAds: Int = 3,
    /** Keşfet ızgarasında kaç profil kartında bir yerel (native) reklam kartı gösterilir. */
    val nativeAdCardInterval: Int = 10,
    /**
     * Bir kullanıcının bir günde izleyebileceği toplam ödüllü reklam sayısı (istemci tavanı).
     * Ödül hakları ayrıca sunucuda (AdMob SSV, `rewards.ts`) ödül türü başına sınırlanır;
     * varsayılan 4 = beğeni (1) + beni beğenenler (2) + oda değiştirme (1). `0` ödüllü
     * reklamları kapatır.
     */
    val rewardedAdFrequencyLimit: Int = 4,
) {
    val isInterstitialActive: Boolean get() = adsEnabled && interstitialEnabled && interstitialDailyCap > 0 && interstitialSessionCap > 0
    val isNativeActive: Boolean get() = adsEnabled && nativeEnabled
    val isRewardedActive: Boolean get() = adsEnabled && rewardedEnabled && rewardedAdFrequencyLimit > 0

    /** Bugün [shownToday] ödüllü reklam izlemiş bir kullanıcıya yeni bir ödüllü reklam gösterilebilir mi? */
    fun allowsRewardedAd(shownToday: Int): Boolean = isRewardedActive && shownToday < rewardedAdFrequencyLimit

    /** Bu ayarlarla çalışan geçiş reklamı sıklık politikası. */
    fun interstitialPolicy(): AdFrequencyPolicy = AdFrequencyPolicy(
        minIntervalMillis = interstitialMinIntervalSeconds * 1_000,
        sessionCap = interstitialSessionCap,
        dailyCap = interstitialDailyCap,
        actionsBetweenAds = interstitialActionsBetweenAds,
    )

    /** Remote Config Anahtar Tanımları. Firebase konsolundaki parametre adlarıyla birebir aynıdır. */
    object Keys {
        const val ADS_ENABLED = "ads_enabled"
        const val INTERSTITIAL_ENABLED = "ads_interstitial_enabled"
        const val NATIVE_ENABLED = "ads_native_enabled"
        const val REWARDED_ENABLED = "ads_rewarded_enabled"
        const val INTERSTITIAL_MIN_INTERVAL_SECONDS = "interstitial_min_interval_seconds"
        const val INTERSTITIAL_SESSION_CAP = "interstitial_session_cap"
        const val INTERSTITIAL_DAILY_CAP = "interstitial_daily_cap"
        const val INTERSTITIAL_ACTIONS_BETWEEN_ADS = "interstitial_actions_between_ads"
        const val NATIVE_AD_CARD_INTERVAL = "native_ad_card_interval"
        const val REWARDED_AD_FREQUENCY_LIMIT = "rewarded_ad_frequency_limit"
    }

    companion object {
        val DEFAULT = AdRemoteConfig()

        /** Remote Config'e `setDefaults` ile verilecek uygulama içi varsayılanlar. */
        val DEFAULTS: List<Pair<String, Any>> = listOf(
            Keys.ADS_ENABLED to DEFAULT.adsEnabled,
            Keys.INTERSTITIAL_ENABLED to DEFAULT.interstitialEnabled,
            Keys.NATIVE_ENABLED to DEFAULT.nativeEnabled,
            Keys.REWARDED_ENABLED to DEFAULT.rewardedEnabled,
            Keys.INTERSTITIAL_MIN_INTERVAL_SECONDS to DEFAULT.interstitialMinIntervalSeconds,
            Keys.INTERSTITIAL_SESSION_CAP to DEFAULT.interstitialSessionCap.toLong(),
            Keys.INTERSTITIAL_DAILY_CAP to DEFAULT.interstitialDailyCap.toLong(),
            Keys.INTERSTITIAL_ACTIONS_BETWEEN_ADS to DEFAULT.interstitialActionsBetweenAds.toLong(),
            Keys.NATIVE_AD_CARD_INTERVAL to DEFAULT.nativeAdCardInterval.toLong(),
            Keys.REWARDED_AD_FREQUENCY_LIMIT to DEFAULT.rewardedAdFrequencyLimit.toLong(),
        )

        /**
         * Ham Remote Config değerlerinden güvenli bir yapılandırma üretir. Konsolda yanlış
         * girilmiş değerler (negatif, aşırı büyük) makul aralıklara sıkıştırılır — bir yazım
         * hatası kullanıcıya dakikada bir reklam gösterilmesine YOL AÇAMAZ.
         *
         * @param boolean Anahtar için boolean değer; tanımsızsa `null`.
         * @param long Anahtar için tam sayı değer; tanımsız veya sayı değilse `null`.
         */
        fun fromRemote(boolean: (String) -> Boolean?, long: (String) -> Long?): AdRemoteConfig = AdRemoteConfig(
            adsEnabled = boolean(Keys.ADS_ENABLED) ?: DEFAULT.adsEnabled,
            interstitialEnabled = boolean(Keys.INTERSTITIAL_ENABLED) ?: DEFAULT.interstitialEnabled,
            nativeEnabled = boolean(Keys.NATIVE_ENABLED) ?: DEFAULT.nativeEnabled,
            rewardedEnabled = boolean(Keys.REWARDED_ENABLED) ?: DEFAULT.rewardedEnabled,
            // En az 30 sn: AdMob'un "art arda geçiş reklamı" politikasına karşı taban.
            interstitialMinIntervalSeconds = (long(Keys.INTERSTITIAL_MIN_INTERVAL_SECONDS) ?: DEFAULT.interstitialMinIntervalSeconds)
                .coerceIn(30L, 24L * 60 * 60),
            interstitialSessionCap = (long(Keys.INTERSTITIAL_SESSION_CAP) ?: DEFAULT.interstitialSessionCap.toLong())
                .coerceIn(0L, 20L).toInt(),
            interstitialDailyCap = (long(Keys.INTERSTITIAL_DAILY_CAP) ?: DEFAULT.interstitialDailyCap.toLong())
                .coerceIn(0L, 50L).toInt(),
            interstitialActionsBetweenAds = (long(Keys.INTERSTITIAL_ACTIONS_BETWEEN_ADS) ?: DEFAULT.interstitialActionsBetweenAds.toLong())
                .coerceIn(1L, 50L).toInt(),
            nativeAdCardInterval = (long(Keys.NATIVE_AD_CARD_INTERVAL) ?: DEFAULT.nativeAdCardInterval.toLong())
                .coerceIn(4L, 50L).toInt(),
            rewardedAdFrequencyLimit = (long(Keys.REWARDED_AD_FREQUENCY_LIMIT) ?: DEFAULT.rewardedAdFrequencyLimit.toLong())
                .coerceIn(0L, 20L).toInt(),
        )
    }
}

/**
 * Reklam ayarlarının (bkz. [AdRemoteConfig]) tek kaynağı. Reklam bileşenleri ve geçiş reklamı
 * denetleyicisi sabitler yerine [config]'i GÖZLEMLER; yeni değerler bir sonraki reklam
 * kararında hemen etkili olur.
 */
interface AdConfigRepository {
    val config: StateFlow<AdRemoteConfig>

    /**
     * Remote Config'den güncel değerleri çeker ve etkinleştirir. Hata fırlatmaz; ulaşılamazsa
     * son etkin (veya varsayılan) değerler korunur.
     */
    suspend fun refresh()
}
