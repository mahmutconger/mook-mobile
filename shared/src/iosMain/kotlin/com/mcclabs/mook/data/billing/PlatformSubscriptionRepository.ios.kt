package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.PlanCatalogRepository
import com.mcclabs.mook.domain.billing.SubscriptionRepository

actual fun createPlatformSubscriptionRepository(planCatalog: PlanCatalogRepository): SubscriptionRepository =
    FreeSubscriptionRepository(planCatalog)
