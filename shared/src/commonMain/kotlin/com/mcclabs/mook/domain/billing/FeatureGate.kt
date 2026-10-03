package com.mcclabs.mook.domain.billing

/** Kota kontrolüne ve reklam akışına tabi olan özellikler. */
enum class Feature { LIKE, MESSAGE, NEW_CHAT, ROOM_SWITCH, PROFILE_VISIT, LIKED_ME, REWIND, BOOST }

/** Bir eylemden önce gösterilebilecek reklam yerleşimleri (interstitial veya ödüllü). */
enum class AdPlacement { LIKE_INTERSTITIAL, PROFILE_VISIT_INTERSTITIAL, LIKES_REWARDED, ROOM_REWARDED, LIKED_ME_REWARDED }

/**
 * Bir özelliğin (feature) karar anındaki kullanım durumunu [FeatureGate]'e bildirmek için
 * kullanılan anlık görüntü (snapshot).
 *
 * Değerler her zaman yerel önbellekten (son bilinen sunucu cevabından) okunur — [FeatureGate]
 * bu değerleri asla ağ üzerinden sormaz; bu sayede karar senkron ve anında verilebilir
 * (bkz. Gereksinim 1.1: reklam istenmeden önce yerel kota kontrolü).
 */
data class UsageSnapshot(
    val likes: Int = 0,
    val messages: Int = 0,
    val newChats: Int = 0,
    val roomSwitches: Int = 0,
    val likedMeUnlocks: Int = 0,
    val rewinds: Int = 0,
    val boosts: Int = 0,
    /**
     * Bugün ödüllü reklam izlenerek kazanılmış ekstra beğeni sayısı. Yalnızca Free/Economy
     * kademesinde anlamlıdır (Gereksinim 2.5); [BillingConfig.effectiveDailyLikeLimit] bu
     * değeri taban beğeni limitine ekler (en fazla [BillingConfig.MAX_FREE_REWARDED_LIKES]
     * kadar).
     */
    val rewardedLikes: Int = 0,
    /**
     * Gereksinim 2.5: bugün ödüllü reklam izlenerek kazanılmış ekstra "beni beğenenler"
     * açma sayısı — [rewardedLikes] ile aynı desen, yalnızca Free/Economy'de anlamlıdır.
     */
    val rewardedLikedMeUnlocks: Int = 0,
    /**
     * Gereksinim 2.9 (Faz 4): bugün ödüllü reklam izlenerek kazanılmış ekstra oda değişimi
     * sayısı — [rewardedLikes] ile aynı desen, yalnızca Free/Economy'de anlamlıdır.
     */
    val rewardedRoomSwitches: Int = 0,
)

/** [FeatureGate.decide] tarafından üretilebilecek kararlar. */
sealed interface GateDecision {
    /** Eylem serbest; ek reklam veya limit engeli yok. */
    data object Allowed : GateDecision

    /** Eylemden önce belirtilen reklam yerleşimi gösterilmeli, ardından eylem devam etmelidir. */
    data class AdFirst(val placement: AdPlacement) : GateDecision

    /**
     * Kota tükendi. Eylem ASLA reklam istenmeden doğrudan engellenmeli ve Limit Sheet
     * açılmalıdır (bkz. Gereksinim 1.1 — kullanıcı hakkı kalmamışken reklam izletilmez).
     *
     * @property reason Limit Sheet'in hangi metni ve hangi aksiyonları göstereceğini belirleyen
     *   sebep (bkz. Gereksinim 1.5).
     * @property rewarded Kullanıcının bir ödüllü reklam izleyerek ek hak kazanabileceği
     *   yerleşim; böyle bir yol yoksa (ör. mesaj/oda/sohbet limitleri, ya da Free dışı bir
     *   kademe) `null` — ödüllü beğeni reklamı yalnızca Free kademede anlamlıdır.
     * @property upgradeTo Bu limiti gevşetecek bir üst kademe varsa o kademe; en üst kademede
     *   dahi aynı (ya da daha düşük) sayısal tavan uygulanıyorsa `null`. `null`, bir adil
     *   kullanım tavanına (ör. Standart/Premium'da günlük 2000 mesaj) ulaşıldığını gösterir —
     *   bu durumda Limit Sheet "yükselt" değil "adil kullanım" mesajı göstermelidir
     *   (bkz. Gereksinim 1.5).
     */
    data class LimitReached(
        val reason: LimitReason,
        val rewarded: AdPlacement?,
        val upgradeTo: Tier?,
    ) : GateDecision
}

/**
 * Reklam ve abonelik kotalarını istemci tarafında öngörmek için kullanılan saf (side-effect'siz)
 * mantık sınıfı.
 *
 * ÖNEMLİ: Bu sınıf yalnızca bir UX tahminidir; asıl (yetkili) zorlama her zaman sunucu
 * tarafındadır (bkz. `functions/src/monetization.ts` — `enforceMessageQuota`, `swipe`,
 * `switchRoom` vb. Firestore transaction'ları). Buradaki amacın tamamı, kullanıcıyı zaten
 * hakkı kalmamışken gereksiz yere bir reklam izlemeye zorlamamak ve arayüzü ağ turu
 * beklemeden anında doğru duruma taşımaktır — sunucu her zaman son sözü söyler ve bu sınıfın
 * ürettiği kararla çelişebilir (ör. çoklu cihaz senaryosunda önbellek bayatlamışsa).
 */
class FeatureGate {

    /**
     * Bir eylem için karar üretir.
     *
     * Sıra kritiktir: önce kota kontrolü yapılır. Kota tükenmişse [GateDecision.LimitReached]
     * döner ve reklam ASLA istenmez (Gereksinim 1.1). Kota müsaitse ve reklamla-önce (ad-first)
     * politikası aktifse [GateDecision.AdFirst] döner; aksi halde [GateDecision.Allowed] döner.
     *
     * @param showInterstitial Çağıran taraf (ör. [AdFrequencyPolicy]) bu turda bir interstitial
     *   gösterilmesinin uygun olduğuna zaten karar verdiyse `true`. Bu parametre yalnızca kota
     *   müsaitken dikkate alınır; kota tükenmişken hiçbir zaman reklam tetiklenmez.
     */
    fun decide(feature: Feature, state: EntitlementState, usage: UsageSnapshot, showInterstitial: Boolean): GateDecision {
        val limits = state.limits
        val tier = state.tier
        val effectiveLimit = effectiveLimitFor(feature, limits, tier, usage.rewardedLikes, usage.rewardedLikedMeUnlocks, usage.rewardedRoomSwitches)
        val exhausted = effectiveLimit != null && usageFor(feature, usage) >= effectiveLimit

        if (exhausted) {
            // reasonFor yalnızca PROFILE_VISIT için null döner; o özelliğin efektif limiti de
            // her zaman null olduğundan (yukarıdaki effectiveLimitFor) bu dal PROFILE_VISIT
            // için hiçbir zaman tetiklenmez. Yine de tip güvenliği için açıkça ele alınır.
            val reason = reasonFor(feature) ?: return GateDecision.Allowed
            return GateDecision.LimitReached(reason, rewardedPlacementFor(feature, tier), upgradeTierFor(feature, tier, state.catalog))
        }

        if (limits.showsAds && showInterstitial) {
            return when (feature) {
                Feature.LIKE -> GateDecision.AdFirst(AdPlacement.LIKE_INTERSTITIAL)
                Feature.PROFILE_VISIT -> GateDecision.AdFirst(AdPlacement.PROFILE_VISIT_INTERSTITIAL)
                else -> GateDecision.Allowed
            }
        }
        return GateDecision.Allowed
    }

    /**
     * Kota tükendiğinde, kullanıcının ödüllü reklam izleyerek ekstra hak kazanabileceği
     * yerleşimi belirler (Gereksinim 2.5).
     *
     * Yalnızca [BillingConfig.REWARDED_ELIGIBLE_TIERS] (Free, Economy) içindeki kademeler
     * için ve yalnızca ödüllü-reklam-destekli özellikler (beğeni, "beni beğenenler") için
     * `null`'dan farklı döner — mesaj/oda/sohbet limitleri gibi diğer tüm özellikler için
     * her zaman `null`'dır (bir reklamla "ekstra mesaj hakkı" satın alınamaz).
     */
    private fun rewardedPlacementFor(feature: Feature, tier: Tier): AdPlacement? {
        if (tier !in BillingConfig.REWARDED_ELIGIBLE_TIERS) return null
        return when (feature) {
            Feature.LIKE -> AdPlacement.LIKES_REWARDED
            Feature.LIKED_ME -> AdPlacement.LIKED_ME_REWARDED
            // Gereksinim 2.9: oda değişimi de artık ödüllü-reklam-destekli.
            Feature.ROOM_SWITCH -> AdPlacement.ROOM_REWARDED
            else -> null
        }
    }

    private fun reasonFor(feature: Feature): LimitReason? = when (feature) {
        Feature.LIKE -> LimitReason.DAILY_LIKES
        Feature.MESSAGE -> LimitReason.DAILY_MESSAGES
        Feature.NEW_CHAT -> LimitReason.DAILY_NEW_CHATS
        Feature.ROOM_SWITCH -> LimitReason.ROOM_SWITCHES
        Feature.LIKED_ME -> LimitReason.LIKED_ME_UNLOCKS
        Feature.REWIND -> LimitReason.REWINDS
        Feature.BOOST -> LimitReason.BOOSTS
        Feature.PROFILE_VISIT -> null
    }

    private fun usageFor(feature: Feature, usage: UsageSnapshot): Int = when (feature) {
        Feature.LIKE -> usage.likes
        Feature.MESSAGE -> usage.messages
        Feature.NEW_CHAT -> usage.newChats
        Feature.ROOM_SWITCH -> usage.roomSwitches
        Feature.LIKED_ME -> usage.likedMeUnlocks
        Feature.REWIND -> usage.rewinds
        Feature.BOOST -> usage.boosts
        Feature.PROFILE_VISIT -> 0
    }

    /**
     * Bir özelliğin efektif (bonusları dahil eden) günlük/aylık limitini döner. `null`,
     * "sınırsız" (bu kademede hiçbir sayısal tavan uygulanmaz) anlamına gelir.
     */
    private fun effectiveLimitFor(
        feature: Feature,
        limits: PlanLimits,
        tier: Tier,
        rewardedLikesToday: Int,
        rewardedLikedMeUnlocksToday: Int = 0,
        rewardedRoomSwitchesToday: Int = 0,
    ): Int? = when (feature) {
        Feature.LIKE -> BillingConfig.effectiveDailyLikeLimit(limits, tier, rewardedLikesToday)
        Feature.MESSAGE -> BillingConfig.effectiveDailyMessageLimit(limits)
        Feature.NEW_CHAT -> limits.dailyNewChats
        // Gereksinim 2.9: [BillingConfig.effectiveDailyRoomSwitchLimit] Free/Economy'de bir
        // ödüllü reklam bonusu ekler; taban hâlâ [PlanLimits.roomSwitchesPerDay]'dır.
        Feature.ROOM_SWITCH -> BillingConfig.effectiveDailyRoomSwitchLimit(limits, tier, rewardedRoomSwitchesToday)
        // Gereksinim 2.5: [BillingConfig.effectiveDailyLikedMeLimit] Free/Economy'de bir
        // ödüllü reklam bonusu ekler; taban hâlâ [PlanLimits.likedMeUnlocksPerDay]'dır.
        Feature.LIKED_ME -> BillingConfig.effectiveDailyLikedMeLimit(limits, tier, rewardedLikedMeUnlocksToday)
        Feature.REWIND -> limits.rewindsPerDay
        Feature.BOOST -> limits.boostsPerMonth
        Feature.PROFILE_VISIT -> null
    }

    /**
     * Bu özelliğin limitini gevşetecek en yakın üst kademeyi bulur.
     *
     * Sıradaki her kademe için ilgili efektif limit hesaplanır; `null` (sınırsız) ya da mevcut
     * kademeden sayıca büyükse o kademe önerilir. Hiçbir üst kademe daha yüksek bir sayı
     * sunmuyorsa (ör. Premium'da dahi sabit bir aylık boost tavanı varsa, ya da Standart ve
     * Premium'un ikisi de aynı adil kullanım mesaj tavanına tabiyse) `null` döner.
     *
     * Hesap, kullanıcının durumuyla gelen GÜNCEL plan kataloğu ([EntitlementState.catalog],
     * kaynak `config/plans`) üzerinden yapılır.
     */
    private fun upgradeTierFor(feature: Feature, tier: Tier, catalog: PlanCatalog): Tier? {
        val currentLimit = effectiveLimitFor(feature, catalog.limitsFor(tier), tier, 0)
        return Tier.entries
            .filter { it.ordinal > tier.ordinal }
            .firstOrNull { candidate ->
                val candidateLimit = effectiveLimitFor(feature, catalog.limitsFor(candidate), candidate, 0)
                candidateLimit == null || (currentLimit != null && candidateLimit > currentLimit)
            }
    }
}
