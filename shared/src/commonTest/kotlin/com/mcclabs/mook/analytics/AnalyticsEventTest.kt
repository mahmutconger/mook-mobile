package com.mcclabs.mook.analytics

import com.mcclabs.mook.domain.analytics.AdFailureStep
import com.mcclabs.mook.domain.analytics.AdFormat
import com.mcclabs.mook.domain.analytics.AnalyticsEvent
import com.mcclabs.mook.domain.analytics.DefaultSensitiveAnalyticsKeyPolicy
import com.mcclabs.mook.domain.billing.AdPlacement
import com.mcclabs.mook.domain.billing.Feature
import com.mcclabs.mook.domain.billing.GateDecision
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.billing.RewardType
import com.mcclabs.mook.domain.billing.Tier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsEventTest {

    /** Sözlükteki her olay türünden bir örnek — yeni olay eklenince buraya da eklenmeli. */
    private val allEvents: List<AnalyticsEvent> = listOf(
        AnalyticsEvent.GateDecisionMade.from(Feature.LIKE, Tier.FREE, GateDecision.Allowed),
        AnalyticsEvent.LimitReached(LimitReason.DAILY_LIKES, Tier.FREE, "discover", upgradeAvailable = true),
        AnalyticsEvent.AdShown("discover_native", AdFormat.NATIVE),
        AnalyticsEvent.AdNotReady("discover_like_interstitial", AdFormat.INTERSTITIAL),
        AnalyticsEvent.AdFailed("rewarded_like", AdFormat.REWARDED, AdFailureStep.LOAD, 3, "No fill"),
        AnalyticsEvent.RewardedEarned(RewardType.BONUS_LIKES, "rewarded_like", verified = true),
        AnalyticsEvent.PaywallView("in_app", Tier.FREE),
        AnalyticsEvent.PurchaseStarted("mook_premium", Tier.PREMIUM, Tier.FREE, "new"),
        AnalyticsEvent.PurchaseCompleted("mook_premium", Tier.PREMIUM, "new"),
        AnalyticsEvent.PurchaseCancelled("mook_premium"),
        AnalyticsEvent.PurchasePending("mook_premium"),
        AnalyticsEvent.PurchaseChangeScheduled("mook_economy", Tier.ECONOMY),
        AnalyticsEvent.PurchaseFailed("mook_premium", "network_error"),
        AnalyticsEvent.PurchaseRestoreStarted,
        AnalyticsEvent.PurchaseRestoreFinished("completed"),
        AnalyticsEvent.SubscriptionChanged(Tier.FREE, Tier.PREMIUM),
    )

    @Test
    fun eventNamesFollowFirebaseRulesAndAreUnique() {
        val reserved = setOf("purchase", "ad_impression", "ad_reward", "in_app_purchase", "error", "screen_view")
        allEvents.forEach { event ->
            assertTrue(event.name.matches(Regex("^[a-z][a-z0-9_]{0,39}$")), "Geçersiz olay adı: ${event.name}")
            assertTrue(event.name !in reserved, "GA4 ayrılmış adı kullanılamaz: ${event.name}")
        }
        assertEquals(allEvents.size, allEvents.map { it.name }.toSet().size)
    }

    @Test
    fun parameterKeysAreNeverFilteredAsSensitive() {
        // Hassas anahtar politikası "age" gibi parçaları eler; sözlükteki hiçbir anahtar
        // (ör. `stage`, `message`) sessizce kaybolmamalıdır.
        val policy = DefaultSensitiveAnalyticsKeyPolicy()
        allEvents.flatMap { it.parameters.keys }.forEach { key ->
            assertTrue(!policy.isSensitive(key), "Parametre hassas sayılıp elenirdi: $key")
            assertTrue(key.length <= 40, "Parametre adı çok uzun: $key")
        }
    }

    @Test
    fun requiredMonetizationEventsExist() {
        val names = allEvents.map { it.name }.toSet()
        listOf(
            "gate_decision", "ad_shown", "ad_not_ready", "ad_failed", "rewarded_earned",
            "limit_reached", "paywall_view", "purchase_started", "purchase_completed",
            "purchase_cancelled", "purchase_failed", "subscription_changed",
        ).forEach { assertTrue(it in names, "Eksik olay: $it") }
    }

    @Test
    fun gateDecisionMapsLimitReachedDetails() {
        val event = AnalyticsEvent.GateDecisionMade.from(
            Feature.LIKE, Tier.FREE,
            GateDecision.LimitReached(LimitReason.DAILY_LIKES, AdPlacement.LIKES_REWARDED, Tier.ECONOMY),
        )
        assertEquals("limit_reached", event.parameters["outcome"])
        assertEquals("daily_likes", event.parameters["limit_reason"])
        assertEquals("economy", event.parameters["upgrade_to"])
        assertEquals(true, event.parameters["rewarded_available"])
    }

    @Test
    fun subscriptionDirectionIsClassified() {
        assertEquals("new", AnalyticsEvent.SubscriptionChanged.directionOf(Tier.FREE, Tier.ECONOMY))
        assertEquals("ended", AnalyticsEvent.SubscriptionChanged.directionOf(Tier.PREMIUM, Tier.FREE))
        assertEquals("upgrade", AnalyticsEvent.SubscriptionChanged.directionOf(Tier.ECONOMY, Tier.PREMIUM))
        assertEquals("downgrade", AnalyticsEvent.SubscriptionChanged.directionOf(Tier.PREMIUM, Tier.STANDARD))
    }

    @Test
    fun longErrorTextIsTruncated() {
        val event = AnalyticsEvent.AdFailed("p", AdFormat.NATIVE, AdFailureStep.LOAD, errorMessage = "x".repeat(500))
        assertEquals(AnalyticsEvent.MAX_TEXT_VALUE_LENGTH, (event.parameters["error_detail"] as String).length)
    }
}
