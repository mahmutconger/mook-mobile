package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.PremiumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default [PremiumRepository] used until the RevenueCat SDK and store products are wired up.
 *
 * It reports every user as non-premium, so all gates behave as the free tier. Swapping this
 * for `RevenueCatPremiumRepository` in [com.mcclabs.mook.di.appModule] is the single line that
 * turns billing on — no feature code changes.
 */
class FreePremiumRepository : PremiumRepository {

    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    override suspend fun refresh() { /* no-op until a real provider is wired */ }

    override suspend fun identify(userId: String) { /* no-op */ }

    override suspend fun signOut() { /* no-op */ }
}
