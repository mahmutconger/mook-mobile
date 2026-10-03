package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.BillingError
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.PlanCatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.mcclabs.mook.domain.billing.PaywallOffer
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.PurchaseOutcome
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.WinBackEligibility
import com.mcclabs.mook.domain.billing.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Safe fallback until the RevenueCat Android project, products and API key are provisioned. */
class FreeSubscriptionRepository(
    /** Ücretsiz kademenin sınırları da `config/plans`tan gelir. */
    private val planCatalog: PlanCatalogRepository? = null,
) : SubscriptionRepository {
    private val mutableState = MutableStateFlow(EntitlementState(isResolved = true))
    override val state: StateFlow<EntitlementState> = planCatalog
        ?.let { mutableState.asStateFlow().withPlanCatalog(it.catalog, CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
        ?: mutableState.asStateFlow()
    override suspend fun offerings(): Result<PaywallOffer> = Result.failure(IllegalStateException("Billing is not configured"))
    override suspend fun purchase(plan: PlanPackage): PurchaseOutcome = PurchaseOutcome.Error(BillingError.BillingUnavailable)
    override suspend fun restore(): PurchaseOutcome = PurchaseOutcome.Error(BillingError.BillingUnavailable)
    override suspend fun logIn(uid: String) = Unit
    override suspend fun logOut() { mutableState.value = EntitlementState(Tier.FREE, isResolved = true) }
    override suspend fun refresh() = Unit

    // Gereksinim 1.9: RevenueCat burada hiç yapılandırılmadığından karşılaştırılacak bir
    // sağlayıcı kimliği yoktur — bu yüzden AccountMergeUseCase için her zaman `null` döner
    // (yani bu depoyla çakışma ASLA tespit edilmez).
    override fun currentIdentifiedUserId(): String? = null

    // Gereksinim 2.6: RevenueCat yapılandırılmamışken hiçbir ürün için uygunluk
    // BİLİNEMEZ — güvenli varsayılan olan `false`'a düşülür (bkz. arayüz KDoc'u).
    override suspend fun introEligibility(productIdentifiers: List<String>): Map<String, Boolean> =
        productIdentifiers.associateWith { false }

    override suspend fun winBackEligibility(): Result<WinBackEligibility> =
        Result.failure(IllegalStateException("Billing is not configured"))

    override suspend fun recordWinBackRedemption(): Result<Unit> =
        Result.failure(IllegalStateException("Billing is not configured"))

    // Gereksinim 1 (Faz 4): burada hiç satın alma yapılamayacağından bu kontrol zararsızdır;
    // `true` dönmek arayüzü kilitlemez (ticari akış zaten `offerings()`'te başarısız olur).
    override suspend fun deviceTrialEligibility(deviceId: String): Boolean = true

    override suspend fun recordDeviceTrialConsumption(deviceId: String) = Unit

    // RevenueCat burada hiç yapılandırılmadığından doğrulanacak bir sunucu kademesi de
    // yoktur — sunucu eylemi zaten hiçbir zaman satın alma temelli reddedilmez.
    override suspend fun verifyEntitlementNow(): Tier? = null
}
