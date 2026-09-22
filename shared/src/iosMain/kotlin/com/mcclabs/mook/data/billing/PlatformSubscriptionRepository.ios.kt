package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.SubscriptionRepository

actual fun createPlatformSubscriptionRepository(): SubscriptionRepository = FreeSubscriptionRepository()
