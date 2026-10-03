package com.mcclabs.mook.likedme

import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.domain.model.LikedMeEntry
import com.mcclabs.mook.domain.model.LikedMePage
import com.mcclabs.mook.domain.repository.LikedMeRepository
import com.mcclabs.mook.domain.repository.LikedMeUnlockResult
import com.mcclabs.mook.feature.likedme.LikedMeListItem
import com.mcclabs.mook.feature.likedme.LikedMeViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** "Beni Beğenenler" ViewModel'inin liste düzeni ve kilit açma akışı. */
class LikedMeViewModelTest {

    private val repository = mockk<LikedMeRepository>()
    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)
    private val connectivity = mockk<ConnectivityObserver>()
    private val analytics = mockk<AnalyticsRepository>(relaxed = true)
    private val entitlement = MutableStateFlow(
        EntitlementState(tier = Tier.FREE, isResolved = true, limits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE)),
    )

    private fun entry(i: Int) = LikedMeEntry("token_$i", likedAtMillis = i.toLong(), isUnlocked = false, photoUrl = null, profile = null)

    private fun page(count: Int) = LikedMePage(
        entries = (1..count).map(::entry), totalCount = count, hasMore = false, revealAll = false, unlocksRemainingToday = 0,
    )

    @BeforeEach
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
        every { subscriptions.state } returns entitlement
        every { connectivity.isOnline } returns MutableStateFlow(true)
        coEvery { repository.load() } returns Result.success(page(10))
    }

    @AfterEach
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    @DisplayName("Ücretsiz planda 8. satır (indeks 7) yerel reklamdır; sayaç sunucudan gelir")
    fun freePlanInterleavesNativeAd() {
        val viewModel = LikedMeViewModel(repository, subscriptions, connectivity, analytics)
        val state = viewModel.state.value
        assertEquals(10, state.totalCount)
        assertEquals(11, state.items.size)
        assertTrue(state.items[7] is LikedMeListItem.NativeAd)
    }

    @Test
    @DisplayName("Reklamsız plana geçilince reklam yuvaları kalkar ve liste yenilenir")
    fun paidPlanRemovesAds() {
        val viewModel = LikedMeViewModel(repository, subscriptions, connectivity, analytics)
        entitlement.value = EntitlementState(tier = Tier.PREMIUM, isResolved = true, limits = PlanCatalog.BUNDLED.limitsFor(Tier.PREMIUM))
        assertTrue(viewModel.state.value.items.none { it is LikedMeListItem.NativeAd })
        coVerify(exactly = 2) { repository.load() }
    }

    @Test
    @DisplayName("Hak yoksa limit sayfası açılır; ödül doğrulanınca kilit açma otomatik tekrarlanır")
    fun rewardedUnlockRetriesPendingEntry() {
        coEvery { repository.unlock("token_1") } returnsMany listOf(
            LikedMeUnlockResult.DailyLimitReached,
            LikedMeUnlockResult.Unlocked(alreadyUnlocked = false),
        )
        val viewModel = LikedMeViewModel(repository, subscriptions, connectivity, analytics)

        viewModel.onUnlockClicked(entry(1))
        assertEquals(LimitReason.LIKED_ME_UNLOCKS, viewModel.state.value.limitReason)

        viewModel.onRewardedUnlockConfirmed()
        assertNull(viewModel.state.value.limitReason)
        coVerify(exactly = 2) { repository.unlock("token_1") }
        // Başarılı kilit açmadan sonra liste yeniden yüklenir (açık profil sunucudan gelir).
        coVerify(exactly = 2) { repository.load() }
    }
}
