package com.mcclabs.mook.domain.billing

import kotlinx.coroutines.flow.StateFlow

/** Server-aligned subscription tiers. The order is intentional and used for entitlement fallback. */
enum class Tier { FREE, ECONOMY, STANDARD, PREMIUM }

data class PlanLimits(
    val dailyLikes: Int?,
    val dailyMessages: Int?,
    val dailyNewChats: Int?,
    val roomSlots: Int?,
    val roomSwitchesPerDay: Int?,
    val showsAds: Boolean,
    val freeRoam: Boolean,
    val incognito: Boolean,
    val likedMeUnlocksPerDay: Int?,
    val rewindsPerDay: Int?,
    val boostsPerMonth: Int,
)

data class EntitlementState(
    val tier: Tier = Tier.FREE,
    /** False until RevenueCat has returned account info; ads stay hidden while plan is unknown. */
    val isResolved: Boolean = false,
    val expiresAtMillis: Long? = null,
    val willRenew: Boolean = false,
    val inTrial: Boolean = false,
    /** Kademenin GÜNCEL sınırları — `config/plans`tan gelen [catalog]dan türetilir. */
    val limits: PlanLimits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE),
    /**
     * Sınırların türetildiği plan kataloğu (Tek Doğruluk Kaynağı: `config/plans`). Yükseltme
     * önerisi gibi "başka bir kademe ne sunar?" kararları da bu tabloyu kullanır.
     */
    val catalog: PlanCatalog = PlanCatalog.BUNDLED,
    /**
     * Gereksinim 2.3/2.4: aktif yetkinin ALTINDAKİ mağaza ürün kimliği (ör.
     * "mook_premium:monthly-v1") — [PlanPackage.identifier] (RevenueCat'in KENDİ paket
     * kimliği, ör. "$rc_monthly") ile KARIŞTIRILMAMALIDIR; bu, [PlanPackage.productIdentifier]
     * ile eşleşen alandır. Yalnızca RevenueCat'ten CANLI bir `CustomerInfo` geldiğinde
     * doldurulur (soğuk başlangıç önbelleğinde saklanmaz) — bkz.
     * `AndroidRevenueCatSubscriptionRepository.updateState()`.
     */
    val activeProductIdentifier: String? = null,
    /**
     * Gereksinim 2.3/2.4: bu cihazda EN SON gerçekleştirilen satın almanın anlık
     * görüntüsü (fiyat + para birimi) — [PriceChangeConfirmationUseCase]'in "o zaman
     * ödediğin" ile "şu an listelenen" fiyatı karşılaştırabilmesi için cihazda kalıcı
     * olarak saklanır (bkz. `CachedTierStore.savePurchasePrice`). RevenueCat'in
     * `CustomerInfo`'su satın alma ANINDAKİ fiyatı geriye dönük olarak sağlamadığından,
     * bu bilgi yalnızca BU cihazda BİZZAT satın alma yapıldığı anda yakalanabilir — bu
     * yüzden `restore()` bunu güncellemez ve başka bir cihazda satın alınıp bu cihaza
     * aktarılan bir abonelik için `null` kalabilir (bkz. [PriceChangeCheckResult.Unknown]).
     */
    val lastKnownPurchasePrice: PurchasePriceSnapshot? = null,
    /**
     * Aktif yetkinin Play temel plan kimliği (ör. `monthly`). RevenueCat Android'de
     * [activeProductIdentifier] yalnızca abonelik kimliğini taşır; aylık/yıllık ayrımı ve
     * "zaten aktif" kontrolü için bu alan gerekir (bkz. [SubscriptionChangePolicy]). Yalnızca
     * canlı `CustomerInfo`'dan doldurulur.
     */
    val activeBasePlanIdentifier: String? = null,
    /**
     * Aktif yetkinin kaynağı. `null` ise canlı bilgi henüz gelmemiştir (yalnızca önbellek) ve
     * plan değişikliği güvenle kurulamaz (bkz. [SubscriptionChangePolicy]).
     */
    val activeSubscriptionSource: SubscriptionSource? = null,
    /**
     * RevenueCat ödeme sorunu tespit ettiyse (`EntitlementInfo.billingIssueDetectedAt`) o an;
     * doluysa Ayarlar'da "Ödeme Yöntemini Güncelle" bağlantısı gösterilir.
     */
    val billingIssueDetectedAtMillis: Long? = null,
    /** Mağazanın abonelik yönetim sayfası (`CustomerInfo.managementURL`); yoksa `null`. */
    val managementUrl: String? = null,
    /** Dönem sonuna ertelenmiş plan değişikliği (ör. Standart → Ekonomik düşürme); yoksa `null`. */
    val scheduledChange: ScheduledPlanChange? = null,
)

/**
 * Gereksinim 2.3/2.4: bir satın almanın YAPILDIĞI ANDAKİ ham fiyat bilgisi.
 *
 * [formattedPrice], kullanıcıya gösterilecek zaten yerelleştirilmiş metindir (ör.
 * "₺149,99" ya da "$4.99") — UI HER ZAMAN bunu gösterir, [amountMicros]/[currencyCode]
 * yalnızca [PriceChangeConfirmationUseCase]'in sayısal karşılaştırması içindir. Bu
 * ayrım, Gereksinim 2.4'ün "UI'da sabit kodlanmış TRY/USD YOK" kısıtını yapısal olarak
 * garanti eder: karşılaştırma mantığı hiçbir zaman bir para birimi sembolünü
 * BİÇİMLENDİRMEZ, yalnızca RevenueCat'in ZATEN yerelleştirdiği metni olduğu gibi taşır.
 */
data class PurchasePriceSnapshot(
    val productIdentifier: String,
    val amountMicros: Long,
    val currencyCode: String,
    val formattedPrice: String,
)

data class PlanPackage(
    val identifier: String,
    val tier: Tier,
    val period: Period,
    val localizedPrice: String,
    val isRecommended: Boolean = false,
    /**
     * Gereksinim 1.5: bu kullanıcı için şu an satın alınabilir bir ücretsiz deneme teklifi
     * var mı (Play Billing tarafında uygunluk zaten filtrelenmiş olarak gelir — bkz.
     * `Package.toPlanPackage()` içindeki `subscriptionOptions?.freeTrial` kontrolü).
     * `false` ise (ör. kullanıcı bu ürünün denemesini daha önce kullandıysa) arayüz
     * "ücretsiz deneme" düğmesini/metnini GÖSTERMEMELİDİR.
     */
    val hasFreeTrialAvailable: Boolean = false,
    /** Ücretsiz deneme süresi (gün); deneme yoksa veya mağaza bildirmediyse `null`. */
    val freeTrialDays: Int? = null,
    /**
     * Gereksinim 2.3/2.4: bu paketin ALTINDAKİ mağaza ürün kimliği (ör.
     * "mook_premium:monthly-v1") — [identifier] RevenueCat'in KENDİ paket kimliğidir
     * ("$rc_monthly" gibi), bu alan ise [EntitlementState.activeProductIdentifier] ile
     * eşleştirmek için kullanılan, mağazadaki GERÇEK üründür.
     */
    val productIdentifier: String = "",
    /**
     * Gereksinim 2.3/2.4: [localizedPrice]'ın ham (mikro birim) karşılığı — 1.000.000
     * mikro birim = 1 tam para birimi (ör. 1 TL, 1 USD). SADECE
     * [PriceChangeConfirmationUseCase]'in sayısal karşılaştırması için vardır; UI ASLA
     * bunu biçimlendirip göstermemelidir (bkz. [localizedPrice]).
     */
    val priceAmountMicros: Long = 0L,
    /** Gereksinim 2.3/2.4: ISO 4217 para birimi kodu (ör. "TRY", "USD") — bkz. [priceAmountMicros]. */
    val priceCurrencyCode: String = "",
)

enum class Period { MONTHLY, YEARLY }

data class PaywallOffer(
    val packages: List<PlanPackage>,
    /** RevenueCat teklif meta verisinden gelen "En Popüler" kademesi (bkz. [OfferingMetadataParser]). */
    val mostPopularTier: Tier? = null,
)

sealed interface PurchaseOutcome {
    data object Success : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data object Pending : PurchaseOutcome

    /**
     * Ertelenmiş (DEFERRED) bir plan değişikliği Google Play tarafından kabul edildi; yeni plan
     * mevcut dönemin sonunda başlayacak. Kullanıcı o tarihe kadar mevcut planını kullanmaya
     * devam eder — bu bir "satın alma başarılı, avantajlar hemen açıldı" durumu DEĞİLDİR.
     *
     * @property effectiveAtMillis Yeni planın başlayacağı an (mevcut dönemin bitişi); bilinmiyorsa `null`.
     */
    data class ChangeScheduled(val effectiveAtMillis: Long?) : PurchaseOutcome

    /**
     * Gereksinim 1.11: eskiden serbest metin taşıyan bu durum artık [BillingError] taşır —
     * sunum katmanı HANGİ hatanın oluştuğuna göre yerelleştirilmiş, spesifik bir mesaj seçer.
     */
    data class Error(val error: BillingError) : PurchaseOutcome
}

/**
 * Provider-neutral billing boundary. Android maps this to RevenueCat; iOS intentionally uses
 * the no-op implementation until the iOS commerce launch is explicitly scoped.
 */
interface SubscriptionRepository {
    val state: StateFlow<EntitlementState>
    suspend fun offerings(): Result<PaywallOffer>
    suspend fun purchase(plan: PlanPackage): PurchaseOutcome
    suspend fun restore(): PurchaseOutcome
    suspend fun logIn(uid: String)
    suspend fun logOut()
    suspend fun refresh()

    /**
     * Sağlayıcı tarafında (RevenueCat) hâlihazırda tanımlanmış olan App User ID (varsa).
     *
     * Gereksinim 1.9: [AccountMergeUseCase][com.mcclabs.mook.domain.account.AccountMergeUseCase]
     * bu değeri, uygulamaya YENİ giriş yapılan Firebase UID ile karşılaştırarak "bu cihazdaki
     * aktif yetkilendirme HANGİ kimliğe ait" sorusunu yanıtlar. Bu değer bir sonraki soğuk
     * başlangıçta da (RevenueCat SDK'sı kendi kimliğini cihazda sakladığından) AYNI kalır —
     * bu sayede "aynı kullanıcının uygulamayı yeniden açması" ile "cihaza GERÇEKTEN farklı bir
     * kimliğin girmesi" birbirinden güvenilir biçimde ayırt edilebilir.
     *
     * RevenueCat henüz yapılandırılmamışsa (ör. [FreeSubscriptionRepository][com.mcclabs.mook.data.billing.FreeSubscriptionRepository])
     * `null` döner — bu durumda hiçbir çakışma tespit edilemez, çünkü karşılaştırılacak bir
     * önceki kimlik yoktur.
     */
    fun currentIdentifiedUserId(): String?

    /**
     * Gereksinim 2.6: [productIdentifiers] içindeki her mağaza ürün kimliği için, BU
     * KULLANICININ deneme/giriş fiyatı teklifine uygun olup olmadığını sorar.
     *
     * RevenueCat'in resmi `checkTrialOrIntroductoryPriceEligibility` API'sini sarar —
     * `Package.subscriptionOptions?.freeTrial`'ın varlığına bakmaktan (bu yalnızca ÜRÜNDE
     * böyle bir teklif TANIMLI olduğunu gösterir, bu KULLANICININ ona uygun olduğunu DEĞİL)
     * KASITLI olarak farklıdır: Android/Play Billing, iOS'un aksine, mağaza katmanında
     * uygunluğu güvenilir biçimde filtrelemez — bu yüzden "yalnızca ilk kez abone olanlara"
     * kısıtı ancak bu açık sorguyla doğru uygulanabilir (Gereksinim 2.6: teklif
     * kanibalizasyonunu önler, zaten abone olmuş/olmuş bir kullanıcı giriş fiyatını tekrar
     * GÖRMEZ).
     *
     * Dönen harita, sorgulanan HER kimlik için bir giriş içerir; RevenueCat henüz
     * yapılandırılmamışsa (ör. [FreeSubscriptionRepository][com.mcclabs.mook.data.billing.FreeSubscriptionRepository])
     * ya da sorgu başarısız olursa TÜM değerler güvenli varsayılan olan `false`'a düşer —
     * belirsizlikte teklif GÖSTERİLMEZ, çünkü bir uygun kullanıcıya teklifi kaçırmak,
     * uygun OLMAYAN birine yanlışlıkla göstermekten çok daha az zararlıdır.
     */
    suspend fun introEligibility(productIdentifiers: List<String>): Map<String, Boolean>

    /**
     * Gereksinim 2.7: bu kullanıcı şu an bir Win-Back (geri kazanım) indirimi görmeye
     * uygun mu? Asıl yaptırım her zaman sunucudadır (bkz. `checkWinBackEligibility`
     * Cloud Function'ı ve `functions/src/monetization.ts`'teki 90 günlük soğuma kuralı)
     * — bu yalnızca o sonucu istemciye taşır.
     */
    suspend fun winBackEligibility(): Result<WinBackEligibility>

    /**
     * Gereksinim 2.7: kullanıcı bir Win-Back indirimini FİİLEN kullandığında (satın alma
     * TAMAMLANDIĞINDA) çağrılır — 90 günlük soğuma süresi BUNDAN itibaren başlar. Yalnızca
     * [winBackEligibility]'yi SORMAK bu süreyi ASLA başlatmaz (bkz. sunucu tarafı KDoc'u).
     */
    suspend fun recordWinBackRedemption(): Result<Unit>

    /**
     * Gereksinim 1 (Faz 4): [deviceId] (bkz. `platformDeviceIdentifier()`) üzerinde daha önce
     * (bu hesapla YA DA aynı fiziksel cihazdaki BAŞKA bir hesapla) bir deneme/giriş fiyatı
     * teklifi TÜKETİLMEDİYSE `true` döner.
     *
     * [introEligibility] (RevenueCat'in `checkTrialOrIntroductoryPriceEligibility`'si) yalnızca
     * ŞU AN aktif App User ID'yi (yani BU hesabı) bilir — aynı fiziksel cihazda yeni bir
     * e-postayla YENİ bir hesap açan bir kullanıcı mağaza düzeyinde hâlâ "uygun" görünebilir.
     * Bu metod, çoklu hesap deneme suistimalini (Gereksinim 2.7 & 2.8) önlemek için o boşluğu
     * kapatan, sunucu tarafında yetkili bir CİHAZ düzeyinde kontrol ekler. Belirsizlik
     * durumunda (ağ hatası vb.) GÜVENLİ tarafta kalınır: `false` döner — deneme metni
     * GİZLENİR (bkz. [introEligibility] KDoc'undaki aynı ilke).
     */
    suspend fun deviceTrialEligibility(deviceId: String): Boolean

    /**
     * Gereksinim 1 (Faz 4): bu cihazda bir deneme/giriş fiyatı teklifiyle satın alma
     * TAMAMLANDIĞINDA çağrılır — [deviceId] sunucudaki cihaz defterine (device ledger)
     * kalıcı olarak işlenir, böylece bu cihazdaki SONRAKİ hesaplar (varsa) artık
     * [deviceTrialEligibility] üzerinden uygun görünmez.
     */
    suspend fun recordDeviceTrialConsumption(deviceId: String)

    /**
     * Immediate Authorization Fallback (Altyapı Gereksinimi): RevenueCat webhook gecikmesi
     * yarış koşuluna karşı SON ÇARE doğrulaması. Sunucu (`resolveTierFor`,
     * `functions/src/monetization.ts`) planı ÖNCE Firebase Auth özel talebinden
     * (`revenueCatEntitlements`), o FREE dönerse `customers/{uid}` belgesinden okur — İKİSİ
     * DE yalnızca RevenueCat'in ASENKRON webhook'u işlendikten SONRA güncellenir. Bir satın
     * almanın HEMEN ardından gelen bir sunucu eylemi (`activateBoost`, `setIncognito` vb.)
     * bu iki katmanı da hâlâ eski (bayat) görebilir ve kullanıcı YANLIŞLIKLA reddedilir.
     *
     * Çağıran taraf (tipik olarak bir sunucu eylemi `upgrade-required`/`permission-denied`
     * ile reddedildiğinde) bunu SON ÇARE olarak çağırır: hem özel talep hem de belge
     * [EntitlementState.tier] ile HÂLÂ tutarsızsa, RevenueCat'in KENDİ REST API'sine
     * (`/v1/subscribers/{app_user_id}`) sunucu tarafında DOĞRUDAN sorup ANINDA doğrulayan
     * `verifyEntitlementNow` Cloud Function'ını tetikler; o da sonucu `customers/{uid}`e
     * YAZARAK webhook'un asıl işini erkenden tamamlar.
     *
     * @return Doğrulanmış yeni [Tier], ya da doğrulamaya GEREK YOKTU (zaten güncel) ya da
     *   doğrulama BAŞARISIZ olduysa `null`. Bu fonksiyon ASLA bir reddi kendiliğinden bir
     *   onaya çevirmez — yalnızca GERÇEKTEN bayat bir reddi düzeltir; `null` dönerse çağıran
     *   taraf normal ret akışına devam etmelidir.
     */
    suspend fun verifyEntitlementNow(): Tier?
}

/**
 * Gereksinim 2.7: [SubscriptionRepository.winBackEligibility] sonucu.
 */
data class WinBackEligibility(
    val isEligible: Boolean,
    /** Uygun DEĞİLSE soğuma süresinin biteceği an (epoch ms); uygunsa `null`. */
    val cooldownEndsAtMillis: Long? = null,
)

/**
 * Dönem sonuna ertelenmiş (DEFERRED) plan değişikliği. Google Play bekleyen ürünü
 * `CustomerInfo` içinde bildirmediğinden, değişiklik planlandığı anda cihazda saklanır ve
 * gerçekleşene (ya da geçerliliğini yitirene) kadar Ayarlar'da gösterilir.
 */
data class ScheduledPlanChange(val targetTier: Tier, val effectiveAtMillis: Long?) {

    /**
     * Değişiklik hâlâ bekliyor mu? Kademe hedefe ulaştıysa, abonelik tamamen bittiyse (FREE) veya
     * yürürlük tarihinin üzerinden [GRACE_MILLIS] geçtiyse artık beklemiyordur.
     */
    fun isStillPending(currentTier: Tier, nowMillis: Long): Boolean {
        if (currentTier == targetTier || currentTier == Tier.FREE) return false
        val effectiveAt = effectiveAtMillis ?: return true
        return nowMillis < effectiveAt + GRACE_MILLIS
    }

    companion object {
        /** Mağaza yenilemesinin gecikebileceği süre (3 gün). */
        const val GRACE_MILLIS: Long = 3L * 24 * 60 * 60 * 1000
    }
}
