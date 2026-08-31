package com.mcclabs.mook.domain.billing

/**
 * Central configuration for monetization limits and entitlement identifiers.
 *
 * Keeping the numbers here (not scattered across the UI/ViewModels) makes it a single
 * place to tune the free tier and keeps the paywall copy in sync with what is enforced.
 */
object BillingConfig {
    /** How many swipes a non-premium user gets per calendar day (local time). */
    const val FREE_DAILY_SWIPE_LIMIT: Int = 10

    /**
     * RevenueCat entitlement identifier that unlocks all premium features. Must match the
     * entitlement configured in the RevenueCat dashboard exactly.
     */
    const val ENTITLEMENT_PREMIUM: String = "premium"
}
