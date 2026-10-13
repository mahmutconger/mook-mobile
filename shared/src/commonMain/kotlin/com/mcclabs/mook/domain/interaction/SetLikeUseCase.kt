package com.mcclabs.mook.domain.interaction

import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.domain.repository.LikeRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SetLikeUseCase(private val repository: LikeRepository) {
    suspend operator fun invoke(profileId: String, isLiked: Boolean): MatchResult {
        require(profileId.isNotBlank()) { "Profil kimliği boş olamaz." }
        val result = try {
            withTimeout(10_000) { repository.setLiked(profileId, isLiked) }
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw IllegalStateException("İşlem zamanında tamamlanamadı. Lütfen tekrar deneyin.", timeout)
        }
        // Kuyruğa alınmış veya beklenen yönle uyuşmayan bir sonuç sunucu onayı sayılmaz.
        check(if (isLiked) result is MatchResult.SingleLike || result is MatchResult.MutualMatch
            else result is MatchResult.Pass) { "Beğeni durumu kaydedilemedi. Lütfen tekrar deneyin." }
        return result
    }
}
