package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.billing.PendingActionQueue
import com.mcclabs.mook.domain.billing.PendingSwipeAction
import com.mcclabs.mook.domain.billing.SwipeTimeoutFallbackHandler
import com.mcclabs.mook.domain.connectivity.ConnectivityObserver
import com.mcclabs.mook.domain.model.MatchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Gereksinim 1 (Faz 6): [SwipeTimeoutFallbackHandler]'ın SAF karar mantığını doğrular --
 * `swipe` çağrısı zaman aşımına uğradığında ya da bağlantı koptuğunda eylemin [PendingActionQueue]'ya
 * kuyruğa alınıp [MatchResult.QueuedOffline] döndüğünü, GERÇEK başarı/hata durumlarının ise
 * OLDUĞU GİBİ (kuyruğa dokunulmadan) döndüğünü garanti eder.
 */
class SwipeTimeoutFallbackHandlerTest {

    private val pendingActionQueue = mockk<PendingActionQueue>(relaxed = true)
    private val connectivityObserver = mockk<ConnectivityObserver>()

    private fun createHandler(isOnline: Boolean = true): SwipeTimeoutFallbackHandler {
        every { connectivityObserver.isOnline } returns MutableStateFlow(isOnline)
        return SwipeTimeoutFallbackHandler(pendingActionQueue, connectivityObserver)
    }

    @Nested
    @DisplayName("Sunucu zaman aşımı içinde cevap verdiğinde")
    inner class CallCompletesInTime {
        @Test
        @DisplayName("başarı sonucu olduğu gibi döner, kuyruğa hiç dokunulmaz")
        fun returnsSuccessUnchanged() = runTest {
            val handler = createHandler(isOnline = true)

            val result = handler.execute(profileId = "u1", isLike = true, timeoutMillis = 5_000L) {
                MatchResult.MutualMatch
            }

            assertEquals(MatchResult.MutualMatch, result)
            coVerify(exactly = 0) { pendingActionQueue.enqueue(any()) }
        }

        @Test
        @DisplayName("çevrimiçiyken gelen GERÇEK bir sunucu hatası kuyruğa alınmadan olduğu gibi döner")
        fun returnsRealServerErrorUnchangedWhenOnline() = runTest {
            val handler = createHandler(isOnline = true)

            val result = handler.execute(profileId = "u1", isLike = true, timeoutMillis = 5_000L) {
                MatchResult.Error("permission-denied")
            }

            assertTrue(result is MatchResult.Error)
            coVerify(exactly = 0) { pendingActionQueue.enqueue(any()) }
        }
    }

    @Nested
    @DisplayName("Sunucu zaman aşımı süresi içinde cevap VERMEDİĞİNDE")
    inner class CallTimesOut {
        @Test
        @DisplayName("eylemi kuyruğa alır ve QueuedOffline döner")
        fun queuesActionAndReturnsQueuedOffline() = runTest {
            val handler = createHandler(isOnline = true)

            val result = handler.execute(profileId = "u42", isLike = false, timeoutMillis = 100L) {
                delay(10_000L)
                MatchResult.MutualMatch
            }

            assertEquals(MatchResult.QueuedOffline, result)
            coVerify(exactly = 1) {
                pendingActionQueue.enqueue(match { it: PendingSwipeAction -> it.profileId == "u42" && !it.isLike })
            }
        }
    }

    @Nested
    @DisplayName("Bağlantı koptuğunda (çağrı bir hatayla tamamlansa bile)")
    inner class ConnectivityDropped {
        @Test
        @DisplayName("hatayı bir bağlantı kaybı olarak yorumlar: kuyruğa alır ve QueuedOffline döner")
        fun queuesActionWhenOffline() = runTest {
            val handler = createHandler(isOnline = false)

            val result = handler.execute(profileId = "u7", isLike = true, timeoutMillis = 5_000L) {
                MatchResult.Error("network error")
            }

            assertEquals(MatchResult.QueuedOffline, result)
            coVerify(exactly = 1) {
                pendingActionQueue.enqueue(match { it: PendingSwipeAction -> it.profileId == "u7" && it.isLike })
            }
        }
    }
}
