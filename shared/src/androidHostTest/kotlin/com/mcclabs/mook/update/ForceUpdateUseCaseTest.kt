package com.mcclabs.mook.update

import com.mcclabs.mook.domain.model.UpdateState
import com.mcclabs.mook.domain.repository.UpdateRepository
import com.mcclabs.mook.domain.update.ForceUpdateUseCase
import com.mcclabs.mook.domain.update.InAppUpdateGateway
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Kritik altyapı Gereksinimi (Zorla Güncelleme): [ForceUpdateUseCase] — [UpdateRepository]
 * (Remote Config karşılaştırması) ile [InAppUpdateGateway]'i (Play Core akışı) doğru
 * şekilde birleştirdiğini doğrular. Play Core'un kendisi (gerçek SDK çağrısı)
 * `androidMain`deki `AndroidInAppUpdateGateway`de yaşar ve burada MOCK'lanır — bu test
 * yalnızca UseCase'in SAF birleştirme/yönlendirme mantığını hedefler.
 */
class ForceUpdateUseCaseTest {

    private val updateRepository = mockk<UpdateRepository>()
    private val inAppUpdateGateway = mockk<InAppUpdateGateway>()
    private val updateStateFlow = MutableStateFlow<UpdateState>(UpdateState.None)

    private fun createUseCase(): ForceUpdateUseCase {
        every { updateRepository.updateState } returns updateStateFlow
        return ForceUpdateUseCase(updateRepository, inAppUpdateGateway)
    }

    @Nested
    @DisplayName("evaluate()")
    inner class Evaluate {

        @Test
        @DisplayName("UpdateRepository.checkUpdateStatus()'a doğrudan delege eder")
        fun `delegates to repository check`() = runTest {
            val useCase = createUseCase()
            coEvery { updateRepository.checkUpdateStatus() } returns UpdateState.ForceUpdateRequired

            val result = useCase.evaluate()

            assertEquals(UpdateState.ForceUpdateRequired, result)
            coVerify(exactly = 1) { updateRepository.checkUpdateStatus() }
        }

        @Test
        @DisplayName("Remote Config henüz eşiği aşmadıysa None döner")
        fun `returns none when no threshold crossed`() = runTest {
            val useCase = createUseCase()
            coEvery { updateRepository.checkUpdateStatus() } returns UpdateState.None

            assertEquals(UpdateState.None, useCase.evaluate())
        }
    }

    @Nested
    @DisplayName("updateState")
    inner class UpdateStateProperty {

        @Test
        @DisplayName("UpdateRepository'nin StateFlow'unu birebir yansıtır")
        fun `mirrors repository state flow`() = runTest {
            val useCase = createUseCase()

            assertEquals(UpdateState.None, useCase.updateState.first())
            updateStateFlow.value = UpdateState.ForceUpdateRequired
            assertEquals(UpdateState.ForceUpdateRequired, useCase.updateState.first())
        }
    }

    @Nested
    @DisplayName("startImmediateUpdate()")
    inner class StartImmediateUpdate {

        @Test
        @DisplayName("Play Core akışı başarıyla başlatılabildiğinde true döner")
        fun `returns true when flow starts`() = runTest {
            val useCase = createUseCase()
            coEvery { inAppUpdateGateway.startImmediateUpdate() } returns true

            assertTrue(useCase.startImmediateUpdate())
            coVerify(exactly = 1) { inAppUpdateGateway.startImmediateUpdate() }
        }

        @Test
        @DisplayName("Gateway başlatamazsa (ör. iOS no-op, Play Store yok) false döner")
        fun `returns false when gateway cannot start flow`() = runTest {
            val useCase = createUseCase()
            coEvery { inAppUpdateGateway.startImmediateUpdate() } returns false

            assertFalse(useCase.startImmediateUpdate())
        }
    }
}
