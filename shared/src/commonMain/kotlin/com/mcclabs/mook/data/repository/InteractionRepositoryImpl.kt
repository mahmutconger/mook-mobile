package com.mcclabs.mook.data.repository

import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.domain.repository.InteractionRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.mcclabs.mook.data.appFirestore
import dev.gitlive.firebase.functions.functions
import kotlinx.serialization.Serializable
import com.mcclabs.mook.util.Log

class InteractionRepositoryImpl : InteractionRepository {

    override suspend fun swipeUser(toUserId: String, isLike: Boolean): MatchResult {
        return try {
            Firebase.auth.currentUser?.uid
                ?: return MatchResult.Error("User not logged in")
            val response = Firebase.functions
                .httpsCallable("swipe")
                .invoke(SwipeRequest(toUserId = toUserId, isLike = isLike))
                .data<SwipeResponse>()
            when (response.result) {
                "mutual_match" -> MatchResult.MutualMatch
                "single_like" -> MatchResult.SingleLike
                "pass" -> MatchResult.Pass
                else -> MatchResult.Error("Unexpected swipe result")
            }

        } catch (e: Exception) {
            Log.e("Kaydırma başarısız: $toUserId (isLike=$isLike)", e)
            MatchResult.Error(e.message ?: "Unknown error occurred during swipe")
        }
    }

    override suspend fun checkMutualMatch(withUserId: String): Boolean {
        return try {
            val currentUserId = Firebase.auth.currentUser?.uid ?: return false
            val matchId = if (currentUserId < withUserId) "${currentUserId}_${withUserId}" else "${withUserId}_${currentUserId}"
            
            val matchDoc = appFirestore.collection("matches").document(matchId).get()
            matchDoc.exists
        } catch (e: Exception) {
            Log.e("Karşılıklı eşleşme kontrolünde hata (withUserId=$withUserId)", e)
            false
        }
    }

    override suspend fun rewindLastPass(): Result<String> = runCatching {
        Firebase.auth.currentUser?.uid ?: error("User not logged in")
        Firebase.functions
            .httpsCallable("rewind")
            .invoke()
            .data<RewindResponse>()
            .profileUid
    }

    override suspend fun activateBoost(): Result<Long> = runCatching {
        Firebase.auth.currentUser?.uid ?: error("User not logged in")
        Firebase.functions
            .httpsCallable("activateBoost")
            .invoke()
            .data<BoostResponse>()
            .boostUntil
    }
}

/** Payload and response names intentionally mirror `functions/src/monetization.ts`. */
@Serializable
private data class SwipeRequest(val toUserId: String, val isLike: Boolean)

@Serializable
private data class SwipeResponse(val result: String, val idempotent: Boolean = false)

@Serializable
private data class RewindResponse(val profileUid: String)

@Serializable
private data class BoostResponse(val boostUntil: Long)
