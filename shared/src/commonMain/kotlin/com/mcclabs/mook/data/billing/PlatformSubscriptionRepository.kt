package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.SubscriptionRepository

/**
 * Billing is intentionally platform-owned: Android is backed by Google Play via
 * RevenueCat, while iOS stays a safe no-op until its store launch is in scope.
 */
expect fun createPlatformSubscriptionRepository(): SubscriptionRepository
