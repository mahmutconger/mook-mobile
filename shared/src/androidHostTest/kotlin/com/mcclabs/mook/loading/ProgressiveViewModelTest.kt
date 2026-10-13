package com.mcclabs.mook.loading

import com.mcclabs.mook.domain.interaction.SetLikeUseCase
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.*
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.domain.model.*
import com.mcclabs.mook.domain.repository.*
import com.mcclabs.mook.feature.chatlist.ChatListViewModel
import com.mcclabs.mook.feature.discover.DiscoverViewModel
import com.mcclabs.mook.feature.liked.LikedViewModel
import com.mcclabs.mook.feature.likedme.LikedMeViewModel
import com.mcclabs.mook.feature.profile.ProfileDetailsViewModel
import com.mcclabs.mook.ui.loading.*
import com.mcclabs.mook.util.Log
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/** Domain verisi ve görsel sonuçları farklı zamanlarda geldiğinde tüm ekranların durumları. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressiveViewModelTest {
    private val repository = mockk<DiscoverRepository>(relaxed = true)
    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)
    private val interactions = mockk<InteractionRepository>(relaxed = true)
    private val chats = mockk<ChatRepository>(relaxed = true)
    private val connectivity = mockk<ConnectivityObserver>()
    private val profile = DiscoverProfile("peer", "Deniz", 27, null, null, listOf("photo-1", "photo-2"), "Yürüyüş sever", emptyList())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkObject(Log)
        every { Log.e(any(), any()) } just Runs
        every { subscriptions.state } returns MutableStateFlow(EntitlementState())
        every { connectivity.isOnline } returns MutableStateFlow(false)
        coEvery { repository.actedOnProfileIds() } returns emptySet()
        coEvery { repository.getLikeUsageToday() } returns LikeUsage()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun likedMovesFromLoadingToTextThenImageSuccess() = runTest {
        val response = CompletableDeferred<List<LikedProfile>>()
        coEvery { repository.getLikedProfiles() } coAnswers { response.await() }
        val viewModel = LikedViewModel(repository, SetLikeUseCase(mockk(relaxed = true)))
        assertEquals(ContentLoadingPhase.Loading, viewModel.state.value.loadingPhase)
        runCurrent()
        response.complete(listOf(LikedProfile(profile, false)))
        runCurrent()
        assertEquals("Deniz", viewModel.state.value.liked.single().profile.name)
        assertEquals(ContentLoadingPhase.ImagesLoading, viewModel.state.value.loadingPhase)
        viewModel.onImageLoadChanged("photo-1", ImageLoadStatus.Success)
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
        viewModel.onImageLoadChanged("removed-photo", ImageLoadStatus.Error)
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
    }

    @Test
    fun likedImageFailureEndsShimmerAndPreservesText() = runTest {
        coEvery { repository.getLikedProfiles() } returns listOf(LikedProfile(profile, false))
        val viewModel = LikedViewModel(repository, SetLikeUseCase(mockk(relaxed = true)))
        runCurrent()
        viewModel.onImageLoadChanged("photo-1", ImageLoadStatus.Error)
        assertFalse(viewModel.state.value.imagesLoading)
        assertEquals(setOf("photo-1"), viewModel.state.value.imageLoading.failedUrls)
        assertEquals("Deniz", viewModel.state.value.liked.single().profile.name)
        viewModel.onImageLoadChanged("photo-1", ImageLoadStatus.Loading)
        assertTrue(viewModel.state.value.imagesLoading)
        viewModel.onImageLoadChanged("photo-1", ImageLoadStatus.Success)
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
    }

    @Test
    fun likedMeMovesFromLoadingToTextThenImageSuccess() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        val likedMeRepository = mockk<LikedMeRepository>()
        val response = CompletableDeferred<Result<LikedMePage>>()
        coEvery { likedMeRepository.load() } coAnswers { response.await() }
        val viewModel = LikedMeViewModel(likedMeRepository, subscriptions, connectivity, mockk(relaxed = true))
        assertEquals(ContentLoadingPhase.Loading, viewModel.state.value.loadingPhase)
        runCurrent()
        response.complete(Result.success(LikedMePage(
            listOf(LikedMeEntry("token", 1L, true, "photo-1", LikedMeProfile("peer", "Deniz", 27, null, null, "", false))),
            1, false, true, null,
        )))
        runCurrent()
        assertEquals("Deniz", viewModel.state.value.entries.single().profile?.name)
        assertEquals(ContentLoadingPhase.ImagesLoading, viewModel.state.value.loadingPhase)
        viewModel.onImageLoadChanged("photo-1", ImageLoadStatus.Success)
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
    }

    @Test
    fun discoverPublishesTextBeforeSlowMetadataAndPreservesImageResults() = runTest {
        val settings = mockk<SettingsRepository>(relaxed = true)
        every { settings.observeSettings() } returns MutableStateFlow(MatchSettings())
        coEvery { settings.getSettings() } returns MatchSettings()
        val response = CompletableDeferred<List<DiscoverProfile>>()
        val metadata = CompletableDeferred<Boolean>()
        coEvery { repository.getDiscoverProfiles(any()) } coAnswers { response.await() }
        coEvery { settings.getHasSeenLikedMeTutorial() } coAnswers { metadata.await() }
        val viewModel = DiscoverViewModel(repository, interactions, settings, subscriptions,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk<AnalyticsRepository>(relaxed = true), SetLikeUseCase(mockk(relaxed = true)))
        assertEquals(ContentLoadingPhase.Loading, viewModel.state.value.loadingPhase)
        runCurrent()
        response.complete(listOf(profile))
        runCurrent()
        assertEquals("Deniz", viewModel.state.value.profiles.single().name)
        assertEquals(ContentLoadingPhase.ImagesLoading, viewModel.state.value.loadingPhase)
        viewModel.onImageLoadChanged("photo-1", ImageLoadStatus.Success)
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
        metadata.complete(true)
        runCurrent()
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
    }

    @Test
    fun chatsPublishMessagesAndEachPeerWithoutWaitingForSlowPeers() = runTest {
        val rooms = MutableSharedFlow<List<ChatRoom>>(extraBufferCapacity = 1)
        every { chats.observeChats() } returns rooms
        val response = CompletableDeferred<DiscoverProfile?>()
        val slowResponse = CompletableDeferred<DiscoverProfile?>()
        coEvery { repository.getProfileDetails("peer") } coAnswers { response.await() }
        coEvery { repository.getProfileDetails("slow") } coAnswers { slowResponse.await() }
        val viewModel = ChatListViewModel(chats, repository)
        assertEquals(ContentLoadingPhase.Loading, viewModel.state.value.loadingPhase)
        runCurrent()
        rooms.emit(listOf(ChatRoom("chat", "peer", lastMessage = "Merhaba"), ChatRoom("slow-chat", "slow")))
        runCurrent()
        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Merhaba", viewModel.state.value.rooms.first().lastMessage)
        response.complete(profile)
        runCurrent()
        assertEquals("Deniz", viewModel.state.value.rooms.first().peerName)
        assertEquals(ContentLoadingPhase.ImagesLoading, viewModel.state.value.loadingPhase)
        viewModel.onImageLoadChanged("photo-1", ImageLoadStatus.Success)
        slowResponse.complete(null)
        runCurrent()
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
        assertEquals("Deniz", viewModel.state.value.rooms.first().peerName)
    }

    @Test
    fun chatNewEmissionCancelsOldPeerRequests() = runTest {
        val rooms = MutableSharedFlow<List<ChatRoom>>(extraBufferCapacity = 1)
        every { chats.observeChats() } returns rooms
        coEvery { repository.getProfileDetails("slow") } coAnswers { awaitCancellation() }
        coEvery { repository.getProfileDetails("peer") } returns profile
        val viewModel = ChatListViewModel(chats, repository)
        runCurrent()
        rooms.emit(listOf(ChatRoom("old", "slow")))
        runCurrent()
        rooms.emit(listOf(ChatRoom("new", "peer")))
        runCurrent()
        assertEquals("new", viewModel.state.value.rooms.single().chatId)
        assertEquals("Deniz", viewModel.state.value.rooms.single().peerName)
    }

    @Test
    fun ownProfilePublishesTextAndCompletesEveryGalleryImage() = runTest {
        verifyProfileProgression(isOwnProfile = true)
    }

    @Test
    fun visitedProfilePublishesTextBeforeRelationshipMetadata() = runTest {
        verifyProfileProgression(isOwnProfile = false)
    }

    private suspend fun TestScope.verifyProfileProgression(isOwnProfile: Boolean) {
        mockkStatic(com.google.firebase.auth.FirebaseAuth::class)
        val auth = mockk<com.google.firebase.auth.FirebaseAuth>()
        val user = mockk<com.google.firebase.auth.FirebaseUser>()
        every { com.google.firebase.auth.FirebaseAuth.getInstance() } returns auth
        every { auth.currentUser } returns user
        every { user.uid } returns if (isOwnProfile) "peer" else "current"
        val response = CompletableDeferred<DiscoverProfile?>()
        val metadata = CompletableDeferred<Boolean>()
        coEvery { repository.getProfileDetails("peer") } coAnswers { response.await() }
        coEvery { interactions.checkMutualMatch("peer") } coAnswers { metadata.await() }
        val viewModel = ProfileDetailsViewModel(repository, interactions, subscriptions, chats, connectivity,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), "peer", SetLikeUseCase(mockk(relaxed = true)))
        assertEquals(ContentLoadingPhase.Loading, viewModel.state.value.loadingPhase)
        runCurrent()
        response.complete(profile)
        runCurrent()
        assertEquals("Deniz", viewModel.state.value.profile?.name)
        assertEquals(isOwnProfile, viewModel.state.value.isOwnProfile)
        assertEquals(ContentLoadingPhase.ImagesLoading, viewModel.state.value.loadingPhase)
        if (!isOwnProfile) assertFalse(viewModel.state.value.canAct)
        viewModel.onImageLoadChanged("photo-1", ImageLoadStatus.Success)
        assertTrue(viewModel.state.value.imagesLoading)
        viewModel.onImageLoadChanged("photo-2", ImageLoadStatus.Success)
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
        metadata.complete(false)
        runCurrent()
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
    }

    @Test
    fun emptyAndPhotolessListsFinishWithoutShimmer() = runTest {
        coEvery { repository.getLikedProfiles() } returns listOf(LikedProfile(profile.copy(photoUrls = listOf("", " ")), false))
        val viewModel = LikedViewModel(repository, SetLikeUseCase(mockk(relaxed = true)))
        runCurrent()
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
        coEvery { repository.getLikedProfiles() } returns emptyList()
        viewModel.load()
        runCurrent()
        assertTrue(viewModel.state.value.liked.isEmpty())
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
    }

    @Test
    fun likedDataFailureEmitsErrorWithoutImageLoading() = runTest {
        coEvery { repository.getLikedProfiles() } throws IllegalStateException("Bağlantı kurulamadı")
        val viewModel = LikedViewModel(repository, SetLikeUseCase(mockk(relaxed = true)))
        runCurrent()
        assertEquals(ContentLoadingPhase.Error, viewModel.state.value.loadingPhase)
        assertFalse(viewModel.state.value.imagesLoading)
    }

    @Test
    fun reloadCancelsOldLikedRequestAndPreservesNewestText() = runTest {
        coEvery { repository.getLikedProfiles() } coAnswers { awaitCancellation() }
        val viewModel = LikedViewModel(repository, SetLikeUseCase(mockk(relaxed = true)))
        runCurrent()
        coEvery { repository.getLikedProfiles() } returns listOf(LikedProfile(profile, false))
        viewModel.load()
        runCurrent()
        assertEquals("Deniz", viewModel.state.value.liked.single().profile.name)
        assertEquals(ContentLoadingPhase.ImagesLoading, viewModel.state.value.loadingPhase)
        viewModel.onImageLoadChanged("photo-1", ImageLoadStatus.Success)
        viewModel.load()
        runCurrent()
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
    }

    @Test
    fun emptyChatEmissionEndsInitialLoading() = runTest {
        every { chats.observeChats() } returns flowOf(emptyList())
        val viewModel = ChatListViewModel(chats, repository)
        runCurrent()
        assertTrue(viewModel.state.value.rooms.isEmpty())
        assertEquals(ContentLoadingPhase.Success, viewModel.state.value.loadingPhase)
    }

    @Test
    fun failingChatStreamEmitsErrorState() = runTest {
        every { chats.observeChats() } returns flow { throw IllegalStateException("Bağlantı kurulamadı") }
        val viewModel = ChatListViewModel(chats, repository)
        runCurrent()
        assertEquals(ContentLoadingPhase.Error, viewModel.state.value.loadingPhase)
    }

    @Test
    fun oneUnreadablePeerDoesNotDiscardOtherRooms() = runTest {
        every { chats.observeChats() } returns flowOf(listOf(ChatRoom("first", "peer"), ChatRoom("second", "failed")))
        coEvery { repository.getProfileDetails("peer") } returns profile
        coEvery { repository.getProfileDetails("failed") } throws IllegalStateException("Profil okunamadı")
        val viewModel = ChatListViewModel(chats, repository)
        runCurrent()
        assertEquals(2, viewModel.state.value.rooms.size)
        assertEquals("Deniz", viewModel.state.value.rooms.first().peerName)
        assertNull(viewModel.state.value.rooms.last().peerName)
        assertNull(viewModel.state.value.error)
        assertEquals(ContentLoadingPhase.ImagesLoading, viewModel.state.value.loadingPhase)
    }

    @Test
    fun imageReconciliationKeepsCachedSuccessAndRemovesStaleUrls() {
        val previous = ImageLoadingState().reconcile(listOf("photo-1", "photo-2"))
            .onResult("photo-1", ImageLoadStatus.Success)
        val next = previous.reconcile(listOf("photo-1", "photo-3"))
        assertEquals(ImageLoadStatus.Success, next.statuses["photo-1"])
        assertEquals(setOf("photo-1", "photo-3"), next.statuses.keys)
        assertTrue(next.imagesLoading)
        assertEquals(next, next.onResult("photo-2", ImageLoadStatus.Error))
        assertEquals(next, next.onResult("photo-1", ImageLoadStatus.Loading))
    }
}
