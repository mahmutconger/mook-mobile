package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.data.appFirestore
import com.mcclabs.mook.domain.model.RoomUsage
import com.mcclabs.mook.domain.repository.RoomSlotRepository
import com.mcclabs.mook.util.Log
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.serialization.Serializable

/** Payload of the `closeRoomSlots` callable. Field names must match `functions/src/index.ts`. */
@Serializable
private data class CloseRoomSlotsRequest(val roomCodesToClose: List<String>)

/**
 * Firestore/callable-backed [RoomSlotRepository] (Gereksinim 1.7).
 *
 * [openRooms] reads directly from the user's own `users/{uid}` document — allowed by
 * the security rules (`allow get: if isMe(uid)`), the same way [SettingsRepositoryImpl]
 * reads `roomLanguageCode`. [closeRooms] goes through the `closeRoomSlots` callable
 * instead of a direct write: `roomLanguageCodes`/`roomLastActiveAt` are in the
 * client-write-protected field list in `firestore.rules`, so only server logic (which
 * also picks a sane new active room) may change them.
 */
class RoomSlotRepositoryImpl : RoomSlotRepository {

    override suspend fun openRooms(): List<RoomUsage> {
        val uid = Firebase.auth.currentUser?.uid ?: return emptyList()
        return try {
            val document = appFirestore.collection("users").document(uid).get()
            val codes = runCatching { document.get<List<String>>("roomLanguageCodes") }.getOrNull()
                ?: runCatching { document.get<String?>("roomLanguageCode") }.getOrNull()?.let { listOf(it) }
                ?: emptyList()
            val lastActiveAt = runCatching { document.get<Map<String, Long>>("roomLastActiveAt") }
                .getOrNull() ?: emptyMap()
            codes.map { code -> RoomUsage(code = code, lastActiveAtMillis = lastActiveAt[code] ?: 0L) }
        } catch (e: Exception) {
            Log.e("Açık odalar okunamadı", e)
            emptyList()
        }
    }

    override suspend fun closeRooms(codes: List<String>) {
        if (codes.isEmpty()) return
        appHttpsCallable("closeRoomSlots")
            .invoke(CloseRoomSlotsRequest(roomCodesToClose = codes))
    }
}
