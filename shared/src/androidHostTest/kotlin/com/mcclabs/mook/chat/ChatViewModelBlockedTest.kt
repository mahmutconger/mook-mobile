package com.mcclabs.mook.chat

import androidx.lifecycle.ViewModelStore
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.model.ChatSendError
import com.mcclabs.mook.domain.model.ChatSendException
import com.mcclabs.mook.domain.model.MessageStatus
import com.mcclabs.mook.domain.repository.ChatRepository
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.RoomSlotRepository
import com.mcclabs.mook.domain.repository.SendMessageResult
import com.mcclabs.mook.feature.chat.ChatViewModel
import com.mcclabs.mook.util.Log
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Sohbet ekranının engel durumu: kullanıcının kendi engeli (ekran açılırken okunur) ve
 * karşı tarafın engeli (yalnızca sunucunun `blocked-user` yanıtından öğrenilir).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelBlockedTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val chats = mockk<ChatRepository>(relaxed = true)
    private val discover = mockk<DiscoverRepository>(relaxed = true)
    private val roomSlots = mockk<RoomSlotRepository>(relaxed = true)
    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)
    private val analytics = mockk<AnalyticsRepository>(relaxed = true)
    private var nextMessageId = 0

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(Log)
        every { Log.e(any(), any()) } just Runs
        mockkStatic(com.google.firebase.auth.FirebaseAuth::class)
        val auth = mockk<com.google.firebase.auth.FirebaseAuth>()
        val user = mockk<com.google.firebase.auth.FirebaseUser>()
        every { com.google.firebase.auth.FirebaseAuth.getInstance() } returns auth
        every { auth.currentUser } returns user
        every { user.uid } returns "me"
        every { subscriptions.state } returns MutableStateFlow(EntitlementState(isResolved = true))
        every { chats.observeMessages(any()) } returns flowOf(emptyList())
        every { chats.newMessageId() } answers { "message-${nextMessageId++}" }
        coEvery { chats.getChatRoom(any()) } returns null
        coEvery { chats.hasBlocked(any()) } returns false
        coEvery { chats.sendMessage(any(), any(), any(), any(), any()) } returns SendMessageResult()
        coEvery { discover.getProfileDetails(any()) } returns null
    }

    @AfterEach
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun viewModel() = ChatViewModel(chats, discover, roomSlots, subscriptions, analytics, "me_peer", "peer")
        .also { store.put("chat", it) }

    private fun serverRejectsAsBlocked() {
        coEvery { chats.sendMessage(any(), any(), any(), any(), any()) } throws ChatSendException(ChatSendError.BLOCKED)
    }

    @Test
    @DisplayName("Engel yokken mesaj sunucuya gönderilir ve kompozer açık kalır")
    fun sendsWhenNobodyIsBlocked() = runTest(dispatcher) {
        val viewModel = viewModel()
        runCurrent()
        viewModel.onComposerTextChange("Merhaba")
        viewModel.onSend()
        runCurrent()

        assertFalse(viewModel.state.value.isBlocked)
        assertNull(viewModel.state.value.sendError)
        coVerify(exactly = 1) { chats.sendMessage("me_peer", "peer", "Merhaba", any(), "message-0") }
    }

    @Test
    @DisplayName("Sunucu blocked-user ile reddederse isBlocked açılır ve sonraki gönderimler reddedilir")
    fun serverBlockedErrorClosesTheComposer() = runTest(dispatcher) {
        serverRejectsAsBlocked()
        val viewModel = viewModel()
        runCurrent()
        assertFalse(viewModel.state.value.isBlocked)

        viewModel.onComposerTextChange("Merhaba")
        viewModel.onSend()
        runCurrent()

        val state = viewModel.state.value
        assertTrue(state.isBlocked)
        assertEquals(ChatSendError.BLOCKED, state.sendError)
        val failed = state.messages.single()
        assertEquals(MessageStatus.FAILED, failed.status)

        // Kompozer kapandıktan sonra ne yeni gönderim ne de yeniden deneme sunucuya gider.
        viewModel.onComposerTextChange("Tekrar")
        viewModel.onSend()
        viewModel.onMessageLongPressed(failed)
        viewModel.onRetry(failed)
        runCurrent()

        coVerify(exactly = 1) { chats.sendMessage(any(), any(), any(), any(), any()) }
        assertEquals("Tekrar", viewModel.state.value.composerText)
        assertNull(viewModel.state.value.actionTarget, "Reddedilen yeniden deneme eylem sayfasını kapatmalı")
        assertEquals(MessageStatus.FAILED, viewModel.state.value.messages.single().status)
    }

    @Test
    @DisplayName("Bildirimden açılan engelli sohbet: bayrak baştan açık, hiç gönderim denenmez")
    fun chatOpenedWithAPeerIBlockedNeverSends() = runTest(dispatcher) {
        coEvery { chats.hasBlocked("peer") } returns true
        val viewModel = viewModel()
        runCurrent()
        assertTrue(viewModel.state.value.isBlocked)

        viewModel.onComposerTextChange("Merhaba")
        viewModel.onSend()
        runCurrent()

        coVerify(exactly = 0) { chats.sendMessage(any(), any(), any(), any(), any()) }
        assertTrue(viewModel.state.value.messages.isEmpty())
        assertNull(viewModel.state.value.sendError)
    }

    @Test
    @DisplayName("Karşılıklı engel: çökme olmaz, gönderim yine yapılmaz")
    fun mutualBlockStaysClosedWithoutCrashing() = runTest(dispatcher) {
        // Kullanıcı karşı tarafı engelledi VE karşı taraf da onu engelledi (sunucu reddederdi).
        coEvery { chats.hasBlocked("peer") } returns true
        serverRejectsAsBlocked()
        val viewModel = viewModel()
        runCurrent()

        viewModel.onComposerTextChange("Merhaba")
        viewModel.onSend()
        runCurrent()

        assertTrue(viewModel.state.value.isBlocked)
        coVerify(exactly = 0) { chats.sendMessage(any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("Önce karşı taraf engelledi, sonra kullanıcı da engelledi: bayrak açık kalır")
    fun blockedBackAfterAServerRejectionStaysBlocked() = runTest(dispatcher) {
        serverRejectsAsBlocked()
        val first = viewModel()
        runCurrent()
        first.onComposerTextChange("Merhaba")
        first.onSend()
        runCurrent()
        assertTrue(first.state.value.isBlocked)

        // Kullanıcı karşılık olarak engeller ve sohbeti yeniden açar.
        coEvery { chats.hasBlocked("peer") } returns true
        val reopened = ChatViewModel(chats, discover, roomSlots, subscriptions, analytics, "me_peer", "peer")
            .also { store.put("reopened", it) }
        runCurrent()
        reopened.onComposerTextChange("Merhaba")
        reopened.onSend()
        runCurrent()

        assertTrue(reopened.state.value.isBlocked)
        coVerify(exactly = 1) { chats.sendMessage(any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("Engel dışındaki gönderim hataları kompozeri kapatmaz")
    fun otherSendErrorsDoNotBlockTheChat() = runTest(dispatcher) {
        coEvery { chats.sendMessage(any(), any(), any(), any(), any()) } throws ChatSendException(ChatSendError.RATE_LIMITED)
        val viewModel = viewModel()
        runCurrent()
        viewModel.onComposerTextChange("Merhaba")
        viewModel.onSend()
        runCurrent()

        assertFalse(viewModel.state.value.isBlocked)
        assertEquals(ChatSendError.RATE_LIMITED, viewModel.state.value.sendError)
    }
}
