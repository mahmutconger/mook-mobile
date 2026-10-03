package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.PlanCatalogRepository
import com.mcclabs.mook.domain.billing.SubscriptionRepository

/**
 * Billing is intentionally platform-owned: Android is backed by Google Play via
 * RevenueCat, while iOS stays a safe no-op until its store launch is in scope.
 */
/**
 * @param planCatalog Plan sınırlarının tek doğruluk kaynağı (`config/plans`); durumun
 *   [com.mcclabs.mook.domain.billing.EntitlementState.limits] alanı bundan türetilir.
 */
expect fun createPlatformSubscriptionRepository(planCatalog: PlanCatalogRepository): SubscriptionRepository
