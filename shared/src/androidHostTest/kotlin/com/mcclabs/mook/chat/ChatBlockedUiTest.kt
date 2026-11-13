package com.mcclabs.mook.chat

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.model.ChatRoom
import com.mcclabs.mook.domain.model.ChatSendError
import com.mcclabs.mook.domain.model.ChatSendException
import com.mcclabs.mook.domain.repository.ChatRepository
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.RoomSlotRepository
import com.mcclabs.mook.domain.repository.SendMessageResult
import com.mcclabs.mook.domain.theme.ThemePreference
import com.mcclabs.mook.feature.chat.ChatScreen
import com.mcclabs.mook.feature.chat.ChatViewModel
import com.mcclabs.mook.ui.theme.AppTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.chat_blocked_notice
import mook.shared.generated.resources.chat_error_blocked
import mook.shared.generated.resources.chat_room_closed_read_only
import mook.shared.generated.resources.chat_send_cd
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Gerçek sohbet ekranında engel şeridi ve kompozerin kapanması doğrulanır. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ChatBlockedUiTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val chats = mockk<ChatRepository>(relaxed = true)
    private val discover = mockk<DiscoverRepository>(relaxed = true)
    private val roomSlots = mockk<RoomSlotRepository>(relaxed = true)
    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)

    // Beklenen metinler sabit yazılmaz, ekranın kullandığı kaynaklardan okunur: test
    // metnin kendisini değil, doğru metnin doğru durumda gösterildiğini doğrular.
    private lateinit var blockedNotice: String
    private lateinit var roomClosedNotice: String
    private lateinit var blockedError: String
    private lateinit var sendLabel: String

    @Before
    fun setUp() {
        mockkStatic(com.google.firebase.auth.FirebaseAuth::class)
        val auth = mockk<com.google.firebase.auth.FirebaseAuth>()
        val user = mockk<com.google.firebase.auth.FirebaseUser>()
        every { com.google.firebase.auth.FirebaseAuth.getInstance() } returns auth
        every { auth.currentUser } returns user
        every { user.uid } returns "me"
        every { subscriptions.state } returns MutableStateFlow(EntitlementState(isResolved = true))
        every { chats.observeMessages(any()) } returns flowOf(emptyList())
        every { chats.newMessageId() } returns "message-1"
        coEvery { chats.getChatRoom(any()) } returns null
        coEvery { chats.hasBlocked(any()) } returns false
        coEvery { chats.sendMessage(any(), any(), any(), any(), any()) } returns SendMessageResult()
        coEvery { discover.getProfileDetails(any()) } returns null
    }

    @After
    fun tearDown() {
        store.clear()
        unmockkAll()
    }

    private fun showChat() {
        compose.setContent {
            blockedNotice = stringResource(Res.string.chat_blocked_notice)
            roomClosedNotice = stringResource(Res.string.chat_room_closed_read_only)
            blockedError = stringResource(Res.string.chat_error_blocked)
            sendLabel = stringResource(Res.string.chat_send_cd)
            AppTheme(ThemePreference.Light, systemDark = false) {
                ChatScreen(
                    chatId = "me_peer",
                    peerUid = "peer",
                    onNavigateBack = {},
                    viewModel = ChatViewModel(
                        chats, discover, roomSlots, subscriptions, mockk<AnalyticsRepository>(relaxed = true), "me_peer", "peer",
                    ).also { store.put("chat", it) },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun assertComposerIsGone() {
        compose.onNodeWithContentDescription(sendLabel).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun openChatShowsAnEnabledComposerAndNoBlockedNotice() {
        showChat()

        compose.onNodeWithText(blockedNotice).assertDoesNotExist()
        compose.onNodeWithContentDescription(sendLabel).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Merhaba")
        compose.onNodeWithContentDescription(sendLabel).assertIsEnabled()
    }

    @Test
    fun chatWithAPeerIBlockedShowsTheNoticeInsteadOfTheComposer() {
        // Bildirim/derin bağlantıyla açılan mevcut bir sohbet: kullanıcı bu kişiyi engellemiş.
        coEvery { chats.hasBlocked("peer") } returns true
        showChat()

        compose.onNodeWithText(blockedNotice).assertIsDisplayed()
        // Kesme işareti ekranda ters eğik çizgisiz görünmeli (Compose kaynakları bu kaçışı çözmez).
        org.junit.Assert.assertTrue(blockedNotice, "can't" in blockedNotice && !blockedNotice.contains(92.toChar()))
        assertComposerIsGone()
        coVerify(exactly = 0) { chats.sendMessage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun serverBlockedRejectionReplacesTheComposerWithTheNotice() {
        // Karşı taraf kullanıcıyı engellemiş: istemci bunu ilk gönderimin yanıtından öğrenir.
        coEvery { chats.sendMessage(any(), any(), any(), any(), any()) } throws ChatSendException(ChatSendError.BLOCKED)
        showChat()

        compose.onNode(hasSetTextAction()).performTextInput("Merhaba")
        compose.onNodeWithContentDescription(sendLabel).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(blockedNotice).assertIsDisplayed()
        compose.onNodeWithText(blockedError).assertIsDisplayed()
        assertComposerIsGone()
        coVerify(exactly = 1) { chats.sendMessage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun blockedNoticeTakesPrecedenceOverTheClosedRoomNotice() {
        coEvery { chats.hasBlocked("peer") } returns true
        coEvery { chats.getChatRoom("me_peer") } returns ChatRoom(chatId = "me_peer", peerUid = "peer", roomLanguageCode = "tr")
        coEvery { roomSlots.openRooms() } returns emptyList()
        showChat()

        compose.onNodeWithText(blockedNotice).assertIsDisplayed()
        compose.onNodeWithText(roomClosedNotice).assertDoesNotExist()
        assertComposerIsGone()
    }
}
