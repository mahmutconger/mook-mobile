package com.mcclabs.mook.data.repository

import com.mcclabs.mook.domain.model.Interaction
import com.mcclabs.mook.domain.model.Match
import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.domain.repository.InteractionRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.mcclabs.mook.data.appFirestore
import kotlinx.datetime.Clock
import com.mcclabs.mook.util.Log

class InteractionRepositoryImpl : InteractionRepository {

    override suspend fun swipeUser(toUserId: String, isLike: Boolean): MatchResult {
        return try {
            val currentUserId = Firebase.auth.currentUser?.uid
                ?: return MatchResult.Error("User not logged in")

            val db = appFirestore
            val type = if (isLike) "like" else "pass"
            val timestamp = com.mcclabs.mook.util.getCurrentTimeMillis()
            val interactionId = "${currentUserId}_${toUserId}"

            Log.d("Kaydırma: $type -> $toUserId (doküman: interactions/$interactionId)")

            // 1. Save the interaction
            val interaction = Interaction(
                id = interactionId,
                fromUserId = currentUserId,
                toUserId = toUserId,
                type = type,
                timestamp = timestamp
            )
            db.collection("interactions").document(interactionId).set(interaction)

            // 2. If it's a pass, return immediately
            if (!isLike) {
                return MatchResult.Pass
            }

            // 3. If it's a like, check if the other user has already liked us
            val reverseInteractionId = "${toUserId}_${currentUserId}"
            val reverseInteractionDoc = db.collection("interactions").document(reverseInteractionId).get()
            
            if (reverseInteractionDoc.exists) {
                val reverseType = reverseInteractionDoc.get<String>("type")
                Log.d("Karşı taraf ($toUserId) beni '$reverseType' geçmiş")
                if (reverseType == "like") {
                    // It's a mutual match! Create a match document.
                    val matchId = if (currentUserId < toUserId) "${currentUserId}_${toUserId}" else "${toUserId}_${currentUserId}"
                    val match = Match(
                        id = matchId,
                        users = listOf(currentUserId, toUserId),
                        timestamp = timestamp
                    )
                    db.collection("matches").document(matchId).set(match)
                    Log.d("EŞLEŞME! matches/$matchId yazıldı")

                    return MatchResult.MutualMatch
                }
            } else {
                Log.d("Karşı taraf ($toUserId) beni henüz kaydırmamış")
            }

            MatchResult.SingleLike

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
}
