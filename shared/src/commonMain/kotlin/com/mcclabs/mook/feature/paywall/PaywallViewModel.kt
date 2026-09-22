package com.mcclabs.mook.feature.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcclabs.mook.domain.billing.PaywallOffer
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.PurchaseOutcome
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallUiState(
    val isLoading: Boolean = true,
    val offer: PaywallOffer? = null,
    val isPurchasing: Boolean = false,
    val message: String? = null,
    val purchaseComplete: Boolean = false,
    val entitlement: EntitlementState = EntitlementState(),
)

class PaywallViewModel(
    private val subscriptions: SubscriptionRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            subscriptions.state.collect { entitlement ->
                mutableState.update { it.copy(entitlement = entitlement) }
            }
        }
        load()
    }

    fun load() = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(isLoading = true, message = null)
        subscriptions.refresh()
        subscriptions.offerings().fold(
            onSuccess = { offer ->
                mutableState.update { it.copy(isLoading = false, offer = offer, message = null) }
            },
            onFailure = { error ->
                mutableState.update { it.copy(isLoading = false, message = error.message ?: "Plans are not available yet.") }
            },
        )
    }

    fun purchase(plan: PlanPackage) = viewModelScope.launch {
        if (mutableState.value.isPurchasing) return@launch
        mutableState.value = mutableState.value.copy(isPurchasing = true, message = null)
        when (val outcome = subscriptions.purchase(plan)) {
            PurchaseOutcome.Success -> mutableState.value = mutableState.value.copy(isPurchasing = false, purchaseComplete = true, message = "Subscription is active.")
            PurchaseOutcome.Cancelled -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = "Purchase cancelled.")
            PurchaseOutcome.Pending -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = "Your payment is pending approval.")
            is PurchaseOutcome.Error -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = outcome.message)
        }
    }

    fun restore() = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(isPurchasing = true, message = null)
        when (val outcome = subscriptions.restore()) {
            PurchaseOutcome.Success -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = "Purchases restored.")
            PurchaseOutcome.Cancelled -> mutableState.value = mutableState.value.copy(isPurchasing = false)
            PurchaseOutcome.Pending -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = "Your restoration is pending.")
            is PurchaseOutcome.Error -> mutableState.value = mutableState.value.copy(isPurchasing = false, message = outcome.message)
        }
    }

    fun dismissPurchaseComplete() {
        mutableState.update { it.copy(purchaseComplete = false) }
    }
}
