package com.mcclabs.mook.data.repository

import com.mcclabs.mook.domain.repository.LikedMeUnlockResult
import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.domain.repository.InteractionRepository
import com.mcclabs.mook.domain.repository.BoostSummary
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.mcclabs.mook.data.appFirestore
import com.mcclabs.mook.domain.billing.SwipeTimeoutFallbackHandler
import kotlinx.serialization.Serializable
import com.mcclabs.mook.util.Log

class InteractionRepositoryImpl(
    private val swipeTimeoutFallbackHandler: SwipeTimeoutFallbackHandler,
) : InteractionRepository {

    /**
     * Gereksinim 1 (Faz 6): ağ isteği [SwipeTimeoutFallbackHandler] tarafından sarmalanır --
     * sunucu [com.mcclabs.mook.domain.billing.BillingConfig.SWIPE_TIMEOUT_MILLIS] içinde
     * cevap vermezse ya da bağlantı koptuysa çağrı [com.mcclabs.mook.domain.model.MatchResult.QueuedOffline]
     * ile döner ve eylem kalıcı kuyruğa alınır -- bu iç `try/catch` bloğu HİÇBİR ZAMAN fırlatmaz,
     * her koşulda bir [MatchResult] döner (bkz. handler KDoc'u).
     */
    override suspend fun swipeUser(toUserId: String, isLike: Boolean): MatchResult =
        swipeTimeoutFallbackHandler.execute(profileId = toUserId, isLike = isLike) {
            try {
                Firebase.auth.currentUser?.uid
                    ?: return@execute MatchResult.Error("User not logged in")
                val response = appHttpsCallable("swipe")
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
        appHttpsCallable("rewind")
            .invoke()
            .data<RewindResponse>()
            .profileUid
    }

    override suspend fun unlockLikedMe(profileUid: String): LikedMeUnlockResult = try {
        val response = appHttpsCallable("unlockLikedMe")
            .invoke(LikedMeUnlockRequest(profileUid))
            .data<LikedMeUnlockResponse>()
        LikedMeUnlockResult.Unlocked(alreadyUnlocked = response.alreadyUnlocked)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e("Beni beğenenler profili açılamadı: $profileUid", error)
        mapLikedMeUnlockFailure(error.message)
    }

    override suspend fun activateBoost(): Result<Long> = runCatching {
        Firebase.auth.currentUser?.uid ?: error("User not logged in")
        appHttpsCallable("activateBoost")
            .invoke()
            .data<BoostResponse>()
            .boostUntil
    }

    /** Gereksinim 2.12 (Faz 4): bkz. [InteractionRepository.getBoostSummary] KDoc'u. */
    override suspend fun getBoostSummary(): Result<BoostSummary> = runCatching {
        Firebase.auth.currentUser?.uid ?: error("User not logged in")
        val response = appHttpsCallable("getBoostSummary").invoke().data<BoostSummaryResponse>()
        BoostSummary(viewsGained = response.viewsGained, boostUntilMillis = response.boostUntilMillis)
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

@Serializable
private data class LikedMeUnlockRequest(val profileUid: String)

@Serializable
private data class LikedMeUnlockResponse(val unlocked: Boolean = false, val alreadyUnlocked: Boolean = false)

/** `unlockLikedMe` sunucu hata kodlarını alan sonucuna çevirir (saf; birim testli). */
internal fun mapLikedMeUnlockFailure(message: String?): LikedMeUnlockResult {
    val text = message.orEmpty()
    return when {
        text.contains("daily-liked-me-limit", ignoreCase = true) -> LikedMeUnlockResult.DailyLimitReached
        text.contains("upgrade-required", ignoreCase = true) -> LikedMeUnlockResult.UpgradeRequired
        text.contains("not-liked-by-profile", ignoreCase = true) -> LikedMeUnlockResult.NotLikedByProfile
        else -> LikedMeUnlockResult.Failed(message)
    }
}

@Serializable
private data class BoostSummaryResponse(val viewsGained: Int = 0, val boostUntilMillis: Long? = null)
