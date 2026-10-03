package com.mcclabs.mook.feature.paywall

import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.billing.SubscriptionChangePolicy
import com.mcclabs.mook.domain.billing.SubscriptionChange
import com.mcclabs.mook.domain.billing.BillingError
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.analytics.AnalyticsEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.billing.PaywallOffer
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.PurchaseOutcome
import com.mcclabs.mook.domain.billing.RestoreSubscriptionUseCase
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.feature.billing.toLocalizedMessage
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.error_offline
import mook.shared.generated.resources.paywall_change_scheduled
import mook.shared.generated.resources.paywall_change_scheduled_no_date
import mook.shared.generated.resources.paywall_purchase_cancelled
import mook.shared.generated.resources.paywall_purchase_pending
import mook.shared.generated.resources.paywall_purchase_success
import mook.shared.generated.resources.paywall_restore_pending
import mook.shared.generated.resources.paywall_restore_success
import org.jetbrains.compose.resources.getString

/**
 * Paywall paket listesinin yükleme durumu (UDF): Yükleniyor → Hazır | Hata.
 * Hata durumunda arayüz yerelleştirilmiş bir metin ve "Tekrar Dene" düğmesi gösterir.
 */
sealed interface PaywallLoadState {
    data object Loading : PaywallLoadState
    data object Ready : PaywallLoadState

    /** [offline] ise bağlantı yok; değilse RevenueCat ürünleri yükleyemedi. */
    data class Error(val offline: Boolean) : PaywallLoadState
}

data class PaywallUiState(
    val isLoading: Boolean = true,
    /** Paket listesinin yükleme durumu (bkz. [PaywallLoadState]). */
    val loadState: PaywallLoadState = PaywallLoadState.Loading,
    /** Paywall'ı açan limit; başlık buna göre dinamik değişir (bkz. [PaywallHeadline]). */
    val limitReason: LimitReason? = null,
    val offer: PaywallOffer? = null,
    val isPurchasing: Boolean = false,
    /**
     * Gereksinim 1.12: RevenueCat'in `PaymentPendingError`'ı (Play Billing'in bekleyen ödeme
     * yöntemleri — ör. bazı bölgelerdeki nakit/hibrit ödeme seçenekleri) sonucu satın alma
     * onay bekliyor. Sunucu tarafında onaylandığında (bkz. `RevenueCatWebhookService`) ya da
     * kullanıcı uygulamaya dönüp [SubscriptionRepository.refresh]'in ücretli bir kademe
     * getirmesiyle temizlenir. `isPurchasing`'den AYRIDIR: satın alma isteği çoktan
     * tamamlandı (artık "isteniyor" değil, "onay bekleniyor"), ama Satın Al düğmesi aynı
     * ödeme için ikinci bir isteği önlemek üzere yine devre dışı kalmalıdır.
     */
    val isPaymentPending: Boolean = false,
    val message: String? = null,
    val purchaseComplete: Boolean = false,
    val entitlement: EntitlementState = EntitlementState(),
    /**
     * Gereksinim 6 (Faz 6, Tüketici Hukuku): Mesafeli Sözleşmeler Yönetmeliği'nin Cayma
     * Hakkı İstisnası Ön Bilgilendirmesi'nin okunup onaylandığını taşır. Yeni bir satın
     * alma (`canPurchase == true`) başlatılabilmesi İÇİN bu alanın `true` olması ZORUNLUDUR
     * -- hem [PaywallScreen]'deki Satın Al düğmesi hem de [purchase] burada BAĞIMSIZ olarak
     * bunu zorunlu kılar (arayüz durumuna körü körüne güvenilmez). Mevcut bir aboneliği
     * yönetme (Play Store'a yönlendirme) akışını ETKİLEMEZ -- yeni bir dijital içerik
     * ifası başlatmaz.
     */
    val distanceSellingContractAccepted: Boolean = false,
)

class PaywallViewModel(
    private val subscriptions: SubscriptionRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val restoreSubscriptionUseCase: RestoreSubscriptionUseCase,
    /** Paywall ve satın alma adımlarının analitiği (`paywall_view`, `purchase_*`). */
    private val analytics: AnalyticsRepository,
) : ViewModel() {
    private var paywallViewTracked = false
    private val mutableState = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            subscriptions.state.collect { entitlement ->
                mutableState.update { current ->
                    // Gereksinim 1.12: bekleyen bir ödeme, arka planda (RevenueCatWebhookService'in
                    // tetiklediği bir senkronizasyon ya da uygulamaya dönüşte otomatik `refresh()`
                    // ile) ücretli bir kademeye çözüldüyse Satın Al düğmesinin kilidini burada aç —
                    // ayrı bir "onay bildirimi izle" akışına gerek yok, tek doğruluk kaynağı zaten
                    // bu StateFlow.
                    val pendingResolved = current.isPaymentPending && entitlement.isResolved && entitlement.tier != com.mcclabs.mook.domain.billing.Tier.FREE
                    current.copy(
                        entitlement = entitlement,
                        isPaymentPending = if (pendingResolved) false else current.isPaymentPending,
                    )
                }
            }
        }
        load()
    }

    fun load() = viewModelScope.launch {
        // Gereksinim 1.2: planlar zaten yüklüyse (ör. önbellekten) offline'da eski listeyi
        // göstermeye devam ederiz; yalnızca ağdan taze veri denemesini atlarız.
        if (!connectivityObserver.isOnline.value) {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    loadState = if (it.offer != null) PaywallLoadState.Ready else PaywallLoadState.Error(offline = true),
                )
            }
            return@launch
        }
        mutableState.update { it.copy(isLoading = true, loadState = PaywallLoadState.Loading, message = null) }
        subscriptions.refresh()
        subscriptions.offerings().fold(
            onSuccess = { offer ->
                mutableState.update { it.copy(isLoading = false, offer = offer, loadState = PaywallLoadState.Ready, message = null) }
            },
            onFailure = {
                // Ham SDK hata metni (İngilizce) kullanıcıya GÖSTERİLMEZ; arayüz yerelleştirilmiş
                // metni ve "Tekrar Dene" düğmesini [PaywallLoadState.Error] üzerinden çizer.
                mutableState.update { it.copy(isLoading = false, loadState = PaywallLoadState.Error(offline = false)) }
            },
        )
    }

    fun purchase(plan: PlanPackage) = viewModelScope.launch {
        // Gereksinim 1.12: hem aktif bir istek sürerken hem de önceki bir istek hâlâ onay
        // bekliyorken ikinci bir satın alma isteğini engelle.
        if (mutableState.value.isPurchasing || mutableState.value.isPaymentPending) return@launch
        // Gereksinim 6 (Faz 6): Cayma Hakkı İstisnası onaylanmadan sunucuya/SDK'ya HİÇBİR
        // satın alma isteği gönderilmez -- düğmenin devre dışı bırakılması yalnızca bir UX
        // kolaylığıdır, asıl zorlama burada (SOLID: ViewModel arayüz durumuna güvenmez).
        if (!mutableState.value.distanceSellingContractAccepted) return@launch
        if (!connectivityObserver.isOnline.value) {
            mutableState.value = mutableState.value.copy(message = getString(Res.string.error_offline))
            return@launch
        }
        mutableState.value = mutableState.value.copy(isPurchasing = true, message = null)
        val sku = plan.analyticsSku()
        val changeType = changeTypeFor(plan)
        analytics.track(AnalyticsEvent.PurchaseStarted(sku, plan.tier, mutableState.value.entitlement.tier, changeType))
        val outcome = subscriptions.purchase(plan)
        analytics.track(
            when (outcome) {
                PurchaseOutcome.Success -> AnalyticsEvent.PurchaseCompleted(sku, plan.tier, changeType)
                PurchaseOutcome.Cancelled -> AnalyticsEvent.PurchaseCancelled(sku)
                PurchaseOutcome.Pending -> AnalyticsEvent.PurchasePending(sku)
                is PurchaseOutcome.ChangeScheduled -> AnalyticsEvent.PurchaseChangeScheduled(sku, plan.tier)
                is PurchaseOutcome.Error -> AnalyticsEvent.PurchaseFailed(sku, outcome.error.analyticsName())
            },
        )
        when (outcome) {
            PurchaseOutcome.Success -> mutableState.value = mutableState.value.copy(isPurchasing = false, isPaymentPending = false, purchaseComplete = true, message = getString(Res.string.paywall_purchase_success))
            PurchaseOutcome.Cancelled -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = getString(Res.string.paywall_purchase_cancelled))
            PurchaseOutcome.Pending -> {
                // Gereksinim 1.12: "Bekliyor" bir hata değildir — Satın Al düğmesi ayrı bir
                // `isPaymentPending` bayrağıyla devre dışı bırakılmaya devam eder; aksi halde
                // (eski davranışta olduğu gibi `isPurchasing = false` tek başına) kullanıcı
                // hâlâ onay bekleyen bir satın alma varken düğmeye tekrar basıp aynı ödeme
                // için ikinci bir satın alma isteği başlatabilirdi.
                mutableState.value = mutableState.value.copy(isPurchasing = false, isPaymentPending = true, message = getString(Res.string.paywall_purchase_pending))
            }
            is PurchaseOutcome.ChangeScheduled -> {
                // Ertelenmiş plan değişikliği: avantajlar HENÜZ değişmedi, bu yüzden kutlama
                // ekranı (purchaseComplete) gösterilmez; yalnızca geçerlilik tarihi bildirilir.
                val message = outcome.effectiveAtMillis
                    ?.let { getString(Res.string.paywall_change_scheduled, it.formatSubscriptionDate()) }
                    ?: getString(Res.string.paywall_change_scheduled_no_date)
                mutableState.value = mutableState.value.copy(isPurchasing = false, message = message)
            }
            is PurchaseOutcome.Error -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = outcome.error.toLocalizedMessage())
        }
    }

    fun restore() = viewModelScope.launch {
        if (!connectivityObserver.isOnline.value) {
            mutableState.value = mutableState.value.copy(message = getString(Res.string.error_offline))
            return@launch
        }
        mutableState.value = mutableState.value.copy(isPurchasing = true, message = null)
        analytics.track(AnalyticsEvent.PurchaseRestoreStarted)
        // Gereksinim 2.1: restore mantığı artık RestoreSubscriptionUseCase'de tek bir yerde.
        val outcome = restoreSubscriptionUseCase()
        analytics.track(
            AnalyticsEvent.PurchaseRestoreFinished(
                when (outcome) {
                    PurchaseOutcome.Success -> "completed"
                    PurchaseOutcome.Cancelled -> "cancelled"
                    PurchaseOutcome.Pending -> "pending"
                    is PurchaseOutcome.ChangeScheduled -> "scheduled"
                    is PurchaseOutcome.Error -> "failed"
                },
            ),
        )
        when (outcome) {
            PurchaseOutcome.Success -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = getString(Res.string.paywall_restore_success))
            PurchaseOutcome.Cancelled -> mutableState.value = mutableState.value.copy(isPurchasing = false)
            PurchaseOutcome.Pending -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = getString(Res.string.paywall_restore_pending))
            // Geri yükleme bir plan değişikliği planlamaz; tamlık için başarı gibi ele alınır.
            is PurchaseOutcome.ChangeScheduled -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = getString(Res.string.paywall_restore_success))
            is PurchaseOutcome.Error -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = outcome.error.toLocalizedMessage())
        }
    }

    /**
     * Paywall ekranı ilk kez görüntülendiğinde çağrılır; aynı ViewModel ömründe yalnızca bir
     * `paywall_view` olayı gönderilir (yeniden oluşturma/döndürme tekrar saymaz).
     */
    fun onPaywallViewed(source: String, limitReason: LimitReason? = null) {
        mutableState.update { it.copy(limitReason = limitReason) }
        if (paywallViewTracked) return
        paywallViewTracked = true
        analytics.track(AnalyticsEvent.PaywallView(source, mutableState.value.entitlement.tier))
    }

    /** Analitikte kullanılan ürün kimliği: mağaza ürün kimliği, yoksa RevenueCat paket kimliği. */
    private fun PlanPackage.analyticsSku(): String = productIdentifier.ifBlank { identifier }

    /** Satın almanın türü: new / upgrade / downgrade / crossgrade / not_allowed. */
    private fun changeTypeFor(plan: PlanPackage): String {
        val current = mutableState.value
        return when (val change = SubscriptionChangePolicy.evaluate(current.entitlement, plan, current.offer?.packages.orEmpty())) {
            SubscriptionChange.NewPurchase -> "new"
            is SubscriptionChange.Replace -> change.type.name.lowercase()
            is SubscriptionChange.NotAllowed -> "not_allowed"
        }
    }

    /** Hata türünün analitik için kısa adı (ör. "network_error"). */
    private fun BillingError.analyticsName(): String =
        (this::class.simpleName ?: "unknown").replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

    fun dismissPurchaseComplete() {
        mutableState.update { it.copy(purchaseComplete = false) }
    }

    /**
     * Gereksinim 6 (Faz 6): [PaywallScreen]'deki zorunlu onay kutusunun tek durum kaynağı.
     * StateFlow tabanlı, ViewModel'de tutulur -- ekran dönüşünde/yeniden oluşturmada
     * (Compose recomposition) kaybolmaz.
     */
    fun setDistanceSellingContractAccepted(accepted: Boolean) {
        mutableState.update { it.copy(distanceSellingContractAccepted = accepted) }
    }
}
