package com.mcclabs.mook.moderation

import com.mcclabs.mook.domain.auth.LogoutResult
import com.mcclabs.mook.domain.auth.LogoutUseCase
import com.mcclabs.mook.domain.moderation.UserModerationUseCase
import com.mcclabs.mook.domain.repository.ModerationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Gereksinim 1.13: [UserModerationUseCase]'in yasaklama durumunu doğru şekilde dışarı
 * verdiğini VE zorla çıkışı uygulamanın tek çıkış noktası olan [LogoutUseCase] üzerinden
 * (FCM jetonu → RevenueCat → signOut sırasıyla) yaptığını doğrular. Sıranın kendisi
 * `LogoutUseCaseTest` içinde test edilir.
 */
@DisplayName("UserModerationUseCase")
class UserModerationUseCaseTest {

    @Test
    fun `isBanned dogrudan ModerationRepository'den gelir`() {
        val bannedFlow = MutableStateFlow(true)
        val moderationRepository = mockk<ModerationRepository> { every { isBanned } returns bannedFlow }
        val useCase = UserModerationUseCase(moderationRepository, mockk())

        assertEquals(true, useCase.isBanned.value)
    }

    @Test
    fun `enforceBan tam cikis akisini tam olarak bir kez calistirir`() = runBlocking {
        val moderationRepository = mockk<ModerationRepository> { every { isBanned } returns MutableStateFlow(true) }
        val logoutUseCase = mockk<LogoutUseCase>()
        coEvery { logoutUseCase.invoke() } returns LogoutResult(pushTokenRemoved = true, billingIdentityDetached = true)

        UserModerationUseCase(moderationRepository, logoutUseCase).enforceBan()

        coVerify(exactly = 1) { logoutUseCase.invoke() }
    }
}
