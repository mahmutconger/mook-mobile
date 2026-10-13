package com.mcclabs.mook.optimistic

import com.mcclabs.mook.domain.interaction.SetLikeUseCase
import com.mcclabs.mook.domain.model.MatchResult
import com.mcclabs.mook.domain.repository.LikeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SetLikeUseCaseTest {
    private fun useCase(sync: suspend (String, Boolean) -> MatchResult) = SetLikeUseCase(object : LikeRepository {
        override suspend fun setLiked(profileId: String, isLiked: Boolean) = sync(profileId, isLiked)
    })

    @Test
    fun sendsTheRequestedStateAndAcceptsConfirmedResults() = runTest {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val action = useCase { id, liked ->
            calls += id to liked
            if (liked) MatchResult.SingleLike else MatchResult.Pass
        }
        assertEquals(MatchResult.SingleLike, action("peer", true))
        assertEquals(MatchResult.Pass, action("peer", false))
        assertEquals(listOf("peer" to true, "peer" to false), calls)
    }

    @Test
    fun backendExceptionsPropagateToTheOptimisticController() = runTest {
        val action = useCase { _, _ -> error("İzin reddedildi.") }
        val error = assertFailsWith<IllegalStateException> { action("peer", true) }
        assertEquals("İzin reddedildi.", error.message)
    }

    @Test
    fun queuedErrorsAndWrongDirectionAreNeverTreatedAsConfirmation() = runTest {
        for (result in listOf(MatchResult.QueuedOffline, MatchResult.Error("Bağlantı hatası."), MatchResult.Pass)) {
            assertFailsWith<IllegalStateException> { useCase { _, _ -> result }("peer", true) }
        }
        assertFailsWith<IllegalStateException> { useCase { _, _ -> MatchResult.SingleLike }("peer", false) }
    }

    @Test
    fun timeoutBecomesARecoverableFailure() = runTest {
        val action = useCase { _, _ -> CompletableDeferred<MatchResult>().await() }
        val error = assertFailsWith<IllegalStateException> { action("peer", true) }
        assertEquals("İşlem zamanında tamamlanamadı. Lütfen tekrar deneyin.", error.message)
        assertEquals(10_000L, testScheduler.currentTime)
    }

    @Test
    fun blankProfileIdDoesNotReachTheRepository() = runTest {
        assertFailsWith<IllegalArgumentException> { useCase { _, _ -> error("Beklenmeyen çağrı.") }(" ", true) }
    }
}
