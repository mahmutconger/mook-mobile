package com.mcclabs.mook.moderation

import com.mcclabs.mook.domain.model.ChatRoom
import com.mcclabs.mook.domain.moderation.blockedUidsFrom
import com.mcclabs.mook.domain.moderation.canComposeInChat
import com.mcclabs.mook.domain.moderation.withoutBlockedPeers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Engelleme kurallarının istemci tarafı: sohbet listesi filtresi ve sohbet ekranındaki
 * yazma kilidi. Sunucu tarafı `functions/test/blocking.test.js` içinde test edilir.
 */
class BlockedPeersTest {

    private fun room(chatId: String, peerUid: String) = ChatRoom(chatId = chatId, peerUid = peerUid)

    @Test
    fun missingOrEmptyBlockListMeansNobodyIsBlocked() {
        assertTrue(blockedUidsFrom(null).isEmpty())
        assertTrue(blockedUidsFrom(emptyList()).isEmpty())
    }

    @Test
    fun blankEntriesAreIgnoredAndDuplicatesCollapse() {
        assertEquals(setOf("uidB", "uidC"), blockedUidsFrom(listOf("uidB", "", "  ", "uidC", "uidB")))
    }

    @Test
    fun chatsWithBlockedPeersAreRemoved() {
        val rooms = listOf(room("a_b", "uidB"), room("a_c", "uidC"), room("a_d", "uidD"))

        val visible = rooms.withoutBlockedPeers(setOf("uidC"))

        assertEquals(listOf("a_b", "a_d"), visible.map { it.chatId })
    }

    @Test
    fun orderIsPreservedAfterFiltering() {
        // Liste zaten son mesaja göre sıralı gelir; filtre bu sırayı bozmamalı.
        val rooms = listOf(room("1", "uid1"), room("2", "uid2"), room("3", "uid3"), room("4", "uid4"))

        assertEquals(listOf("1", "3"), rooms.withoutBlockedPeers(setOf("uid2", "uid4")).map { it.chatId })
    }

    @Test
    fun emptyBlockListReturnsTheSameList() {
        val rooms = listOf(room("a_b", "uidB"))

        assertSame(rooms, rooms.withoutBlockedPeers(emptySet()))
    }

    @Test
    fun composerIsOpenOnlyWhenTheRoomIsOpenAndNobodyIsBlocked() {
        assertTrue(canComposeInChat(isReadOnly = false, isBlocked = false))
        assertFalse(canComposeInChat(isReadOnly = true, isBlocked = false))
        assertFalse(canComposeInChat(isReadOnly = false, isBlocked = true))
        assertFalse(canComposeInChat(isReadOnly = true, isBlocked = true))
    }
}
