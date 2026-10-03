package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.MidnightCountdown
import com.mcclabs.mook.domain.billing.OfferingMetadataParser
import com.mcclabs.mook.domain.billing.PaywallMetadata
import com.mcclabs.mook.domain.billing.PaywallRequest
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.billing.Period
import com.mcclabs.mook.domain.billing.PlanPackage
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.model.ChatMessage
import com.mcclabs.mook.domain.model.MessageStatus
import com.mcclabs.mook.domain.model.ReadReceiptRules
import com.mcclabs.mook.navigation.NavRoutes
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PremiumPaywallRulesTest {

    private fun plan(id: String, tier: Tier) = PlanPackage(identifier = id, tier = tier, period = Period.MONTHLY, localizedPrice = "₺1")

    // ── RevenueCat teklif meta verisi ──────────────────────────────────────

    @Test
    fun metadataPackageIdWinsOverTier() {
        val metadata = OfferingMetadataParser.parse(mapOf("most_popular_package" to "\$rc_annual_premium", "most_popular_tier" to "standard"))
        assertTrue(metadata.isMostPopular("\$rc_annual_premium", Tier.PREMIUM))
        assertFalse(metadata.isMostPopular("\$rc_monthly_standard", Tier.STANDARD))
        assertEquals(Tier.PREMIUM, metadata.badgeTier(listOf(plan("\$rc_annual_premium", Tier.PREMIUM))))
    }

    @Test
    fun metadataTierIsCaseInsensitiveAndFreeIsIgnored() {
        assertEquals(Tier.STANDARD, OfferingMetadataParser.parse(mapOf("most_popular_tier" to " Standard ")).mostPopularTier)
        assertNull(OfferingMetadataParser.parse(mapOf("most_popular_tier" to "free")).mostPopularTier)
    }

    @Test
    fun malformedOrMissingMetadataGivesNoBadge() {
        assertEquals(PaywallMetadata(), OfferingMetadataParser.parse(null))
        assertEquals(PaywallMetadata(), OfferingMetadataParser.parse(mapOf("most_popular_tier" to 42, "most_popular_package" to listOf("x"))))
        assertFalse(PaywallMetadata().isMostPopular("any", Tier.STANDARD))
    }

    // ── Gece yarısı geri sayımı ───────────────────────────────────────────

    @Test
    fun countdownTargetsNextLocalMidnight() {
        val istanbul = TimeZone.of("Europe/Istanbul")
        val at2230 = 1_759_347_000_000L // 2025-10-01 22:30 İstanbul
        assertEquals(90 * 60 * 1000L, MidnightCountdown.millisUntilNextMidnight(at2230, istanbul))
        assertEquals("01:30:00", MidnightCountdown.format(90 * 60 * 1000L))
        assertEquals("00:00:01", MidnightCountdown.format(1L))
    }

    @Test
    fun countdownIsNeverZeroAtExactMidnight() {
        val utc = TimeZone.UTC
        val midnight = 1_759_276_800_000L // 2025-10-01 00:00 UTC
        assertEquals(24 * 3_600_000L, MidnightCountdown.millisUntilNextMidnight(midnight, utc))
    }

    // ── Paywall yönlendirmesi ─────────────────────────────────────────────

    @Test
    fun paywallRouteCarriesLimitReason() {
        assertEquals("paywall?preselectTrial=false", NavRoutes.Paywall.createRoute(PaywallRequest()))
        assertEquals(
            "paywall?preselectTrial=true&reason=DAILY_LIKES",
            NavRoutes.Paywall.createRoute(PaywallRequest(LimitReason.DAILY_LIKES, preselectTrial = true)),
        )
    }

    // ── Okundu bilgisi ────────────────────────────────────────────────────

    private fun message(id: String, ts: Long, mine: Boolean, status: MessageStatus = MessageStatus.SENT) = ChatMessage(
        id = id, senderUid = if (mine) "me" else "peer", text = id, translatedText = null,
        senderLanguage = "TR", timestamp = ts, isMine = mine, status = status,
    )

    @Test
    fun seenLabelGoesOnLatestOwnMessageReadByPeer() {
        val messages = listOf(message("a", 1, true), message("b", 2, false), message("c", 3, true), message("d", 5, true))
        assertEquals("c", ReadReceiptRules.lastSeenOwnMessageId(messages, peerLastReadAt = 4))
        assertEquals("d", ReadReceiptRules.lastSeenOwnMessageId(messages, peerLastReadAt = 5))
        assertNull(ReadReceiptRules.lastSeenOwnMessageId(messages, peerLastReadAt = null))
    }

    @Test
    fun pendingOrFailedMessagesAreNeverMarkedSeen() {
        val messages = listOf(message("a", 1, true), message("b", 2, true, MessageStatus.SENDING))
        assertEquals("a", ReadReceiptRules.lastSeenOwnMessageId(messages, peerLastReadAt = 10))
    }
}
