package com.mcclabs.mook.data.billing

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.mcclabs.mook.domain.billing.DefaultPlanLimits
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.PaywallOffer
import com.mcclabs.mook.domain.billing.Period
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.PurchaseOutcome
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.Period as RevenueCatPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** The currently resumed Compose host. Purchases cannot be launched from an Application context. */
object RevenueCatActivityHolder {
    var activity: Activity? = null
}

actual fun createPlatformSubscriptionRepository(): SubscriptionRepository = AndroidRevenueCatSubscriptionRepository()

private class AndroidRevenueCatSubscriptionRepository : SubscriptionRepository {
    private val mutableState = MutableStateFlow(EntitlementState())
    override val state: StateFlow<EntitlementState> = mutableState.asStateFlow()
    private var packagesByIdentifier: Map<String, Package> = emptyMap()

    init {
        if (Purchases.isConfigured) {
            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { updateState(it) }
        }
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
                val plans = offering.availablePackages.mapNotNull { pkg -> pkg.toPlanPackage() }
                continuation.resume(Result.success(PaywallOffer(plans)))
            }

            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                continuation.resume(Result.failure(IllegalStateException(error.message)))
            }
        })
    }

    override suspend fun purchase(plan: PlanPackage): PurchaseOutcome {
        val activity = RevenueCatActivityHolder.activity ?: return PurchaseOutcome.Error("Open the paywall while the app is active.")
        val pkg = packagesByIdentifier[plan.identifier] ?: return PurchaseOutcome.Error("This plan is no longer available. Refresh and try again.")
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                PurchaseParams.Builder(activity, pkg).build(),
                object : PurchaseCallback {
                    override fun onCompleted(
                        storeTransaction: com.revenuecat.purchases.models.StoreTransaction,
                        customerInfo: CustomerInfo,
                    ) {
                        updateState(customerInfo)
                        refreshFirebaseIdToken()
                        continuation.resume(PurchaseOutcome.Success)
                    }

                    override fun onError(error: com.revenuecat.purchases.PurchasesError, userCancelled: Boolean) {
                        continuation.resume(
                            when {
                                userCancelled -> PurchaseOutcome.Cancelled
                                error.code == PurchasesErrorCode.PaymentPendingError -> PurchaseOutcome.Pending
                                else -> PurchaseOutcome.Error(error.message)
                            },
                        )
                    }
                },
            )
        }
    }

    override suspend fun restore(): PurchaseOutcome = suspendCancellableCoroutine { continuation ->
        if (!Purchases.isConfigured) {
            continuation.resume(PurchaseOutcome.Error("RevenueCat is not configured"))
            return@suspendCancellableCoroutine
        }
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                updateState(customerInfo)
                refreshFirebaseIdToken()
                continuation.resume(PurchaseOutcome.Success)
            }
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                continuation.resume(PurchaseOutcome.Error(error.message))
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

    override suspend fun logOut() {
        if (Purchases.isConfigured) Purchases.sharedInstance.logOut(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) { mutableState.value = EntitlementState() }
            override fun onError(error: com.revenuecat.purchases.PurchasesError) { mutableState.value = EntitlementState() }
        })
        else mutableState.value = EntitlementState()
    }

    override suspend fun refresh() = suspendCancellableCoroutine { continuation ->
        if (!Purchases.isConfigured) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) { updateState(customerInfo); continuation.resume(Unit) }
            override fun onError(error: com.revenuecat.purchases.PurchasesError) { continuation.resume(Unit) }
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
        return PlanPackage(identifier, tier, period, product.price.formatted, tier == Tier.STANDARD)
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
            expiresAtMillis = entitlement?.expirationDate?.time,
            willRenew = entitlement?.willRenew ?: false,
            inTrial = entitlement?.periodType == PeriodType.TRIAL,
            limits = DefaultPlanLimits.byTier.getValue(tier),
        )
    }

    private fun refreshFirebaseIdToken() {
        // The sync webhook is asynchronous. A forced refresh is harmless if it lands
        // just before the webhook, and makes the new server-signed claim available as
        // soon as Firebase has written it.
        FirebaseAuth.getInstance().currentUser?.getIdToken(true)
    }
}
