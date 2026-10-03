package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.playStoreSubscriptionManagementUrl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Gereksinim 2.3: bu saf URL oluşturucunun `kotlin.test` ile test edilmesi — hiçbir
 * bağımlılığı olmadığından MockK gerekmez.
 */
class PlayStoreSubscriptionManagementUrlTest {

    @Test
    fun productIdentifierGivenAppendsSkuParameter() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?package=com.mcclabs.mook&sku=mook_premium:monthly-v1",
            playStoreSubscriptionManagementUrl("com.mcclabs.mook", "mook_premium:monthly-v1"),
        )
    }

    @Test
    fun nullProductIdentifierFallsBackToGeneralList() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?package=com.mcclabs.mook",
            playStoreSubscriptionManagementUrl("com.mcclabs.mook", null),
        )
    }

    @Test
    fun blankProductIdentifierFallsBackToGeneralList() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?package=com.mcclabs.mook",
            playStoreSubscriptionManagementUrl("com.mcclabs.mook", ""),
        )
    }
}
