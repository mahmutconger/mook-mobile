package com.mcclabs.mook.eula

import com.mcclabs.mook.domain.auth.LogoutUseCase
import com.mcclabs.mook.domain.repository.AuthRepository
import com.mcclabs.mook.feature.eula.EulaGateViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Kullanım şartları kapısında okuma zorunluluğu: sonuna kadar kaydırma + en kısa süre. */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("EULA kapısı okuma zorunluluğu")
class EulaGateReadingTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val logout = mockk<LogoutUseCase>(relaxed = true)

    @BeforeEach fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { auth.hasAcceptedEula() } returns false
        coEvery { auth.acceptEula() } just Runs
    }

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private val readMillis = EulaGateViewModel.MIN_READ_SECONDS * 1_000L

    @Test fun `okumadan onay kutusu işaretlenemez`() = runTest(dispatcher) {
        val vm = EulaGateViewModel(auth, logout)
        runCurrent()
        vm.onAcceptedChange(true)
        assertFalse(vm.state.value.accepted)
        assertFalse(vm.state.value.canAccept)
    }

    @Test fun `sonuna kadar kaydırmak süre dolmadan yetmez`() = runTest(dispatcher) {
        val vm = EulaGateViewModel(auth, logout)
        runCurrent()
        vm.onReadToEnd()
        vm.onAcceptedChange(true)
        assertFalse(vm.state.value.accepted)
    }

    @Test fun `süre dolsa da kaydırılmadan kabul edilemez`() = runTest(dispatcher) {
        val vm = EulaGateViewModel(auth, logout)
        runCurrent()
        advanceTimeBy(readMillis + 1)
        vm.onAcceptedChange(true)
        vm.onContinue()
        runCurrent()
        assertFalse(vm.state.value.accepted)
        coVerify(exactly = 0) { auth.acceptEula() }
    }

    @Test fun `okunup süre dolunca kabul edilir ve kaydedilir`() = runTest(dispatcher) {
        val vm = EulaGateViewModel(auth, logout)
        runCurrent()
        vm.onReadToEnd()
        advanceTimeBy(readMillis + 1)
        assertTrue(vm.state.value.canAccept)
        vm.onAcceptedChange(true)
        vm.onContinue()
        runCurrent()
        assertTrue(vm.state.value.accepted)
        coVerify(exactly = 1) { auth.acceptEula() }
    }
}
