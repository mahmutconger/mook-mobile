package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.ScheduledPlanChange
import com.mcclabs.mook.domain.billing.PlanCatalogRepository
import com.mcclabs.mook.domain.billing.OfferingMetadataParser
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.mcclabs.mook.domain.billing.BillingError
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.PaywallOffer
import com.mcclabs.mook.domain.billing.Period
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.PurchaseOutcome
import com.mcclabs.mook.domain.billing.PurchasePriceSnapshot
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.ReplacementPolicy
import com.mcclabs.mook.domain.billing.SubscriptionChange
import com.mcclabs.mook.domain.billing.SubscriptionChangePolicy
import com.mcclabs.mook.domain.billing.SubscriptionSource
import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.data.appFirestore
import com.mcclabs.mook.domain.appcheck.AppCheckCallResult
import com.mcclabs.mook.data.appcheck.createLimitedUseAppCheckCallableInvoker
import com.mcclabs.mook.domain.appcheck.LimitedUseAppCheckCallableInvoker
import com.mcclabs.mook.domain.billing.EntitlementFallbackVerifier
import com.mcclabs.mook.util.Log
import com.mcclabs.mook.domain.billing.WinBackEligibility
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.util.getCurrentTimeMillis
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.Store
import com.revenuecat.purchases.models.StoreReplacementMode
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.Period as RevenueCatPeriod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.Serializable

/** The currently resumed Compose host. Purchases cannot be launched from an Application context. */
object RevenueCatActivityHolder {
    var activity: Activity? = null
}

actual fun createPlatformSubscriptionRepository(planCatalog: PlanCatalogRepository): SubscriptionRepository =
    AndroidRevenueCatSubscriptionRepository(
        planCatalog = planCatalog,
        cachedTierStore = CachedTierStore { FirebaseApp.getInstance().applicationContext },
        // Gereksinim 2 (Faz 6): SON CARE dogrulamasi icin tek kullanimlik App Check
        // jetonu -- bkz. LimitedUseAppCheckCallableInvoker KDoc'u.
        limitedUseInvoker = createLimitedUseAppCheckCallableInvoker(),
    )

/**
 * Son bilinen (doğrulanmış) abonelik kademesini cihazda saklar.
 *
 * Gereksinim 1.2 (CRITICAL): Soğuk başlangıçta RevenueCat'in `CustomerInfo` ağ çağrısı
 * başarısız olursa, istemci burada saklanan son bilinen kademeye döner ve DAHA ÖNCE ÜCRETLİ
 * bir kullanıcıyı ASLA FREE'ye düşürmez (bu, kullanıcıyı yanlışlıkla reklamlara maruz bırakır).
 * `commit()` kullanılır (`apply()` değil) çünkü bu, sonraki soğuk başlangıçta okunacak kritik
 * bir güvenlik ağıdır — aynı desen `data/sso/PlatformSsoSecurity.android.kt`'de kullanılır.
 */
private class CachedTierStore(contextProvider: () -> Context) {
    private val prefs: SharedPreferences by lazy {
        contextProvider().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun save(tier: Tier, expiresAtMillis: Long?, willRenew: Boolean, inTrial: Boolean) {
        val editor = prefs.edit()
            .putString(KEY_TIER, tier.name)
            .putBoolean(KEY_WILL_RENEW, willRenew)
            .putBoolean(KEY_IN_TRIAL, inTrial)
            .putLong(KEY_CACHED_AT, getCurrentTimeMillis())
        if (expiresAtMillis != null) editor.putLong(KEY_EXPIRES_AT, expiresAtMillis) else editor.remove(KEY_EXPIRES_AT)
        editor.commit()
    }

    fun read(): CachedTier? = runCatching {
        val tierName = prefs.getString(KEY_TIER, null) ?: return@runCatching null
        val tier = Tier.valueOf(tierName)
        CachedTier(
            tier = tier,
            expiresAtMillis = if (prefs.contains(KEY_EXPIRES_AT)) prefs.getLong(KEY_EXPIRES_AT, 0L) else null,
            willRenew = prefs.getBoolean(KEY_WILL_RENEW, false),
            inTrial = prefs.getBoolean(KEY_IN_TRIAL, false),
            cachedAtMillis = prefs.getLong(KEY_CACHED_AT, 0L),
        )
    }.getOrNull()

    /**
     * Gereksinim 2.3/2.4: bu cihazda EN SON BİZZAT yapılan satın almanın anlık
     * görüntüsünü kalıcı olarak saklar — [PriceChangeConfirmationUseCase]'in "o zaman
     * ödediğin fiyat" referansı budur. [save]'den BİLEREK AYRI tutulur: [save] her canlı
     * `CustomerInfo` yenilemesinde çağrılır, ama satın alma fiyatı yalnızca GERÇEK bir
     * satın alma ANINDA değişmelidir — her yenilemede üzerine yazılırsa referans anlamını
     * yitirir.
     */
    fun savePurchasePrice(snapshot: PurchasePriceSnapshot) {
        prefs.edit()
            .putString(KEY_PURCHASE_PRODUCT_ID, snapshot.productIdentifier)
            .putLong(KEY_PURCHASE_PRICE_MICROS, snapshot.amountMicros)
            .putString(KEY_PURCHASE_PRICE_CURRENCY, snapshot.currencyCode)
            .putString(KEY_PURCHASE_PRICE_FORMATTED, snapshot.formattedPrice)
            .apply()
    }

    /** [savePurchasePrice] ile kaydedilen son satın alma anlık görüntüsünü okur, ya da hiç yoksa `null`. */
    fun readPurchasePrice(): PurchasePriceSnapshot? = runCatching {
        val productId = prefs.getString(KEY_PURCHASE_PRODUCT_ID, null) ?: return@runCatching null
        if (!prefs.contains(KEY_PURCHASE_PRICE_MICROS)) return@runCatching null
        val currency = prefs.getString(KEY_PURCHASE_PRICE_CURRENCY, null) ?: return@runCatching null
        val formatted = prefs.getString(KEY_PURCHASE_PRICE_FORMATTED, null) ?: return@runCatching null
        PurchasePriceSnapshot(
            productIdentifier = productId,
            amountMicros = prefs.getLong(KEY_PURCHASE_PRICE_MICROS, 0L),
            currencyCode = currency,
            formattedPrice = formatted,
        )
    }.getOrNull()

    /** Çıkış yapıldığında çağrılır: bir sonraki kullanıcı ASLA önceki kullanıcının önbelleğinden kademe devralmamalıdır. */
    /** Dönem sonuna ertelenmiş plan değişikliğini saklar. */
    fun saveScheduledChange(change: ScheduledPlanChange) {
        val editor = prefs.edit().putString(KEY_SCHEDULED_TIER, change.targetTier.name)
        val effectiveAt = change.effectiveAtMillis
        if (effectiveAt != null) editor.putLong(KEY_SCHEDULED_AT, effectiveAt) else editor.remove(KEY_SCHEDULED_AT)
        editor.apply()
    }

    fun readScheduledChange(): ScheduledPlanChange? = runCatching {
        val tierName = prefs.getString(KEY_SCHEDULED_TIER, null) ?: return@runCatching null
        ScheduledPlanChange(
            targetTier = Tier.valueOf(tierName),
            effectiveAtMillis = if (prefs.contains(KEY_SCHEDULED_AT)) prefs.getLong(KEY_SCHEDULED_AT, 0L) else null,
        )
    }.getOrNull()

    fun clearScheduledChange() {
        prefs.edit().remove(KEY_SCHEDULED_TIER).remove(KEY_SCHEDULED_AT).apply()
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val PREFS_NAME = "mook_cached_tier"
        const val KEY_TIER = "tier"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_WILL_RENEW = "will_renew"
        const val KEY_IN_TRIAL = "in_trial"
        const val KEY_CACHED_AT = "cached_at"
        const val KEY_SCHEDULED_TIER = "scheduled_change_tier"
        const val KEY_SCHEDULED_AT = "scheduled_change_effective_at"
        const val KEY_PURCHASE_PRODUCT_ID = "purchase_price_product_id"
        const val KEY_PURCHASE_PRICE_MICROS = "purchase_price_micros"
        const val KEY_PURCHASE_PRICE_CURRENCY = "purchase_price_currency"
        const val KEY_PURCHASE_PRICE_FORMATTED = "purchase_price_formatted"
    }
}

private data class CachedTier(
    val tier: Tier,
    val expiresAtMillis: Long?,
    val willRenew: Boolean,
    val inTrial: Boolean,
    val cachedAtMillis: Long,
)

/**
 * Gereksinim 2.7: `checkWinBackEligibility` callable'ının yanıtı — alan adları
 * `functions/src/monetization.ts`'teki sunucu yanıtıyla BİREBİR eşleşmelidir.
 */
@Serializable
private data class WinBackEligibilityResponse(
    val eligible: Boolean,
    val cooldownEndsAtMillis: Long? = null,
)

/**
 * Gereksinim 1 (Faz 4): `checkDeviceTrialEligibility`/`recordDeviceTrialConsumption`
 * callable'larının istek gövdesi — alan adı `functions/src/monetization.ts`'teki sunucu
 * imzasıyla BİREBİR eşleşmelidir.
 */
@Serializable
private data class DeviceTrialRequest(val deviceId: String)

/** Gereksinim 1 (Faz 4): `checkDeviceTrialEligibility` callable'ının yanıtı. */
@Serializable
private data class DeviceTrialEligibilityResponse(val eligible: Boolean)

private class AndroidRevenueCatSubscriptionRepository(
    /** Plan sınırlarının tek doğruluk kaynağı (`config/plans`). */
    private val planCatalog: PlanCatalogRepository,
    private val cachedTierStore: CachedTierStore,
    private val limitedUseInvoker: LimitedUseAppCheckCallableInvoker,
) : SubscriptionRepository {
    private val mutableState = MutableStateFlow(seedFromCache())
    // Kademe veya `config/plans` değiştiğinde sınırlar yeniden hesaplanır (bkz. withPlanCatalog).
    // `lazy`: `repositoryScope` aşağıda tanımlandığından ilk erişimde kurulur.
    override val state: StateFlow<EntitlementState> by lazy {
        mutableState.asStateFlow().withPlanCatalog(planCatalog.catalog, repositoryScope)
    }
    private var packagesByIdentifier: Map<String, Package> = emptyMap()

    // Gereksinim 1.12: `markPurchasePending()` bir SDK geri çağrısı (`onError`) içinden
    // tetiklenir — orası suspend değildir, bu yüzden Firestore yazması için kendi kapsamını
    // taşır. `SupervisorJob`: bir yazma başarısız olursa (ağ sorunu vb.) bu, aynı kapsamdaki
    // başka bir işi ya da repository'nin kendisini asla iptal etmemelidir.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        if (Purchases.isConfigured) {
            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { updateState(it) }
        }
    }

    /**
     * Soğuk başlangıçta önbellekten doldurur. Önbellek varsa `isResolved = true` ile döner
     * (bilinen bir kademe var — ağ cevabı beklemeden ads/limits doğru gösterilebilir).
     * Önbellek yoksa (hiç satın alma yapılmamış yeni bir kurulum), varsayılan `EntitlementState()`
     * (`FREE`, `isResolved = false`) korunur — bu güvenli taraftadır: reklamlar bilinmeyen plan
     * durumunda gizli kalır, "hayali" bir FREE kararı ASLA doğrulanmış gibi sunulmaz.
     */
    private fun seedFromCache(): EntitlementState {
        val cached = cachedTierStore.read() ?: return EntitlementState()
        return EntitlementState(
            tier = cached.tier,
            isResolved = true,
            expiresAtMillis = cached.expiresAtMillis,
            willRenew = cached.willRenew,
            inTrial = cached.inTrial,
            limits = planCatalog.catalog.value.limitsFor(cached.tier),
            scheduledChange = cachedTierStore.readScheduledChange()
                ?.takeIf { it.isStillPending(cached.tier, getCurrentTimeMillis()) },
            // Gereksinim 2.3/2.4: `activeProductIdentifier` BİLEREK null bırakılır — bu
            // yalnızca CANLI `CustomerInfo`'dan gelir (bkz. `updateState()`); soğuk
            // başlangıçta henüz bilinmez, ama satın alma fiyatı geçmişi (varsa) hâlâ
            // okunabilir.
            lastKnownPurchasePrice = cachedTierStore.readPurchasePrice(),
        )
    }

    override suspend fun offerings(): Result<PaywallOffer> = suspendCancellableCoroutine { continuation ->
        if (!Purchases.isConfigured) {
            continuation.resume(Result.failure(IllegalStateException("RevenueCat is not configured")))
            return@suspendCancellableCoroutine
        }
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                val offering = offerings.getOffering("default") ?: offerings.current
                if (offering == null) {
                    continuation.resume(Result.failure(IllegalStateException("RevenueCat default offering is empty")))
                    return
                }
                packagesByIdentifier = offering.availablePackages.associateBy { it.identifier }
                val candidatePlans = offering.availablePackages.mapNotNull { pkg -> pkg.toPlanPackage() }
                // Gereksinim 2.6: Play Billing ZATEN yalnızca UYGUN kullanıcılara bir deneme/
                // giriş fiyatı teklifi döner (bkz. `toPlanPackage()`deki
                // `subscriptionOptions?.freeTrial` kontrolü) — RevenueCat'in ayrı bir "intro
                // eligibility" sorgusu (`checkTrialOrIntroductoryPriceEligibility`) SDK v8+'ta
                // KALDIRILDIĞINDAN ürün/kullanıcı düzeyinde ekstra bir doğrulamaya artık gerek
                // yoktur. Cihaz düzeyindeki çoklu hesap suistimali kontrolü (Gereksinim 1, Faz 4)
                // `applyDeviceTrialGate` ile hâlâ AYRICA uygulanır — bkz. o metodun KDoc'u.
                // "En Popüler" rozeti koda gömülü değildir: RevenueCat panelindeki teklif meta
                // verisinden okunur (bkz. OfferingMetadataParser şeması).
                val metadata = OfferingMetadataParser.parse(offering.metadata)
                val plans = candidatePlans.map { plan ->
                    plan.copy(isRecommended = metadata.isMostPopular(plan.identifier, plan.tier))
                }
                repositoryScope.launch {
                    val gated = applyDeviceTrialGate(plans)
                    continuation.resume(Result.success(PaywallOffer(gated, mostPopularTier = metadata.badgeTier(gated))))
                }
            }

            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                continuation.resume(Result.failure(IllegalStateException(error.message)))
            }
        })
    }

    /**
     * Gereksinim 1 (Faz 4): [SubscriptionRepository.introEligibility]/
     * `checkTrialOrIntroductoryPriceEligibility` yalnızca RevenueCat'in ŞU AN aktif App User
     * ID'sini (yani BU hesabı) bilir — aynı fiziksel cihazda yeni bir e-postayla YENİ bir hesap
     * açan bir kullanıcı mağaza düzeyinde hâlâ "uygun" görünebilir. Bu metod, çoklu hesap
     * deneme suistimalini (Gereksinim 2.7 & 2.8) önlemek için [platformDeviceIdentifier]'ın
     * sakladığı yerel cihaz kimliğini sunucudaki cihaz defterine sorarak o boşluğu kapatır:
     * [plans] içinde hâlâ `hasFreeTrialAvailable = true` olan bir paket varsa VE bu cihaz
     * daha önce herhangi bir hesapla bir deneme tükettiyse, TÜM paketlerin bayrağı indirilir.
     */
    private suspend fun applyDeviceTrialGate(plans: List<PlanPackage>): List<PlanPackage> {
        if (plans.none { it.hasFreeTrialAvailable }) return plans
        val deviceEligible = runCatching { deviceTrialEligibility(platformDeviceIdentifier()) }.getOrDefault(false)
        if (deviceEligible) return plans
        return plans.map { plan -> if (plan.hasFreeTrialAvailable) plan.copy(hasFreeTrialAvailable = false) else plan }
    }

    override suspend fun deviceTrialEligibility(deviceId: String): Boolean {
        if (deviceId.isBlank()) return false
        return runCatching {
            appHttpsCallable("checkDeviceTrialEligibility")
                .invoke(DeviceTrialRequest(deviceId))
                .data<DeviceTrialEligibilityResponse>()
                .eligible
        }.getOrDefault(false)
    }

    override suspend fun recordDeviceTrialConsumption(deviceId: String) {
        if (deviceId.isBlank()) return
        runCatching {
            appHttpsCallable("recordDeviceTrialConsumption").invoke(DeviceTrialRequest(deviceId))
        }
    }

    /**
     * Gereksinim 2.6: [SubscriptionRepository.introEligibility] arayüz sözleşmesinin
     * bağımsız (tek başına) suspend sürümü — `offerings()` akışının İÇİNDEN değil,
     * herhangi bir çağıran tarafından doğrudan kullanılabilir.
     *
     * RevenueCat'in ayrı "intro eligibility" sorgusu (`checkTrialOrIntroductoryPriceEligibility`)
     * SDK v8+'ta KALDIRILDI: Play Billing artık bir deneme/giriş fiyatı teklifini yalnızca
     * UYGUN kullanıcılara sunuyor, bu yüzden uygunluk `StoreProduct.subscriptionOptions?.freeTrial`
     * alanının VARLIĞIYLA birebir aynıdır — `toPlanPackage()`'daki AYNI kontrol. Bu bilgi yalnızca
     * en son çekilen (`offerings()` ile önbelleğe alınmış) [packagesByIdentifier]'dan okunur; hiçbir
     * ekstra ağ çağrısı yapılmaz. `packagesByIdentifier` henüz doldurulmamışsa (`offerings()` hiç
     * çağrılmamışsa) sonuç güvenli varsayılan olan `false`'a düşer — arayüz KDoc'undaki AYNI ilke.
     */
    override suspend fun introEligibility(productIdentifiers: List<String>): Map<String, Boolean> {
        val ids = productIdentifiers.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val freeTrialByProductId = packagesByIdentifier.values
            .associate { pkg -> pkg.product.id to (pkg.product.subscriptionOptions?.freeTrial != null) }
        return ids.associateWith { id -> freeTrialByProductId[id] ?: false }
    }

    override suspend fun purchase(plan: PlanPackage): PurchaseOutcome {
        val activity = RevenueCatActivityHolder.activity ?: return PurchaseOutcome.Error(BillingError.ActivityUnavailable)
        val pkg = packagesByIdentifier[plan.identifier] ?: return PurchaseOutcome.Error(BillingError.ProductUnavailable)

        // Değiştirme kararı ÖNBELLEKTEN değil, CANLI abonelik durumundan verilmelidir: soğuk
        // başlangıçta aktif ürün/temel plan/kaynak bilinmez ve "ücretsiz" sanılan ücretli bir
        // kullanıcı yeni bir satın alma başlatıp çift abonelik oluşturabilir.
        if (needsLiveEntitlementState(mutableState.value)) refresh()
        val availablePlans = packagesByIdentifier.values.mapNotNull { it.toPlanPackage() }
        val change = SubscriptionChangePolicy.evaluate(mutableState.value, plan, availablePlans)

        val freeTrialOption = (change as? SubscriptionChange.NewPurchase)
            ?.let { pkg.product.subscriptionOptions?.freeTrial }
        val purchaseParams = when (change) {
            is SubscriptionChange.NotAllowed -> return PurchaseOutcome.Error(change.reason.toBillingError())
            // Gereksinim 1.5: yeni bir satın almada kullanıcı hâlâ uygunsa ücretsiz deneme
            // teklifi (SubscriptionOption) hedeflenir; Play uygunluğu zaten kendi tarafında filtreler.
            SubscriptionChange.NewPurchase -> if (freeTrialOption != null) {
                PurchaseParams.Builder(activity, freeTrialOption)
            } else {
                PurchaseParams.Builder(activity, pkg)
            }
            // Plan değişikliği: RevenueCat `oldProductId` ile cihazdaki eski Play satın almasını
            // ve `purchaseToken`'ını bulur, Play'e `SubscriptionUpdateParams` (oldPurchaseToken +
            // replacementMode) olarak iletir — eski abonelik DEĞİŞTİRİLİR, yanına ikincisi açılmaz.
            // Değiştirmede deneme teklifi hedeflenmez: Play, mevcut aboneye deneme vermez.
            is SubscriptionChange.Replace -> PurchaseParams.Builder(activity, pkg)
                .oldProductId(change.oldSubscriptionId)
                .replacementMode(change.policy.toStoreReplacementMode())
        }

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                purchaseParams.build(),
                object : PurchaseCallback {
                    override fun onCompleted(
                        storeTransaction: com.revenuecat.purchases.models.StoreTransaction,
                        customerInfo: CustomerInfo,
                    ) {
                        val isDeferredChange = change is SubscriptionChange.Replace &&
                            change.policy == ReplacementPolicy.DEFERRED
                        // Gereksinim 2.3/2.4: fiyat anlık görüntüsü `updateState()`'DEN
                        // ÖNCE kaydedilir — `updateState()` `lastKnownPurchasePrice`'ı
                        // önbellekten OKUYARAK yeni state'e yazar. Ertelenmiş değişiklikte yeni
                        // fiyat henüz geçerli olmadığından kaydedilmez.
                        if (!isDeferredChange && plan.productIdentifier.isNotBlank() && plan.priceAmountMicros > 0) {
                            cachedTierStore.savePurchasePrice(
                                PurchasePriceSnapshot(
                                    productIdentifier = plan.productIdentifier,
                                    amountMicros = plan.priceAmountMicros,
                                    currencyCode = plan.priceCurrencyCode,
                                    formattedPrice = plan.localizedPrice,
                                ),
                            )
                        }
                        updateState(customerInfo)
                        refreshFirebaseIdToken()
                        // Gereksinim 1 (Faz 4): yalnızca GERÇEKTEN bir deneme teklifiyle yapılan
                        // yeni satın alma cihaz deneme defterine işlenir (fire-and-forget).
                        if (freeTrialOption != null) {
                            repositoryScope.launch { recordDeviceTrialConsumption(platformDeviceIdentifier()) }
                        }
                        continuation.resume(
                            if (isDeferredChange) {
                                // Play, DEFERRED modda ESKİ ürünün işlemini döndürür; mevcut plan
                                // dönem sonuna kadar aktif kalır ve yeni plan o tarihte başlar.
                                run {
                                    // Play bekleyen ürünü CustomerInfo'da bildirmez: planlanan değişiklik
                                    // cihazda saklanır ve Ayarlar'da gerçekleşene kadar gösterilir.
                                    val change = ScheduledPlanChange(plan.tier, mutableState.value.expiresAtMillis)
                                    cachedTierStore.saveScheduledChange(change)
                                    mutableState.value = mutableState.value.copy(scheduledChange = change)
                                    PurchaseOutcome.ChangeScheduled(effectiveAtMillis = change.effectiveAtMillis)
                                }
                            } else {
                                // Anında gerçekleşen bir değişiklik, önceden planlanmış düşürmeyi geçersiz kılar.
                                cachedTierStore.clearScheduledChange()
                                mutableState.value = mutableState.value.copy(scheduledChange = null)
                                PurchaseOutcome.Success
                            },
                        )
                    }

                    override fun onError(error: com.revenuecat.purchases.PurchasesError, userCancelled: Boolean) {
                        continuation.resume(
                            when {
                                userCancelled -> PurchaseOutcome.Cancelled
                                error.code == PurchasesErrorCode.PaymentPendingError -> {
                                    // Gereksinim 1.12: bekleyen ödemeyi onay beklenmeye başladığı
                                    // ANDA sunucuya işaretle (webhook bunu ayrıca ayırt edemez).
                                    markPurchasePending()
                                    PurchaseOutcome.Pending
                                }
                                // RevenueCat, `oldProductId` için bu Google hesabında aktif bir
                                // satın alma (dolayısıyla eski purchaseToken) bulamadığında
                                // `PurchaseInvalidError` döner — ör. abonelik başka bir Google
                                // hesabıyla alınmışsa. Yeni bir satın almaya DÜŞÜLMEZ: bu,
                                // kullanıcıyı iki ayrı abonelik için ödemeye götürürdü.
                                change is SubscriptionChange.Replace &&
                                    error.code == PurchasesErrorCode.PurchaseInvalidError ->
                                    PurchaseOutcome.Error(BillingError.ExistingSubscriptionNotFound)
                                else -> PurchaseOutcome.Error(error.toBillingError())
                            },
                        )
                    }
                },
            )
        }
    }

    /**
     * Plan değişikliği kararı için canlı abonelik bilgisi gerekiyor mu? Ücretli bir kademe
     * önbellekten biliniyor ama aktif ürün/kaynak henüz canlı `CustomerInfo`'dan gelmediyse
     * `true` döner.
     */
    private fun needsLiveEntitlementState(state: EntitlementState): Boolean =
        !state.isResolved ||
            (state.tier != Tier.FREE && (state.activeProductIdentifier == null || state.activeSubscriptionSource == null))

    override suspend fun restore(): PurchaseOutcome = suspendCancellableCoroutine { continuation ->
        if (!Purchases.isConfigured) {
            continuation.resume(PurchaseOutcome.Error(BillingError.BillingUnavailable))
            return@suspendCancellableCoroutine
        }
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                updateState(customerInfo)
                refreshFirebaseIdToken()
                continuation.resume(PurchaseOutcome.Success)
            }
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                continuation.resume(PurchaseOutcome.Error(error.toBillingError()))
            }
        })
    }

    override suspend fun logIn(uid: String) {
        if (!Purchases.isConfigured) return
        Purchases.sharedInstance.logIn(uid, object : LogInCallback {
            override fun onReceived(customerInfo: CustomerInfo, created: Boolean) = updateState(customerInfo)
            override fun onError(error: com.revenuecat.purchases.PurchasesError) = Unit
        })
    }

    /**
     * Gereksinim 2.7: asıl yaptırım sunucudaki `checkWinBackEligibility` Cloud
     * Function'ındadır (bkz. `functions/src/monetization.ts`'teki 90 günlük soğuma
     * kuralı) — bu yalnızca o sonucu taşır.
     */
    override suspend fun winBackEligibility(): Result<WinBackEligibility> = runCatching {
        val response = appHttpsCallable("checkWinBackEligibility").invoke().data<WinBackEligibilityResponse>()
        WinBackEligibility(
            isEligible = response.eligible,
            cooldownEndsAtMillis = response.cooldownEndsAtMillis,
        )
    }

    /** Gereksinim 2.7: 90 günlük soğuma süresini BAŞLATAN sunucu yazma işlemi — bkz. arayüz KDoc'u. */
    override suspend fun recordWinBackRedemption(): Result<Unit> = runCatching {
        appHttpsCallable("recordWinBackRedemption").invoke()
        Unit
    }

    /**
     * Gereksinim 1.9: [AccountMergeUseCase][com.mcclabs.mook.domain.account.AccountMergeUseCase]'in
     * çakışma tespiti için kullandığı, RevenueCat SDK'sının O AN bağlı olduğu App User ID.
     * RevenueCat henüz yapılandırılmamışsa (ör. anahtar sağlanmadan önceki bir derleme) `null`
     * döner — bu durumda hiçbir çakışma varsayılamaz.
     */
    override fun currentIdentifiedUserId(): String? =
        if (Purchases.isConfigured) Purchases.sharedInstance.appUserID else null

    override suspend fun logOut() {
        // Bir sonraki kullanıcı ASLA bu kullanıcının önbellekteki kademesini devralmamalı
        // (ör. Premium kullanıcı çıkış yapıp aynı cihazda Free bir kullanıcı girerse).
        cachedTierStore.clear()
        // Anonim kullanıcıda RevenueCat `logOut()` hata döner (LogOutWithAnonymousUserError);
        // ayrılacak bir kimlik olmadığından çağrı atlanır.
        if (!Purchases.isConfigured || Purchases.sharedInstance.isAnonymous) {
            mutableState.value = EntitlementState(isResolved = true)
            return
        }
        // Katı çıkış sırası (LogoutUseCase): Firebase `signOut()` bu adım GERÇEKTEN bittikten
        // sonra çalışmalıdır — bu yüzden geri çağırma beklenir. Hata durumunda istisna
        // fırlatılır; LogoutUseCase bunu loglar ve çıkışa devam eder.
        suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.logOut(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    mutableState.value = EntitlementState(isResolved = true)
                    continuation.resume(Unit)
                }
                override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                    mutableState.value = EntitlementState(isResolved = true)
                    continuation.resumeWithException(IllegalStateException("RevenueCat çıkışı başarısız: ${error.message}"))
                }
            })
        }
    }

    override suspend fun refresh() = suspendCancellableCoroutine { continuation ->
        if (!Purchases.isConfigured) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) { updateState(customerInfo); continuation.resume(Unit) }
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                // Gereksinim 1.2 (CRITICAL): ağ hatasında hiçbir şey yapmadan bırakmak, o an
                // mutableState'te ne varsa (önbellekten doldurulmuş son bilinen kademe, ya da
                // hiç önbellek yoksa güvenli FREE/isResolved=false varsayılanı) onu korur.
                // Daha önce ücretli bir kullanıcı bu dalda ASLA FREE'ye düşürülmez.
                continuation.resume(Unit)
            }
        })
    }

    private fun Package.toPlanPackage(): PlanPackage? {
        val tier = when {
            product.id.startsWith("mook_premium:") || product.id == "mook_premium" -> Tier.PREMIUM
            product.id.startsWith("mook_standard:") || product.id == "mook_standard" -> Tier.STANDARD
            product.id.startsWith("mook_economy:") || product.id == "mook_economy" -> Tier.ECONOMY
            else -> return null
        }
        val period = when (product.period?.unit) {
            RevenueCatPeriod.Unit.YEAR -> Period.YEARLY
            RevenueCatPeriod.Unit.MONTH -> Period.MONTHLY
            else -> return null
        }
        return PlanPackage(
            identifier = identifier,
            tier = tier,
            period = period,
            localizedPrice = product.price.formatted,
            // Rozet, `offerings()` içinde teklif meta verisinden atanır.
            isRecommended = false,
            hasFreeTrialAvailable = product.subscriptionOptions?.freeTrial != null,
            freeTrialDays = product.subscriptionOptions?.freeTrial?.freePhase?.billingPeriod?.toDays(),
            // Gereksinim 2.3/2.4: PriceChangeConfirmationUseCase'in sayısal karşılaştırması
            // için ham fiyat — UI hiçbir zaman bunları doğrudan biçimlendirip göstermez,
            // her zaman yukarıdaki `localizedPrice`'ı (RevenueCat'in ZATEN yerelleştirdiği
            // metni) kullanır.
            productIdentifier = product.id,
            priceAmountMicros = product.price.amountMicros,
            priceCurrencyCode = product.price.currencyCode,
        )
    }

    private fun updateState(info: CustomerInfo) {
        val entitlement = sequenceOf("premium", "standard", "economy")
            .mapNotNull { info.entitlements.active[it] }
            .firstOrNull()
        val tier = when (entitlement?.identifier) {
            "premium" -> Tier.PREMIUM
            "standard" -> Tier.STANDARD
            "economy" -> Tier.ECONOMY
            else -> Tier.FREE
        }
        mutableState.value = EntitlementState(
            tier = tier,
            isResolved = true,
            expiresAtMillis = entitlement?.expirationDate?.time,
            willRenew = entitlement?.willRenew ?: false,
            inTrial = entitlement?.periodType == PeriodType.TRIAL,
            limits = planCatalog.catalog.value.limitsFor(tier),
            // Ödeme sorunu (RevenueCat tespit eder) ve mağaza yönetim bağlantısı: Ayarlar'daki
            // "Ödeme Yöntemini Güncelle" bağlantısı bunlara göre gösterilir.
            billingIssueDetectedAtMillis = entitlement?.billingIssueDetectedAt?.time,
            managementUrl = info.managementURL?.toString(),
            scheduledChange = pendingScheduledChange(tier),
            // Gereksinim 2.3/2.4: CANLI RevenueCat verisinden gelen, altındaki mağaza
            // ürün kimliği + bu cihazda kayıtlı son satın alma fiyatı (varsa) — her
            // `updateState()` çağrısında yeniden okunur ki `save()`'in ALTINDA
            // saklanmayan bu alan burada KAYBOLMASIN.
            activeProductIdentifier = entitlement?.productIdentifier,
            lastKnownPurchasePrice = cachedTierStore.readPurchasePrice(),
            // Plan değişikliği (SubscriptionChangePolicy) için: Play temel planı ve yetki kaynağı.
            activeBasePlanIdentifier = entitlement?.productPlanIdentifier,
            activeSubscriptionSource = entitlement?.store?.toSubscriptionSource(),
        )
        // Sunucudan (RevenueCat) doğrulanmış her başarılı cevapta önbelleği güncel tut — bir
        // sonraki soğuk başlangıçta ağ başarısız olursa buraya dönülecek.
        cachedTierStore.save(tier, entitlement?.expirationDate?.time, entitlement?.willRenew ?: false, entitlement?.periodType == PeriodType.TRIAL)
    }

    /** Saklanan ertelenmiş değişiklik hâlâ bekliyorsa döner; gerçekleştiyse/geçtiyse temizler. */
    private fun pendingScheduledChange(currentTier: Tier): ScheduledPlanChange? {
        val stored = cachedTierStore.readScheduledChange() ?: return null
        if (stored.isStillPending(currentTier, getCurrentTimeMillis())) return stored
        cachedTierStore.clearScheduledChange()
        return null
    }

    private fun refreshFirebaseIdToken() {
        // The sync webhook is asynchronous. A forced refresh is harmless if it lands
        // just before the webhook, and makes the new server-signed claim available as
        // soon as Firebase has written it.
        FirebaseAuth.getInstance().currentUser?.getIdToken(true)
    }

    /**
     * Immediate Authorization Fallback (Altyapı Gereksinimi): bkz.
     * [SubscriptionRepository.verifyEntitlementNow] KDoc'undaki tam gerekçe. Gerçek okumaları
     * [EntitlementFallbackVerifier]e (saf karar mantığı) enjekte eder; bu fonksiyon yalnızca
     * platforma özgü (Firebase/Firestore/Cloud Functions) bağlantıdır.
     */
    override suspend fun verifyEntitlementNow(): Tier? {
        return try {
            val verifier = EntitlementFallbackVerifier(
                readClaimedTier = ::readClaimedTierFromIdToken,
                readDocumentTier = ::readTierFromCustomerDocument,
                verifyViaRevenueCatRest = ::verifyTierViaRevenueCatRest,
            )
            val verified = verifier.verify(mutableState.value.tier) ?: return null

            // Sunucu `customers/{uid}`i (ve nihayetinde webhook geldiğinde özel talebi) zaten
            // güncelledi (bkz. `verifyEntitlementNow` Cloud Function'ı); istemcinin YENİ
            // kademesini HEMEN yansıtması için yerel durumu da güncelle ve bir sonraki soğuk
            // başlangıç için önbelleğe yaz.
            mutableState.value = mutableState.value.copy(
                tier = verified,
                isResolved = true,
                limits = planCatalog.catalog.value.limitsFor(verified),
            )
            cachedTierStore.save(
                verified,
                mutableState.value.expiresAtMillis,
                mutableState.value.willRenew,
                mutableState.value.inTrial,
            )
            refreshFirebaseIdToken()
            verified
        } catch (e: Exception) {
            Log.e("Anlık yetkilendirme doğrulaması başarısız", e)
            null
        }
    }

    /** Mevcut (ZORLA YENİLENMEMİŞ) Firebase kimlik jetonunun `revenueCatEntitlements` özel
     *  talebinden kademeyi çözer — sunucudaki `resolveTier()` ile BİREBİR aynı alan adı. */
    private suspend fun readClaimedTierFromIdToken(): Tier {
        val user = FirebaseAuth.getInstance().currentUser ?: return Tier.FREE
        return try {
            val result = suspendCancellableCoroutine<com.google.firebase.auth.GetTokenResult> { continuation ->
                user.getIdToken(false)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            tierFromEntitlementClaim(result.claims["revenueCatEntitlements"])
        } catch (e: Exception) {
            Tier.FREE
        }
    }

    /** `customers/{uid}` belgesinden kademeyi çözer — sunucudaki `resolveTierFor()`nin belge
     *  yedeğiyle (`revenueCatEntitlements`/`entitlements`/`activeEntitlements`) BİREBİR aynı
     *  alan sırası. */
    private suspend fun readTierFromCustomerDocument(): Tier {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Tier.FREE
        return try {
            val document = appFirestore.collection("customers").document(uid).get()
            val raw = runCatching { document.get<List<String>>("revenueCatEntitlements") }.getOrNull()
                ?: runCatching { document.get<List<String>>("entitlements") }.getOrNull()
                ?: runCatching { document.get<List<String>>("activeEntitlements") }.getOrNull()
            tierFromEntitlementClaim(raw)
        } catch (e: Exception) {
            Tier.FREE
        }
    }

    /**
     * Son çare: sunucu tarafında RevenueCat'in KENDİ REST API'sine DOĞRUDAN sorup ANINDA
     * doğrulayan callable'ı çağırır — gizli API anahtarı yalnızca sunucuda yaşar.
     *
     * Gereksinim 2 (Faz 6, Kapsamlı App Check Uygulaması): bu SON ÇARE doğrulaması
     * RevenueCat'in gizli REST anahtarını sunucu tarafında tetiklediği için "highly
     * sensitive operation" olarak sınıflandırılır (bkz. görev tanımı). Standart
     * (önbelleğe alınabilen, tekrar kullanılabilen) App Check jetonu YERİNE TEK
     * KULLANIMLIK bir jeton kullanılır -- bkz. [LimitedUseAppCheckCallableInvoker] KDoc'u.
     * Sunucu tarafı (`subscriptionVerification.ts`deki `consumeAppCheckToken: true`) AYNI
     * jetonun İKİNCİ kez kullanılmasını (replay) REDDEDER.
     */
    private suspend fun verifyTierViaRevenueCatRest(): Tier? {
        return try {
            when (val result = limitedUseInvoker.invoke("verifyEntitlementNow")) {
                is AppCheckCallResult.Success -> {
                    val tierRaw = result.value["tier"] as? String
                    tierRaw?.let { runCatching { Tier.valueOf(it) }.getOrNull() }
                }
                AppCheckCallResult.QuotaExceeded -> {
                    // Gereksinim 2: "Include error handling ... if Play Integrity API
                    // limits are exceeded." Bu YALNIZCA son-çare katmanıdır -- SESSİZCE
                    // atlanır, mevcut iki katmanlı (özel talep + belge) okuma zaten
                    // makul bir cevap verdi; kullanıcı ENGELLENMEMELİDİR (non-blocking).
                    Log.e("App Check limited-use jetonu: Play Integrity kotası aşıldı, bu tur atlandı")
                    null
                }
                AppCheckCallResult.AttestationFailed, AppCheckCallResult.Unavailable -> {
                    Log.e("RevenueCat REST doğrulaması: limited-use App Check jetonu alınamadı")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("RevenueCat REST doğrulaması başarısız", e)
            null
        }
    }

    /**
     * Gereksinim 1.12: `users/{uid}.hasPendingPurchase`'ı `true` yapar.
     *
     * `revenuecatWebhook` Cloud Function'ı, bu alan `true` iken gelen bir yetki-aktif
     * olayında (ör. `INITIAL_PURCHASE`) bunun bir BEKLEYEN ödemenin onayı olduğunu anlar,
     * alanı temizler ve kullanıcının cihazına FCM ile yerel bir bildirim tetikler (bkz.
     * `RevenueCatWebhookService`). Yazma başarısız olursa (ör. anlık ağ sorunu) sessizce
     * yutulur — en kötü ihtimalle kullanıcı push bildirimini kaçırır, uygulamaya dönünce
     * `refresh()` zaten doğru durumu gösterir; bu asla satın alma akışını BLOKE etmemelidir.
     */
    private fun markPurchasePending() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        repositoryScope.launch {
            runCatching {
                com.mcclabs.mook.data.appFirestore.collection("users").document(uid)
                    .set(mapOf("hasPendingPurchase" to true), merge = true)
            }
        }
    }
}

/**
 * [com.revenuecat.purchases.PurchasesError]'ı sağlayıcıdan bağımsız [BillingError]'a eşler
 * (Gereksinim 1.11).
 *
 * RevenueCat'in [PurchasesErrorCode]'u, Play Billing'in ham `BILLING_UNAVAILABLE` /
 * `ITEM_ALREADY_OWNED` yanıt kodlarını BİREBİR ayrı birer değer olarak dışarı vermez — SDK
 * bunları kendi soyutlanmış hata kümesine indirger. Bu yüzden eşleme, RevenueCat'in resmi
 * KDoc açıklamalarına göre EN YAKIN karşılığı seçer:
 * - [PurchasesErrorCode.StoreProblemError] ("Mağaza ile ilgili bir sorun oluştu") → Play
 *   Store'a erişilemiyor/yapılandırılmamış durumunun en yakın karşılığı olduğundan
 *   [BillingError.BillingUnavailable]'a eşlenir.
 * - [PurchasesErrorCode.ProductAlreadyPurchasedError] → doğrudan [BillingError.ItemAlreadyOwned].
 * - [PurchasesErrorCode.NetworkError] → doğrudan [BillingError.NetworkError].
 * - [PurchasesErrorCode.OperationAlreadyInProgressError] → doğrudan
 *   [BillingError.OperationInProgress].
 * - Diğer tüm kodlar, ham mesajı koruyan [BillingError.Unknown]'a düşer; böylece hiçbir hata
 *   sessizce yutulmaz, yalnızca özel bir UI dalı kazanmaz.
 */
/**
 * Bir Firebase özel talebinin ya da `customers/{uid}` belgesinin `revenueCatEntitlements`
 * (veya eşdeğer) alanından [Tier] çözer — sunucudaki `tierFromEntitlements()`
 * (`functions/src/monetization.ts`) ile BİREBİR AYNI öncelik sırası (premium > standard >
 * economy > free) ve "boş/tanınmayan -> FREE" güvenli varsayılanı. Değer bir liste (JSON
 * dizisi -> `List<*>`), bir harita (`{"premium": true}` biçimi -> `Map<*, *>`) ya da tek bir
 * dize olabilir; ikisi de sunucu tarafında ÜRETİLEBİLEN biçimlerdir.
 */
private fun tierFromEntitlementClaim(value: Any?): Tier {
    val names: Set<String> = when (value) {
        is List<*> -> value.mapNotNull { it?.toString()?.lowercase() }.toSet()
        is Map<*, *> -> value.entries
            .filter { (_, enabled) -> enabled == true }
            .mapNotNull { it.key?.toString()?.lowercase() }
            .toSet()
        is String -> setOf(value.lowercase())
        else -> emptySet()
    }
    return when {
        "premium" in names -> Tier.PREMIUM
        "standard" in names -> Tier.STANDARD
        "economy" in names -> Tier.ECONOMY
        else -> Tier.FREE
    }
}

/** Alan modelindeki [ReplacementPolicy]'yi RevenueCat'in (Play'e birebir eşlenen) değiştirme moduna çevirir. */
internal fun ReplacementPolicy.toStoreReplacementMode(): StoreReplacementMode = when (this) {
    ReplacementPolicy.IMMEDIATE_WITH_TIME_PRORATION -> StoreReplacementMode.WITH_TIME_PRORATION
    ReplacementPolicy.DEFERRED -> StoreReplacementMode.DEFERRED
}

/** RevenueCat [Store] değerini alan modeline indirger; yalnızca Play aboneliği uygulama içinden değiştirilebilir. */
internal fun Store.toSubscriptionSource(): SubscriptionSource = when (this) {
    Store.PLAY_STORE -> SubscriptionSource.PLAY_STORE
    Store.PROMOTIONAL -> SubscriptionSource.PROMOTIONAL
    else -> SubscriptionSource.OTHER_STORE
}

/** Uygulama içinden yapılamayan bir geçişin sebebini kullanıcıya gösterilecek hataya çevirir. */
internal fun SubscriptionChange.NotAllowed.Reason.toBillingError(): BillingError = when (this) {
    SubscriptionChange.NotAllowed.Reason.ALREADY_ACTIVE -> BillingError.PlanAlreadyActive
    SubscriptionChange.NotAllowed.Reason.ACTIVE_PLAN_UNKNOWN -> BillingError.ActivePlanUnknown
    SubscriptionChange.NotAllowed.Reason.MANAGED_OUTSIDE_PLAY -> BillingError.SubscriptionManagedElsewhere
    SubscriptionChange.NotAllowed.Reason.TARGET_UNAVAILABLE -> BillingError.ProductUnavailable
}

internal fun com.revenuecat.purchases.PurchasesError.toBillingError(): BillingError = when (code) {
    PurchasesErrorCode.StoreProblemError -> BillingError.BillingUnavailable
    PurchasesErrorCode.ProductAlreadyPurchasedError -> BillingError.ItemAlreadyOwned
    PurchasesErrorCode.NetworkError -> BillingError.NetworkError
    PurchasesErrorCode.OperationAlreadyInProgressError -> BillingError.OperationInProgress
    // Gereksinim 2.1: RevenueCat, `restorePurchases()`'ın makbuzu BAŞKA bir App User ID'ye
    // ZATEN bağlı olan bir aboneliğe aktaramadığı (panodaki "Restore Behavior" ayarı buna
    // izin vermediği) durumu bu kodla işaretler — RevenueCat'in kendi dokümantasyonunda bu,
    // "receipt already in use" olarak tarif edilir.
    PurchasesErrorCode.ReceiptAlreadyInUseError -> BillingError.SubscriptionLinkedToAnotherAccount
    else -> BillingError.Unknown(message)
}

/** Mağaza deneme süresini güne çevirir (ay ≈ 30, yıl ≈ 365 gün); bilinmeyen birimde `null`. */
private fun RevenueCatPeriod.toDays(): Int? = when (unit) {
    RevenueCatPeriod.Unit.DAY -> value
    RevenueCatPeriod.Unit.WEEK -> value * 7
    RevenueCatPeriod.Unit.MONTH -> value * 30
    RevenueCatPeriod.Unit.YEAR -> value * 365
    else -> null
}
