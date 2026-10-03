package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.AdFrequencyPolicy
import com.mcclabs.mook.domain.billing.AdPlacement
import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.Feature
import com.mcclabs.mook.domain.billing.FeatureGate
import com.mcclabs.mook.domain.billing.GateDecision
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.billing.UsageSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureGateTest {
    private val gate = FeatureGate()

    @Test
    fun freeLikeAtLimitOffersRewardedAd() {
        val decision = gate.decide(
            Feature.LIKE,
            EntitlementState(limits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE)),
            UsageSnapshot(likes = 10),
            showInterstitial = false,
        )
        val limit = assertIs<GateDecision.LimitReached>(decision)
        assertEquals(LimitReason.DAILY_LIKES, limit.reason)
        assertEquals(AdPlacement.LIKES_REWARDED, limit.rewarded)
        // Economy en yakın üst kademedir (30 beğeni/gün, Free'nin 10'undan fazla) ve Free'yi
        // bu limitten kurtarır; öneri artık sabit "her zaman Standard" yerine hesaplanıyor.
        assertEquals(Tier.ECONOMY, limit.upgradeTo)
    }

    @Test
    fun freeLikeUnderLimitWithRewardedBonusIsNotExhausted() {
        // 10 taban + en fazla 5 ödüllü bonus = 15. 12. beğeni hâlâ serbest olmalı.
        val decision = gate.decide(
            Feature.LIKE,
            EntitlementState(limits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE)),
            UsageSnapshot(likes = 12, rewardedLikes = 5),
            showInterstitial = false,
        )
        assertEquals(GateDecision.Allowed, decision)
    }

    @Test
    fun premiumLikeIsNeverClientLimited() {
        val decision = gate.decide(
            Feature.LIKE,
            EntitlementState(tier = Tier.PREMIUM, limits = PlanCatalog.BUNDLED.limitsFor(Tier.PREMIUM)),
            UsageSnapshot(likes = 10_000),
            showInterstitial = true,
        )
        assertEquals(GateDecision.Allowed, decision)
    }

    @Test
    fun quotaCheckHappensBeforeAdRequest_limitReachedEvenWhenAdFirstWouldOtherwiseFire() {
        // Gereksinim 1.1: kota tükenmişse reklam ASLA istenmez, showInterstitial=true olsa bile
        // sonuç LimitReached olmalı, AdFirst değil.
        val decision = gate.decide(
            Feature.LIKE,
            EntitlementState(limits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE)),
            UsageSnapshot(likes = 10),
            showInterstitial = true,
        )
        assertIs<GateDecision.LimitReached>(decision)
    }

    @Test
    fun economyMessageLimitOffersUpgradeToStandard() {
        val decision = gate.decide(
            Feature.MESSAGE,
            EntitlementState(tier = Tier.ECONOMY, limits = PlanCatalog.BUNDLED.limitsFor(Tier.ECONOMY)),
            UsageSnapshot(messages = 200),
            showInterstitial = false,
        )
        val limit = assertIs<GateDecision.LimitReached>(decision)
        assertEquals(LimitReason.DAILY_MESSAGES, limit.reason)
        assertNull(limit.rewarded)
        assertEquals(Tier.STANDARD, limit.upgradeTo)
    }

    @Test
    fun standardMessagesHaveNoCountCap() {
        // Yalnızca Çeviri Kotası Mantığı: Standart'ın dailyMessages'ı null'dur ve artık düz bir
        // "adil kullanım" mesaj sayısı tavanı YOKTUR; karakter kotası yalnızca çeviriyi sınırlar.
        val decision = gate.decide(
            Feature.MESSAGE,
            EntitlementState(tier = Tier.STANDARD, limits = PlanCatalog.BUNDLED.limitsFor(Tier.STANDARD)),
            UsageSnapshot(messages = 5_000),
            showInterstitial = false,
        )
        assertEquals(GateDecision.Allowed, decision)
    }

    @Test
    fun premiumMessagesHaveNoCountCap() {
        val decision = gate.decide(
            Feature.MESSAGE,
            EntitlementState(tier = Tier.PREMIUM, limits = PlanCatalog.BUNDLED.limitsFor(Tier.PREMIUM)),
            UsageSnapshot(messages = 5_000),
            showInterstitial = false,
        )
        assertEquals(GateDecision.Allowed, decision)
    }

    @Test
    fun economyLikeLimitOffersRewardedAd() {
        // Gereksinim 2.5: Economy artık Free ile aynı şekilde ödüllü reklamla ekstra beğeni
        // kazanabilir — Economy'nin Free'den DAHA AZ yeteneğe sahip olmaması gerekir.
        val decision = gate.decide(
            Feature.LIKE,
            EntitlementState(tier = Tier.ECONOMY, limits = PlanCatalog.BUNDLED.limitsFor(Tier.ECONOMY)),
            UsageSnapshot(likes = 30),
            showInterstitial = false,
        )
        val limit = assertIs<GateDecision.LimitReached>(decision)
        assertEquals(AdPlacement.LIKES_REWARDED, limit.rewarded)
    }

    @Test
    fun standardLikeLimitDoesNotOfferRewardedAd() {
        // Standart/Premium zaten çok daha yüksek (ya da sınırsız) tabanlara sahip; ödüllü
        // reklam mekanizması yalnızca Free/Economy'e özgüdür (Gereksinim 2.5).
        val decision = gate.decide(
            Feature.LIKE,
            EntitlementState(tier = Tier.STANDARD, limits = PlanCatalog.BUNDLED.limitsFor(Tier.STANDARD)),
            UsageSnapshot(likes = 100),
            showInterstitial = false,
        )
        val limit = assertIs<GateDecision.LimitReached>(decision)
        assertNull(limit.rewarded)
    }

    @Test
    fun economyLikedMeAtZeroBaseOffersRewardedAd() {
        // Gereksinim 2.5: Free/Economy'de taban "beni beğenenler" hakkı 0'dır; ödüllü reklam
        // bu iki kademeye de küçük, sınırlı bir tat vermelidir.
        val decision = gate.decide(
            Feature.LIKED_ME,
            EntitlementState(tier = Tier.ECONOMY, limits = PlanCatalog.BUNDLED.limitsFor(Tier.ECONOMY)),
            UsageSnapshot(likedMeUnlocks = 0),
            showInterstitial = false,
        )
        val limit = assertIs<GateDecision.LimitReached>(decision)
        assertEquals(LimitReason.LIKED_ME_UNLOCKS, limit.reason)
        assertEquals(AdPlacement.LIKED_ME_REWARDED, limit.rewarded)
    }

    @Test
    fun economyLikedMeUnderRewardedBonusIsNotExhausted() {
        // Ödüllü reklam ekonomisi: 0 taban + günde en fazla 2 ödüllü açma (her reklam +1) = 2.
        // 1 açma kullanılmışken 2. açma hâlâ serbest olmalı.
        val decision = gate.decide(
            Feature.LIKED_ME,
            EntitlementState(tier = Tier.ECONOMY, limits = PlanCatalog.BUNDLED.limitsFor(Tier.ECONOMY)),
            UsageSnapshot(likedMeUnlocks = 1, rewardedLikedMeUnlocks = 2),
            showInterstitial = false,
        )
        assertEquals(GateDecision.Allowed, decision)
    }

    @Test
    fun standardLikedMeLimitDoesNotOfferRewardedAd() {
        val decision = gate.decide(
            Feature.LIKED_ME,
            EntitlementState(tier = Tier.STANDARD, limits = PlanCatalog.BUNDLED.limitsFor(Tier.STANDARD)),
            UsageSnapshot(likedMeUnlocks = 5),
            showInterstitial = false,
        )
        val limit = assertIs<GateDecision.LimitReached>(decision)
        assertEquals(LimitReason.LIKED_ME_UNLOCKS, limit.reason)
        assertNull(limit.rewarded)
    }

    @Test
    fun replyToExistingChatDoesNotConsumeNewChatQuota() {
        // Gereksinim 1.6: mevcut bir sohbete yanıt vermek dailyNewChats sayacını azaltmaz.
        // FeatureGate seviyesinde bunun karşılığı: NEW_CHAT feature'ı hiç çağrılmadığı sürece
        // (çağıran taraf yalnızca yeni sohbet başlatırken bu feature'ı sorar) MESSAGE kotası
        // etkilenmeden kalır.
        val decision = gate.decide(
            Feature.MESSAGE,
            EntitlementState(limits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE)),
            UsageSnapshot(messages = 0, newChats = 3),
            showInterstitial = false,
        )
        assertEquals(GateDecision.Allowed, decision)
    }

    @Test
    fun profileVisitIsNeverClientLimited() {
        val decision = gate.decide(
            Feature.PROFILE_VISIT,
            EntitlementState(limits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE)),
            UsageSnapshot(),
            showInterstitial = false,
        )
        assertEquals(GateDecision.Allowed, decision)
    }

    @Test
    fun premiumBoostCapHasNoUpgradeOffer() {
        // Premium'da dahi aylık 4 boost tavanı var; daha üst bir kademe olmadığından bu bir
        // adil kullanım tavanı gibi davranmalıdır (upgradeTo = null).
        val decision = gate.decide(
            Feature.BOOST,
            EntitlementState(tier = Tier.PREMIUM, limits = PlanCatalog.BUNDLED.limitsFor(Tier.PREMIUM)),
            UsageSnapshot(boosts = 4),
            showInterstitial = false,
        )
        val limit = assertIs<GateDecision.LimitReached>(decision)
        assertEquals(LimitReason.BOOSTS, limit.reason)
        assertNull(limit.upgradeTo)
    }

    @Test
    fun interstitialWaitsForOnboardingAndActionCadence() {
        val policy = AdFrequencyPolicy()
        assertTrue(!policy.canShow(100_000, 50_000, 10, 3, null, 0, 0))
        assertTrue(!policy.canShow(100_000_000, 0, 4, 3, null, 0, 0))
        assertTrue(policy.canShow(100_000_000, 0, 5, 3, null, 0, 0))
    }
}
