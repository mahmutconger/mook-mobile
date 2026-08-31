package com.mcclabs.mook.domain.billing

import kotlinx.coroutines.flow.StateFlow

/**
 * Provider-agnostic access to the user's premium (subscription) status.
 *
 * The rest of the app depends only on this interface — never on RevenueCat directly — so
 * the billing provider can be wired in (or swapped) without touching feature code, and so
 * gating logic stays unit-testable. The concrete RevenueCat-backed implementation is added
 * once the store products and RevenueCat dashboard are configured; until then a safe
 * "always free" implementation keeps the app building and running.
 */
interface PremiumRepository {

    /**
     * Whether the current user has an active [BillingConfig.ENTITLEMENT_PREMIUM] entitlement.
     * Reactive: gates observe this and update immediately after a purchase or restore.
     */
    val isPremium: StateFlow<Boolean>

    /** Re-fetches entitlement state from the provider (e.g. after returning to foreground). */
    suspend fun refresh()

    /**
     * Binds purchases to the given app user id (the Firebase uid), so the entitlement follows
     * the account across devices and reinstalls. Call right after sign-in.
     */
    suspend fun identify(userId: String)

    /** Detaches the current user from the provider on sign-out. */
    suspend fun signOut()
}
