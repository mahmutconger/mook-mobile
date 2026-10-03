package com.mcclabs.mook.billing

import com.mcclabs.mook.ads.LikeInterstitialAttempt
import com.mcclabs.mook.domain.billing.PlanCatalog
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.LikeInterstitialGateway
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.BoostManagerUseCase
import com.mcclabs.mook.domain.billing.PendingActionQueue
import com.mcclabs.mook.domain.billing.RecoverPendingSwipeActionsUseCase
import com.mcclabs.mook.domain.billing.Tier
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.domain.model.MatchSettings
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.InteractionRepository
import com.mcclabs.mook.domain.repository.LikeUsage
import com.mcclabs.mook.domain.repository.SettingsRepository
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.feature.discover.DiscoverViewModel
import io.mockk.verify
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Gereksinim 2.15 (KRİTİK): [DiscoverViewModel.likeProfile]'ın, bir interstitial denemesini
 * yalnızca sunucu yanıtı (`swipeUser`) alındıktan ve arayüz geçişi tamamlandıktan SONRA
 * tetiklediğini — ASLA kullanıcının "Beğen" dokunuşunu senkron olarak kesecek şekilde ÖNCE
 * değil — MockK ile ÇAĞRI SIRASINI doğrulayarak kanıtlar (bkz. `LikeInterstitialGateway` KDoc'u).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("DiscoverViewModel — Gereksinim 2.15 interstitial zamanlaması")
class LikeInterstitialTransitionOrderTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val discoverRepository = mockk<DiscoverRepository>()
    private val interactionRepository = mockk<InteractionRepository>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val pendingActionQueue = mockk<PendingActionQueue>()
    private val connectivityObserver = mockk<ConnectivityObserver>()
    private val likeInterstitialGateway = mockk<LikeInterstitialGateway>()
    private val boostManagerUseCase = mockk<BoostManagerUseCase>()
    private val analyticsRepository = mockk<AnalyticsRepository>()

    private val profile = DiscoverProfile(
        id = "peer-uid",
        name = "Ada",
        age = 25,
        country = null,
        language = null,
        photoUrls = emptyList(),
        bio = "",
        interests = emptyList(),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        every { subscriptionRepository.state } returns MutableStateFlow(
            EntitlementState(tier = Tier.FREE, isResolved = true, limits = PlanCatalog.BUNDLED.limitsFor(Tier.FREE)),
        )
        coJustRun { subscriptionRepository.refresh() }

        coEvery { settingsRepository.getSettings() } returns MatchSettings()
        every { settingsRepository.observeSettings() } returns flowOf(MatchSettings())
        coEvery { settingsRepository.getHasSeenLikedMeTutorial() } returns false

        justRun { discoverRepository.resetDiscoverPaging() }
        // Kapı kararları ve limitler analitiğe bildirilir (gate_decision / limit_reached).
        justRun { analyticsRepository.track(any()) }
        coEvery { discoverRepository.getDiscoverProfiles(any()) } returns listOf(profile)
        every { discoverRepository.hasMoreProfiles() } returns false
        coEvery { discoverRepository.getLikeUsageToday() } returns LikeUsage(likes = 0, rewardedLikes = 0, likesEver = 3)
        justRun { discoverRepository.markActedOn(any()) }

        coJustRun { pendingActionQueue.enqueue(any()) }
        coJustRun { pendingActionQueue.remove(any()) }
        coEvery { pendingActionQueue.dequeueAll() } returns emptyList()

        every { connectivityObserver.isOnline } returns MutableStateFlow(true)

        coEvery { interactionRepository.swipeUser("peer-uid", true) } returns MatchResult.SingleLike

        coEvery { likeInterstitialGateway.attemptShowAfterTransition(any(), any()) } returns LikeInterstitialAttempt()
        justRun { likeInterstitialGateway.recordAction(any(), any(), any()) }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DiscoverViewModel(
        repository = discoverRepository,
        interactionRepository = interactionRepository,
        settingsRepository = settingsRepository,
        subscriptions = subscriptionRepository,
        pendingActionQueue = pendingActionQueue,
        recoverPendingSwipeActionsUseCase = RecoverPendingSwipeActionsUseCase(
            pendingActionQueue, interactionRepository, connectivityObserver,
        ),
        likeInterstitialGateway = likeInterstitialGateway,
        boostManagerUseCase = boostManagerUseCase,
        analyticsRepository = analyticsRepository,
    )

    @Test
    fun `beğeni sunucuya gönderilmeden interstitial hiç denenmez`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.likeProfile("peer-uid")

        // Gereksinim 2.15: sıra kritik — sunucu yanıtı (swipeUser) HER ZAMAN interstitial
        // denemesinden ÖNCE gelmelidir. coVerifyOrder, bu göreli sırayı kanıtlar.
        coVerifyOrder {
            interactionRepository.swipeUser("peer-uid", true)
            likeInterstitialGateway.attemptShowAfterTransition(any(), any())
        }
    }

    @Test
    fun `interstitial denemesi arayüz beğenilen olarak güncellendikten sonra tetiklenir`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.likeProfile("peer-uid")

        // Gereksinim 2.15: reklam denendiğinde StateFlow zaten "beğenildi" durumuna
        // güncellenmiş olmalı — bu, arayüzün kullanıcıya ÖNCE geçişi gösterdiğinin kanıtıdır.
        assertTrue("peer-uid" in viewModel.state.value.likedProfileIds)
        coVerifyOrder {
            interactionRepository.swipeUser("peer-uid", true)
            likeInterstitialGateway.attemptShowAfterTransition(any(), any())
            likeInterstitialGateway.recordAction(any(), any(), any())
        }
    }

    @Test
    fun `karşılıklı eşleşmede kutlama ekranı reklamla kesilmez`() = runTest(dispatcher) {
        coEvery { interactionRepository.swipeUser("peer-uid", true) } returns MatchResult.MutualMatch
        // Eşleşme dalı "match_created" analitik olayını kaydeder.
        justRun { analyticsRepository.logMatchCreated(any()) }
        val viewModel = createViewModel()
        // Eşleşme olayı tamponsuz bir SharedFlow'a yayınlanır; gerçek ekran gibi bir dinleyici
        // olmadan `emit` askıda kalır ve akış hiç tamamlanmaz.
        backgroundScope.launch { viewModel.events.collect { } }

        viewModel.likeProfile("peer-uid")
        advanceUntilIdle()

        // Reklam gösterim kuralı: eşleşmede geçiş reklamı HİÇ denenmez; eşleşme yalnızca reklam
        // sıklığı sayacına eklenir (bkz. AdDisplayRules).
        coVerify(exactly = 0) { likeInterstitialGateway.attemptShowAfterTransition(any(), any()) }
        verify(exactly = 1) {
            likeInterstitialGateway.recordAction(any(), LikeInterstitialAttempt(countSuccessfulLike = true), true)
        }
    }
}
