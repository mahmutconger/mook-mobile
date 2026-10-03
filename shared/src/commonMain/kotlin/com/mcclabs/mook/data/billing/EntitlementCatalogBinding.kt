package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.PlanCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Abonelik durumunu güncel plan kataloğuyla birleştirir: kademe ya da `config/plans` değiştiği
 * an [EntitlementState.limits] yeniden hesaplanır. Böylece arayüz ve alan mantığı sabit bir
 * tabloya değil, sunucunun kurallarına uyar.
 */
internal fun StateFlow<EntitlementState>.withPlanCatalog(
    catalog: StateFlow<PlanCatalog>,
    scope: CoroutineScope,
): StateFlow<EntitlementState> {
    fun bind(state: EntitlementState, plans: PlanCatalog) = state.copy(limits = plans.limitsFor(state.tier), catalog = plans)
    return combine(this, catalog, ::bind).stateIn(scope, SharingStarted.Eagerly, bind(value, catalog.value))
}
