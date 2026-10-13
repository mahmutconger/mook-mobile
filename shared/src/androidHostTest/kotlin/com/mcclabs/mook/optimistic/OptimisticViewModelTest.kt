package com.mcclabs.mook.optimistic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.*
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.domain.interaction.SetLikeUseCase
import com.mcclabs.mook.domain.model.*
import com.mcclabs.mook.domain.repository.*
import com.mcclabs.mook.feature.discover.DiscoverEvent
import com.mcclabs.mook.feature.discover.DiscoverViewModel
import com.mcclabs.mook.feature.liked.LikedEvent
import com.mcclabs.mook.feature.liked.LikedViewModel
import com.mcclabs.mook.feature.profile.ProfileDetailsEvent
import com.mcclabs.mook.feature.profile.ProfileDetailsViewModel
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class OptimisticViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = mockk<DiscoverRepository>(relaxed = true)
    private val likes = mockk<LikeRepository>()
    private val interactions = mockk<InteractionRepository>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)
    private val profile = DiscoverProfile("peer", "Deniz", 25, null, null, emptyList(), "", emptyList())
    private val second = profile.copy(id = "second", name = "Ece")
    private val setLike = SetLikeUseCase(likes)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { subscriptions.state } returns MutableStateFlow(EntitlementState(
            tier = Tier.PREMIUM, isResolved = true, limits = PlanCatalog.BUNDLED.limitsFor(Tier.PREMIUM)))
        every { settings.observeSettings() } returns flowOf(MatchSettings())
        every { repository.hasMoreProfiles() } returns false
        every { repository.actedOnProfileIds() } returns emptySet()
        coEvery { repository.getLikeUsageToday() } returns LikeUsage(likes = 2, likesEver = 3)
        coEvery { repository.getDiscoverProfiles(any()) } returns listOf(profile, second)
        coEvery { repository.getLikedProfiles() } returns listOf(LikedProfile(profile, false), LikedProfile(second, true))
        coEvery { repository.getProfileDetails("peer") } returns profile
    }

    @AfterEach
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun <T : ViewModel> retain(viewModel: T): T {
        store.put(viewModel::class.simpleName.orEmpty(), viewModel)
        return viewModel
    }

    private fun discover() = retain(DiscoverViewModel(repository, interactions, settings, subscriptions,
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk<AnalyticsRepository>(relaxed = true), setLike))

    private fun liked() = retain(LikedViewModel(repository, setLike))

    private fun details(): ProfileDetailsViewModel {
        mockkStatic(com.google.firebase.auth.FirebaseAuth::class)
        val auth = mockk<com.google.firebase.auth.FirebaseAuth>()
        val user = mockk<com.google.firebase.auth.FirebaseUser>()
        every { com.google.firebase.auth.FirebaseAuth.getInstance() } returns auth
        every { auth.currentUser } returns user
        every { user.uid } returns "current"
        val connectivity = mockk<ConnectivityObserver>()
        every { connectivity.isOnline } returns MutableStateFlow(false)
        return retain(ProfileDetailsViewModel(repository, interactions, subscriptions, mockk(relaxed = true),
            connectivity, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), "peer", setLike))
    }

    @Test
    fun discoverLikeIsImmediateAndRemainsAfterSuccess() = runTest {
        val response = CompletableDeferred<MatchResult>()
        coEvery { likes.setLiked("peer", true) } coAnswers { response.await() }
        val viewModel = discover()
        runCurrent()
        viewModel.likeProfile("peer")
        assertTrue("peer" in viewModel.state.value.likedProfileIds)
        assertEquals(3, viewModel.state.value.swipesUsedToday)
        assertTrue(viewModel.state.value.isSwipeInFlight)
        viewModel.likeProfile("peer")
        coVerify(exactly = 1) { likes.setLiked("peer", true) }
        response.complete(MatchResult.SingleLike)
        runCurrent()
        assertTrue("peer" in viewModel.state.value.likedProfileIds)
        assertEquals(4, viewModel.state.value.likesEver)
        assertFalse(viewModel.state.value.isSwipeInFlight)
        verify { repository.markActedOn("peer") }
    }

    @Test
    fun discoverFailureRestoresHeartAndQuotaAndBuffersOneError() = runTest {
        val response = CompletableDeferred<MatchResult>()
        coEvery { likes.setLiked("peer", true) } coAnswers { response.await() }
        val viewModel = discover()
        runCurrent()
        viewModel.likeProfile("peer")
        assertTrue("peer" in viewModel.state.value.likedProfileIds)
        response.completeExceptionally(IllegalStateException("İzin reddedildi."))
        runCurrent()
        assertFalse("peer" in viewModel.state.value.likedProfileIds)
        assertEquals(2, viewModel.state.value.swipesUsedToday)
        assertFalse(viewModel.state.value.isSwipeInFlight)
        val events = mutableListOf<DiscoverEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        assertEquals(listOf(DiscoverEvent.ShowSnackbar("Beğeni güncellenemedi. Lütfen tekrar deneyin.")), events)
        runCurrent()
        assertEquals(1, events.size)
        verify(exactly = 0) { repository.markActedOn("peer") }
    }

    @Test
    fun discoverUnlikeUpdatesImmediatelyAndRestoresOnFailure() = runTest {
        coEvery { likes.setLiked("peer", true) } returns MatchResult.SingleLike
        val response = CompletableDeferred<MatchResult>()
        coEvery { likes.setLiked("peer", false) } coAnswers { response.await() }
        val viewModel = discover()
        runCurrent()
        viewModel.likeProfile("peer")
        runCurrent()
        viewModel.likeProfile("peer")
        assertFalse("peer" in viewModel.state.value.likedProfileIds)
        assertEquals(3, viewModel.state.value.swipesUsedToday)
        response.completeExceptionally(IllegalStateException("Bağlantı hatası."))
        runCurrent()
        assertTrue("peer" in viewModel.state.value.likedProfileIds)
        assertEquals(3, viewModel.state.value.swipesUsedToday)
    }

    @Test
    fun likedUnlikeIsImmediateAndRemainsRemovedAfterSuccess() = runTest {
        val response = CompletableDeferred<MatchResult>()
        coEvery { likes.setLiked("peer", false) } coAnswers { response.await() }
        val viewModel = liked()
        runCurrent()
        viewModel.unlikeProfile("peer")
        assertEquals(listOf("second"), viewModel.state.value.visibleLiked.map { it.profile.id })
        viewModel.unlikeProfile("peer")
        coVerify(exactly = 1) { likes.setLiked("peer", false) }
        response.complete(MatchResult.Pass)
        runCurrent()
        assertEquals(listOf("second"), viewModel.state.value.liked.map { it.profile.id })
        assertTrue(viewModel.state.value.unlikedProfileIds.isEmpty())
        verify { repository.unmarkActedOn("peer") }
    }

    @Test
    fun likedFailureRestoresOriginalOrderAndEmitsOneError() = runTest {
        val response = CompletableDeferred<MatchResult>()
        coEvery { likes.setLiked("peer", false) } coAnswers { response.await() }
        val viewModel = liked()
        runCurrent()
        viewModel.unlikeProfile("peer")
        assertEquals(1, viewModel.state.value.visibleLiked.size)
        response.completeExceptionally(IllegalStateException("İzin reddedildi."))
        runCurrent()
        assertEquals(listOf("peer", "second"), viewModel.state.value.visibleLiked.map { it.profile.id })
        val events = mutableListOf<LikedEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        assertEquals(listOf(LikedEvent.ShowSnackbar("Beğeni geri çekilemedi. Lütfen tekrar deneyin.")), events)
        runCurrent()
        assertEquals(1, events.size)
    }

    @Test
    fun independentUnlikeFailuresDoNotRestoreSuccessfullyRemovedRows() = runTest {
        val first = CompletableDeferred<MatchResult>()
        val secondResponse = CompletableDeferred<MatchResult>()
        coEvery { likes.setLiked("peer", false) } coAnswers { first.await() }
        coEvery { likes.setLiked("second", false) } coAnswers { secondResponse.await() }
        val viewModel = liked()
        runCurrent()
        viewModel.unlikeProfile("peer")
        viewModel.unlikeProfile("second")
        assertTrue(viewModel.state.value.visibleLiked.isEmpty())
        secondResponse.complete(MatchResult.Pass)
        first.completeExceptionally(IllegalStateException("Bağlantı hatası."))
        runCurrent()
        assertEquals(listOf("peer"), viewModel.state.value.visibleLiked.map { it.profile.id })
    }

    @Test
    fun staleLoadCannotReintroduceConfirmedUnlike() = runTest {
        coEvery { likes.setLiked("peer", false) } returns MatchResult.Pass
        val viewModel = liked()
        runCurrent()
        val response = CompletableDeferred<List<LikedProfile>>()
        coEvery { repository.getLikedProfiles() } coAnswers { response.await() }
        viewModel.load()
        runCurrent()
        viewModel.unlikeProfile("peer")
        response.complete(listOf(LikedProfile(profile, false), LikedProfile(second, true)))
        runCurrent()
        assertEquals(listOf("second"), viewModel.state.value.visibleLiked.map { it.profile.id })
    }

    @Test
    fun loadStartedDuringUnlikeCannotReintroduceTheRemovedRow() = runTest {
        val sync = CompletableDeferred<MatchResult>()
        coEvery { likes.setLiked("peer", false) } coAnswers { sync.await() }
        val viewModel = liked()
        runCurrent()
        viewModel.unlikeProfile("peer")
        val response = CompletableDeferred<List<LikedProfile>>()
        coEvery { repository.getLikedProfiles() } coAnswers { response.await() }
        viewModel.load()
        runCurrent()
        sync.complete(MatchResult.Pass)
        runCurrent()
        response.complete(listOf(LikedProfile(profile, false), LikedProfile(second, true)))
        runCurrent()
        assertEquals(listOf("second"), viewModel.state.value.visibleLiked.map { it.profile.id })
    }

    @Test
    fun unlikeTimeoutRollsBackAndConsumedErrorIsNotReplayed() = runTest {
        coEvery { likes.setLiked("peer", false) } coAnswers { awaitCancellation() }
        val viewModel = liked()
        runCurrent()
        viewModel.unlikeProfile("peer")
        assertEquals(1, viewModel.state.value.visibleLiked.size)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(2, viewModel.state.value.visibleLiked.size)
        assertEquals(LikedEvent.ShowSnackbar("Beğeni geri çekilemedi. Lütfen tekrar deneyin."), viewModel.events.first())
        val events = mutableListOf<LikedEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        runCurrent()
        assertTrue(events.isEmpty())
    }

    @Test
    fun viewModelClearingCancelsSyncWithoutFailureNotification() = runTest {
        coEvery { likes.setLiked("peer", false) } coAnswers { awaitCancellation() }
        val viewModel = liked()
        runCurrent()
        viewModel.unlikeProfile("peer")
        store.clear()
        runCurrent()
        assertEquals(2, viewModel.state.value.visibleLiked.size)
        val events = mutableListOf<LikedEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun profileLikeIsImmediateAndConfirmedBeforeNavigation() = runTest {
        val response = CompletableDeferred<MatchResult>()
        coEvery { likes.setLiked("peer", true) } coAnswers { response.await() }
        val viewModel = details()
        runCurrent()
        viewModel.onLikeClicked()
        assertTrue(viewModel.state.value.isLiked)
        assertTrue(viewModel.state.value.isActionInFlight)
        assertFalse(viewModel.state.value.isActedOn)
        response.complete(MatchResult.SingleLike)
        runCurrent()
        assertTrue(viewModel.state.value.isLiked)
        assertTrue(viewModel.state.value.isActedOn)
        assertFalse(viewModel.state.value.isActionInFlight)
        assertEquals(ProfileDetailsEvent.ActionCompleted, viewModel.events.first())
    }

    @Test
    fun profileFailureRestoresLikeAndQuotaAndEmitsAnError() = runTest {
        val response = CompletableDeferred<MatchResult>()
        coEvery { likes.setLiked("peer", true) } coAnswers { response.await() }
        val viewModel = details()
        runCurrent()
        viewModel.onLikeClicked()
        assertTrue(viewModel.state.value.isLiked)
        response.completeExceptionally(IllegalStateException("Bağlantı hatası."))
        runCurrent()
        assertFalse(viewModel.state.value.isLiked)
        assertFalse(viewModel.state.value.isActionInFlight)
        assertEquals(2, viewModel.state.value.swipesUsedToday)
        assertEquals(ProfileDetailsEvent.ShowMessage("İşlem tamamlanamadı. Lütfen tekrar deneyin."), viewModel.events.first())
    }
}
