package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.domain.model.LikedMeEntry
import com.mcclabs.mook.domain.model.LikedMePage
import com.mcclabs.mook.domain.model.LikedMeProfile
import com.mcclabs.mook.domain.repository.LikedMeRepository
import com.mcclabs.mook.domain.repository.LikedMeUnlockResult
import com.mcclabs.mook.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** [LikedMeRepository]'nin `getLikedMe` / `unlockLikedMe` callable'ları üzerinden uygulaması. */
class LikedMeRepositoryImpl : LikedMeRepository {

    override suspend fun load(): Result<LikedMePage> = try {
        Result.success(appHttpsCallable("getLikedMe").invoke().data<LikedMeResponse>().toDomain())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e("Beni beğenenler listesi yüklenemedi", error)
        Result.failure(error)
    }

    override suspend fun unlock(entryToken: String): LikedMeUnlockResult = try {
        val response = appHttpsCallable("unlockLikedMe")
            .invoke(UnlockRequest(entryToken))
            .data<UnlockResponse>()
        LikedMeUnlockResult.Unlocked(alreadyUnlocked = response.alreadyUnlocked)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e("Beni beğenenler girişinin kilidi açılamadı", error)
        mapLikedMeUnlockFailure(error.message)
    }
}

@Serializable
private data class UnlockRequest(val entryToken: String)

@Serializable
private data class UnlockResponse(val unlocked: Boolean = false, val alreadyUnlocked: Boolean = false)

@Serializable
private data class LikedMeResponse(
    val entries: List<LikedMeEntryDto> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val revealAll: Boolean = false,
    val unlocksRemainingToday: Int? = null,
) {
    fun toDomain() = LikedMePage(
        entries = entries.map { it.toDomain() },
        totalCount = totalCount,
        hasMore = hasMore,
        revealAll = revealAll,
        unlocksRemainingToday = unlocksRemainingToday,
    )
}

@Serializable
private data class LikedMeEntryDto(
    val entryToken: String,
    val likedAt: Long = 0L,
    val unlocked: Boolean = false,
    val photoUrl: String? = null,
    val profile: LikedMeProfileDto? = null,
) {
    fun toDomain() = LikedMeEntry(
        entryToken = entryToken,
        likedAtMillis = likedAt,
        // Sunucu açık dediği halde profil yoksa güvenli tarafta kalınır: kilitli göster.
        isUnlocked = unlocked && profile != null,
        photoUrl = photoUrl,
        profile = profile?.toDomain(),
    )
}

@Serializable
private data class LikedMeProfileDto(
    val uid: String,
    val displayName: String = "",
    val age: Int? = null,
    val countryCode: String? = null,
    val languageCode: String? = null,
    val bio: String = "",
    val verified: Boolean = false,
) {
    fun toDomain() = LikedMeProfile(uid, displayName, age, countryCode, languageCode, bio, verified)
}
