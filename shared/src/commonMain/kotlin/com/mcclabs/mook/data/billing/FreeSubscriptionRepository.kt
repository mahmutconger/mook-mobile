package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.DefaultPlanLimits
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.PaywallOffer
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.PurchaseOutcome
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Safe fallback until the RevenueCat Android project, products and API key are provisioned. */
class FreeSubscriptionRepository : SubscriptionRepository {
    private val mutableState = MutableStateFlow(EntitlementState())
    override val state: StateFlow<EntitlementState> = mutableState.asStateFlow()
    override suspend fun offerings(): Result<PaywallOffer> = Result.failure(IllegalStateException("Billing is not configured"))
    override suspend fun purchase(plan: PlanPackage): PurchaseOutcome = PurchaseOutcome.Error("Billing is not configured")
    override suspend fun restore(): PurchaseOutcome = PurchaseOutcome.Error("Billing is not configured")
    override suspend fun logIn(uid: String) = Unit
    override suspend fun logOut() { mutableState.value = EntitlementState(Tier.FREE, limits = DefaultPlanLimits.byTier.getValue(Tier.FREE)) }
    override suspend fun refresh() = Unit
}
