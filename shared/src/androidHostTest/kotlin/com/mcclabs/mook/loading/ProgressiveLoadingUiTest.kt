package com.mcclabs.mook.loading

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.*
import coil3.annotation.DelicateCoilApi
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.mcclabs.mook.domain.billing.AdConfigRepository
import com.mcclabs.mook.domain.billing.AdRemoteConfig
import com.mcclabs.mook.domain.model.*
import com.mcclabs.mook.feature.chatlist.*
import com.mcclabs.mook.feature.discover.*
import com.mcclabs.mook.feature.filters.*
import com.mcclabs.mook.feature.liked.*
import com.mcclabs.mook.feature.likedme.*
import com.mcclabs.mook.feature.profile.*
import com.mcclabs.mook.ui.loading.*
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.atomic.AtomicInteger

/** Gerçek ekranlarda metin görünürlüğü, bağımsız parıltı, hata ve Coil önbelleği doğrulanır. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(DelicateCoilApi::class)
class ProgressiveLoadingUiTest {
    @get:Rule val compose = createComposeRule()
    private val response = CompletableDeferred<Boolean>()
    private val alternateResponse = CompletableDeferred<Boolean>()
    private val fetchCount = AtomicInteger()
    private lateinit var imageLoader: ImageLoader
    private val photoUrl = "https://images.test/avatar.jpg"
    private val profile = DiscoverProfile("peer", "Deniz", 27, null, null, listOf(photoUrl), "Yürüyüş sever", emptyList())

    @Before
    fun setUp() {
        mockkStatic(com.google.firebase.auth.FirebaseAuth::class)
        val auth = mockk<com.google.firebase.auth.FirebaseAuth>()
        every { com.google.firebase.auth.FirebaseAuth.getInstance() } returns auth
        every { auth.currentUser } returns null
        imageLoader = ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components { add(object : Fetcher.Factory<Uri> {
                override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                    if (!data.toString().startsWith("https://images.test/")) return null
                    return Fetcher {
                        fetchCount.incrementAndGet()
                        check((if (data.toString().endsWith("alternate.jpg")) alternateResponse else response).await()) { "Görsel indirilemedi" }
                        ImageFetchResult(ColorImage(0xFF33AA55.toInt(), 128, 128, 65536), false, DataSource.NETWORK)
                    }
                }
            }) }
            .build()
        SingletonImageLoader.setUnsafe(imageLoader)
    }

    @After
    fun tearDown() {
        response.complete(false)
        alternateResponse.complete(false)
        imageLoader.shutdown()
        SingletonImageLoader.reset()
        stopKoin()
        unmockkAll()
    }

    @Test
    fun likedScreenKeepsTextVisibleUntilImageBytesArrive() {
        val state = MutableStateFlow(LikedUiState())
        val viewModel = mockk<LikedViewModel>(relaxed = true)
        every { viewModel.state } returns state
        every { viewModel.events } returns MutableSharedFlow()
        compose.setContent { LikedScreen({}, {}, {}, {}, {}, {}, viewModel) }
        assertSkeleton("Likes")
        compose.runOnIdle { state.value = LikedUiState(liked = listOf(LikedProfile(profile, false)), isLoading = false) }
        assertTextAndShimmer("Likes", "Deniz, 27")
        completeImage()
    }

    @Test
    fun likedMeScreenKeepsUnlockedNameAndLockedImageRules() {
        val state = MutableStateFlow(LikedMeUiState())
        val viewModel = mockk<LikedMeViewModel>(relaxed = true)
        every { viewModel.state } returns state
        every { viewModel.events } returns MutableSharedFlow()
        compose.setContent { LikedMeScreen({}, {}, {}, viewModel) }
        assertSkeleton("LikedMe")
        val entry = LikedMeEntry("token", 1L, true, photoUrl, LikedMeProfile("peer", "Deniz", 27, null, null, "", false))
        compose.runOnIdle { state.value = LikedMeUiState(isLoading = false, entries = listOf(entry), items = listOf(LikedMeListItem.Profile(entry))) }
        assertTextAndShimmer("LikedMe", "Deniz, 27")
        completeImage()
    }

    @Test
    fun chatScreenKeepsPreviewVisibleUntilAvatarArrives() {
        val state = MutableStateFlow(ChatListUiState())
        val viewModel = mockk<ChatListViewModel>(relaxed = true)
        every { viewModel.state } returns state
        compose.setContent { ChatListScreen({ _, _ -> }, {}, {}, {}, {}, viewModel) }
        assertSkeleton("Chats")
        compose.runOnIdle { state.value = ChatListUiState(rooms = listOf(ChatRoom("chat", "peer", "Deniz", photoUrl, "Merhaba")), isLoading = false) }
        assertTextAndShimmer("Chats", "Deniz")
        compose.onNodeWithText("Merhaba").assertIsDisplayed()
        completeImage()
    }

    @Test
    fun discoverScreenShowsCaptionWhilePhotoLoads() {
        val state = MutableStateFlow(DiscoverUiState(isLoading = true))
        val viewModel = mockk<DiscoverViewModel>(relaxed = true)
        every { viewModel.state } returns state
        every { viewModel.events } returns MutableSharedFlow()
        val filters = mockk<FiltersViewModel>(relaxed = true)
        every { filters.state } returns MutableStateFlow(FiltersUiState())
        val ads = mockk<AdConfigRepository>()
        every { ads.config } returns MutableStateFlow(AdRemoteConfig(adsEnabled = false))
        startKoin { modules(module { single { filters }; single { ads } }) }
        compose.setContent { DiscoverScreen(onNavigateToProfile = {}, viewModel = viewModel) }
        assertSkeleton("Discover")
        compose.runOnIdle { state.value = DiscoverUiState(profiles = listOf(profile), isLoading = false, endReached = true) }
        compose.onNodeWithTag("skeletonDiscover").assertDoesNotExist()
        compose.onAllNodes(hasText("Deniz", substring = true)).onFirst().assertIsDisplayed()
        compose.onNodeWithTag("imageShimmer", useUnmergedTree = true).assertIsDisplayed()
        completeImage()
    }

    @Test
    fun profileScreenShowsOwnAndVisitedDetailsWhileGalleryLoads() {
        val state = MutableStateFlow(ProfileDetailsUiState(isLoading = true))
        val viewModel = mockk<ProfileDetailsViewModel>(relaxed = true)
        every { viewModel.state } returns state
        every { viewModel.events } returns MutableSharedFlow()
        compose.setContent { ProfileDetailsScreen("peer", {}, viewModel = viewModel) }
        assertSkeleton("Profile")
        compose.runOnIdle { state.value = ProfileDetailsUiState(profile = profile, isLoading = false, isOwnProfile = true) }
        assertTextAndShimmer("Profile", "Deniz, 27", multipleImages = true)
        compose.runOnIdle { state.value = state.value.copy(isOwnProfile = false) }
        compose.onNodeWithText("Deniz, 27").assertIsDisplayed()
        completeImage()
    }

    @Test
    fun failedImageEndsShimmerAndKeepsText() {
        compose.setContent {
            Column { Text("Deniz"); ProgressiveAsyncImage(photoUrl, null, Modifier.size(56.dp)) }
        }
        compose.onNodeWithTag("imageShimmer", useUnmergedTree = true).assertIsDisplayed()
        response.complete(false)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("imageFallback", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("imageShimmer", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Deniz").assertIsDisplayed()
    }

    @Test
    fun cachedImageDoesNotDownloadAgainOrKeepShimmer() {
        val visible = mutableStateOf(true)
        compose.setContent {
            if (visible.value) ProgressiveAsyncImage(photoUrl, null, Modifier.size(56.dp))
        }
        compose.waitUntil(10_000) { fetchCount.get() == 1 }
        completeImage()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag("imageShimmer", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(1, fetchCount.get())
    }

    @Test
    fun missingImageDoesNotStartShimmerOrNetwork() {
        compose.setContent { ProgressiveAsyncImage(null, null, Modifier.size(56.dp)) }
        compose.onNodeWithTag("imageShimmer", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("imageFallback", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, fetchCount.get())
    }

    @Test
    fun imagePlaceholdersCompleteIndependently() {
        compose.setContent {
            Row {
                ProgressiveAsyncImage(photoUrl, null, Modifier.size(56.dp).testTag("firstImage"))
                ProgressiveAsyncImage("https://images.test/alternate.jpg", null, Modifier.size(56.dp).testTag("secondImage"))
            }
        }
        compose.onAllNodesWithTag("imageShimmer", useUnmergedTree = true).assertCountEquals(2)
        response.complete(true)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("imageShimmer", useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        compose.onNode(hasTestTag("imageShimmer") and hasAnyAncestor(hasTestTag("secondImage")), useUnmergedTree = true).assertIsDisplayed()
        alternateResponse.complete(true)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("imageShimmer", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun changingImageModelKeepsNewRequestPlaceholder() {
        val url = mutableStateOf(photoUrl)
        val completedUrls = mutableListOf<String>()
        compose.setContent {
            ProgressiveAsyncImage(url.value, null, Modifier.size(56.dp), onImageLoadChanged = { imageUrl, status ->
                if (status == ImageLoadStatus.Success) completedUrls.add(imageUrl)
            })
        }
        compose.waitUntil(10_000) { fetchCount.get() == 1 }
        compose.runOnIdle { url.value = "https://images.test/alternate.jpg" }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("imageShimmer", useUnmergedTree = true).fetchSemanticsNodes()
            fetchCount.get() == 2
        }
        response.complete(true)
        compose.onNodeWithTag("imageShimmer", useUnmergedTree = true).assertIsDisplayed()
        assertFalse(completedUrls.contains(photoUrl))
        alternateResponse.complete(true)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("imageShimmer", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() &&
                completedUrls.contains("https://images.test/alternate.jpg")
        }
        compose.onNodeWithTag("imageShimmer", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun assertSkeleton(layout: String) {
        compose.onNodeWithTag("skeleton$layout").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("shimmerPlaceholder", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    private fun assertTextAndShimmer(layout: String, text: String, multipleImages: Boolean = false) {
        compose.onNodeWithTag("skeleton$layout").assertDoesNotExist()
        compose.onNodeWithText(text).assertIsDisplayed()
        if (multipleImages) assertTrue(compose.onAllNodesWithTag("imageShimmer", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        else compose.onNodeWithTag("imageShimmer", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun completeImage() {
        response.complete(true)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("imageShimmer", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        compose.onAllNodesWithTag("imageFallback", useUnmergedTree = true).assertCountEquals(0)
    }
}
