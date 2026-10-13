package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.domain.repository.LikeRepository
import kotlinx.serialization.Serializable

/** Etkileşim yazımları Firestore kuralları gereği yalnızca yetkili callable üzerinden yapılır. */
class FirebaseLikeRepository : LikeRepository {
    override suspend fun setLiked(profileId: String, isLiked: Boolean): MatchResult {
        if (!isLiked) {
            val response = appHttpsCallable("unlike").invoke(UnlikeRequest(profileId)).data<UnlikeResponse>()
            check(response.unliked) { "Beğeni geri çekilemedi. Lütfen tekrar deneyin." }
            return MatchResult.Pass
        }
        val response = appHttpsCallable("swipe")
            .invoke(LikeRequest(profileId, isLike = true)).data<LikeResponse>()
        return when (response.result) {
            "single_like" -> MatchResult.SingleLike
            "mutual_match" -> MatchResult.MutualMatch
            else -> error("Beğeni kaydedilemedi. Lütfen tekrar deneyin.")
        }
    }
}

@Serializable
private data class LikeRequest(val toUserId: String, val isLike: Boolean)

@Serializable
private data class LikeResponse(val result: String)

@Serializable
private data class UnlikeRequest(val toUserId: String)

@Serializable
private data class UnlikeResponse(val unliked: Boolean)
