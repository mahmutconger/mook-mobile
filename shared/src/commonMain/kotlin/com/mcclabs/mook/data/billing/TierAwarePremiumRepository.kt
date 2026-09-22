package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.PremiumRepository
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Compatibility bridge for older screens that only understand the Premium tier. */
class TierAwarePremiumRepository(
    private val subscriptions: SubscriptionRepository,
) : PremiumRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val isPremium: StateFlow<Boolean> = subscriptions.state
        .map { it.tier == Tier.PREMIUM }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override suspend fun refresh() = subscriptions.refresh()
    override suspend fun identify(userId: String) = subscriptions.logIn(userId)
    override suspend fun signOut() = subscriptions.logOut()
}
