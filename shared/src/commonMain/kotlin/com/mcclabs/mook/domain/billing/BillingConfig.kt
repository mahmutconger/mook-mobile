package com.mcclabs.mook.domain.billing

/**
 * Reklam ve abonelik entegrasyonuyla ilgili sabitlerin toplandığı tek nokta.
 *
 * Sayıların buraya toplanması (UI/ViewModel'lere dağılmak yerine) ücretsiz kademeyi
 * ayarlamayı tek bir yerden yapılabilir kılar ve paywall metninin uygulanan değerlerle
 * her zaman uyumlu kalmasını sağlar.
 */
object BillingConfig {
    /** Ücretsiz (Free) kullanıcının takvim gününde (yerel saat) alabileceği kaydırma sayısı. */
    const val FREE_DAILY_SWIPE_LIMIT: Int = 10

    /**
     * Gereksinim 1 (Faz 6): `swipe` Cloud Function çağrısının en fazla ne kadar beklendiği.
     * Bu sürede sunucudan cevap gelmezse [com.mcclabs.mook.domain.billing.SwipeTimeoutFallbackHandler]
     * eylemi kalıcı kuyruğa alır ve arayüz asla bloklanmaz.
     */
    const val SWIPE_TIMEOUT_MILLIS: Long = 5_000L

    /**
     * Tüm premium özellikleri açan RevenueCat yetki (entitlement) tanımlayıcısı. RevenueCat
     * panelinde yapılandırılan yetkiyle birebir aynı olmalıdır.
     */
    const val ENTITLEMENT_PREMIUM: String = "premium"

    /**
     * Ödüllü reklamla ekstra hak kazanabilen kademeler.
     *
     * Gereksinim 2.5: Economy, hiçbir şekilde Free'den DAHA AZ yeteneğe sahip olmamalıdır.
     * Ödüllü reklamla beğeni/"beni beğenenler" hakkı kazanma daha önce yalnızca Free'ye
     * tanınmıştı — bu, günlük taban limitini (Free=10, Economy=30) dolduran bir Economy
     * kullanıcısının, Free'nin aksine, ek hak kazanacak HİÇBİR yolu olmadığı anlamına
     * geliyordu. Bu küme artık her iki kademeyi de kapsar.
     */
    val REWARDED_ELIGIBLE_TIERS: Set<Tier> = setOf(Tier.FREE, Tier.ECONOMY)

    /**
     * Ödüllü reklam ekonomisi: TEK bir beğeni reklamının kazandırdığı ek beğeni. Sunucudaki
     * `functions/src/rewards.ts` — `REWARDED_LIKES_PER_AD` ile BİLİNÇLİ olarak aynı tutulur.
     */
    const val REWARDED_LIKES_PER_AD: Int = 5

    /**
     * Free/Economy kullanıcısının ödüllü reklamla kazanabileceği günlük ekstra beğeni tavanı.
     * [REWARDED_LIKES_PER_AD] ile birlikte "günde 1 reklam = +5 beğeni" kuralını oluşturur.
     */
    const val MAX_FREE_REWARDED_LIKES: Int = 5

    /**
     * Gereksinim 2.5: Free/Economy kullanıcısının ödüllü reklamla kazanabileceği günlük
     * ekstra "beni beğenenler" açma hakkı tavanı. Bu iki kademede taban hak `0`'dır
     * (bkz. [PlanCatalog]) — ödüllü reklam, özelliğe küçük, sınırlı bir tat verir;
     * tam erişim hâlâ Standart/Premium'a yükseltme gerektirir.
     *
     * Gereksinim 2.9 (Faz 4): asıl (yetkili) tavan sunucudaki AdMob SSV callback'inde
     * uygulanır (`functions/src/monetization.ts` — `MAX_REWARDED_LIKED_ME_UNLOCKS`); bu
     * istemci sabiti onunla BİLİNÇLİ olarak aynı tutulur (eskiden `3` idi, `2`'ye
     * hizalandı — istemci burada yalnızca erken/iyimser bir UX tahmini yapar).
     */
    const val MAX_REWARDED_LIKED_ME_UNLOCKS: Int = 2

    /**
     * Gereksinim 2.9 (Faz 4): Free/Economy kullanıcısının ödüllü reklamla kazanabileceği
     * günlük ekstra oda değişimi hakkı tavanı. Sunucudaki AdMob SSV callback'iyle
     * (`MAX_REWARDED_ROOM_SWITCHES`) BİLİNÇLİ olarak aynı tutulur.
     */
    const val MAX_REWARDED_ROOM_SWITCHES: Int = 1

    /**
     * AdMob sunucu tarafı doğrulama (SSV) callback'inin beklenebileceği azami süre.
     *
     * Bu süre aşılırsa istemci iyimser arayüz güncellemesini geri alır ve yerelleştirilmiş
     * bir hata gösterir — ödül asla yerel olarak verilmez, yalnızca sunucudan gelen
     * doğrulanmış kullanım kaydına güvenilir.
     */
    const val REWARDED_SSV_TIMEOUT_MILLIS: Long = 10_000L

    /**
     * Bekleyen bir kaydırma eyleminin, uygulama yeniden başlatıldığında hâlâ "taze" sayılıp
     * sunucuya gönderilebileceği azami yaş. Bu sürenin ötesindeki bir kayıt muhtemelen zaten
     * sunucuda işlenmiş ya da kullanıcı tarafından anlamını yitirmiştir; sessizce atılır.
     */
    const val PENDING_ACTION_MAX_AGE_MILLIS: Long = 24 * 60 * 60 * 1000L

    /**
     * Bağlantı koptuğunda önbellekteki abonelik kademesinin hâlâ geçerli sayılacağı azami süre.
     * Bu pencerenin ötesinde önbellek "bayat" kabul edilir ve arayüz bunu kullanıcıya
     * yansıtabilir (ör. "abonelik durumu doğrulanamıyor" uyarısı) — yine de asla FREE'ye
     * düşürülmez; bkz. [PremiumRepository] ve `PlatformSubscriptionRepository.android.kt`.
     */
    const val CACHED_TIER_MAX_AGE_MILLIS: Long = 7 * 24 * 60 * 60 * 1000L

    /**
     * Günlük efektif mesaj SAYISI limiti. `null`, planın mesaj sayısına tavan koymadığı
     * (Standart/Premium) anlamına gelir.
     *
     * Eskiden `null` yerine düz bir "adil kullanım" mesaj sayısı kullanılıyordu; sunucu bu
     * tavanı artık uygulamıyor. Yalnızca Çeviri Kotası Mantığı gereği karakter kotası mesajı
     * değil yalnızca ÇEVİRİYİ sınırlar ve kota bittiğinde mesajlar çevrilmeden gönderilir.
     */
    fun effectiveDailyMessageLimit(limits: PlanLimits): Int? = limits.dailyMessages

    /**
     * Günlük efektif beğeni limitini hesaplar.
     *
     * Yalnızca Free kademede ödüllü reklamla kazanılan bonus beğeniler tabana eklenir
     * (en fazla [MAX_FREE_REWARDED_LIKES] kadar); `null` limit "sınırsız" anlamına gelir ve
     * olduğu gibi döner. Bu mantık daha önce [FeatureGate] ile ekran durumlarında ayrı ayrı
     * yazılıyordu; tek kaynağa indirgenmesi iki yerin farklı cevap vermesini imkânsız kılar.
     */
    fun effectiveDailyLikeLimit(limits: PlanLimits, tier: Tier, rewardedLikesToday: Int): Int? =
        limits.dailyLikes?.let { base ->
            if (tier in REWARDED_ELIGIBLE_TIERS) base + rewardedLikesToday.coerceIn(0, MAX_FREE_REWARDED_LIKES) else base
        }

    /**
     * Günlük efektif "beni beğenenler" açma limitini hesaplar (Gereksinim 2.5).
     *
     * [effectiveDailyLikeLimit] ile aynı desen: taban `null` ise (Standart/Premium'da
     * sınırsız) olduğu gibi sınırsız kalır; Free/Economy'de taban `0`'dır ve yalnızca
     * ödüllü reklam bonusu eklenir.
     */
    fun effectiveDailyLikedMeLimit(limits: PlanLimits, tier: Tier, rewardedLikedMeUnlocksToday: Int): Int? =
        limits.likedMeUnlocksPerDay?.let { base ->
            if (tier in REWARDED_ELIGIBLE_TIERS) base + rewardedLikedMeUnlocksToday.coerceIn(0, MAX_REWARDED_LIKED_ME_UNLOCKS) else base
        }

    /** Kullanıcı şu an ödüllü reklamla ekstra bir beğeni kazanabilir mi (Gereksinim 2.5). */
    fun canEarnRewardedLikeBonus(tier: Tier, rewardedLikesToday: Int): Boolean =
        // Bir reklam ödülü ya TAMAMEN verilir ya hiç: kalan tavan +5'e yetmiyorsa reklam
        // teklif edilmez (sunucudaki planRewardGrant ile aynı kural).
        tier in REWARDED_ELIGIBLE_TIERS && rewardedLikesToday + REWARDED_LIKES_PER_AD <= MAX_FREE_REWARDED_LIKES

    /** Kullanıcı şu an ödüllü reklamla ekstra bir "beni beğenenler" açma hakkı kazanabilir mi (Gereksinim 2.5). */
    fun canEarnRewardedLikedMeBonus(tier: Tier, rewardedLikedMeUnlocksToday: Int): Boolean =
        tier in REWARDED_ELIGIBLE_TIERS && rewardedLikedMeUnlocksToday < MAX_REWARDED_LIKED_ME_UNLOCKS

    /**
     * Gereksinim 2.9 (Faz 4): günlük efektif oda değişimi limitini hesaplar —
     * [effectiveDailyLikeLimit]/[effectiveDailyLikedMeLimit] ile aynı desen: taban `null`
     * ise (Standart/Premium'da sınırsız) olduğu gibi sınırsız kalır; Free/Economy'de taban
     * dolduğunda yalnızca ödüllü reklam bonusu eklenir (en fazla [MAX_REWARDED_ROOM_SWITCHES]).
     */
    fun effectiveDailyRoomSwitchLimit(limits: PlanLimits, tier: Tier, rewardedRoomSwitchesToday: Int): Int? =
        limits.roomSwitchesPerDay?.let { base ->
            if (tier in REWARDED_ELIGIBLE_TIERS) base + rewardedRoomSwitchesToday.coerceIn(0, MAX_REWARDED_ROOM_SWITCHES) else base
        }

    /** Kullanıcı şu an ödüllü reklamla ekstra bir oda değişimi hakkı kazanabilir mi (Gereksinim 2.9). */
    fun canEarnRewardedRoomSwitchBonus(tier: Tier, rewardedRoomSwitchesToday: Int): Boolean =
        tier in REWARDED_ELIGIBLE_TIERS && rewardedRoomSwitchesToday < MAX_REWARDED_ROOM_SWITCHES
}
