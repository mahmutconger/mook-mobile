package com.mcclabs.mook.sso

import androidx.lifecycle.SavedStateHandle
import com.mcclabs.mook.domain.sso.SsoAccount
import com.mcclabs.mook.domain.sso.SsoAuthRepository
import com.mcclabs.mook.domain.sso.SsoAuthorizationRequest
import com.mcclabs.mook.domain.sso.SsoDeliveryResult
import com.mcclabs.mook.domain.sso.SsoRejectionReason
import com.mcclabs.mook.domain.sso.SsoTokenFailure
import com.mcclabs.mook.domain.sso.SsoTokenResult
import com.mcclabs.mook.domain.sso.SsoVerificationResult
import com.mcclabs.mook.domain.sso.VerifiedSsoRequest
import com.mcclabs.mook.domain.sso.VerifyAuthCallbackUseCase
import com.mcclabs.mook.feature.sso.SsoAuthorizeEvent
import com.mcclabs.mook.feature.sso.SsoAuthorizeUiState
import com.mcclabs.mook.feature.sso.SsoAuthorizeViewModel
import com.mcclabs.mook.feature.sso.SsoBlockReason
import com.mcclabs.mook.feature.sso.SsoConsentError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SsoAuthorizeViewModel")
class SsoAuthorizeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val verifyAuthCallback = mockk<VerifyAuthCallbackUseCase>()
    private val repository = mockk<SsoAuthRepository>()

    private val verified = VerifiedSsoRequest(
        client = SsoFixtures.WALKTALK,
        redirectUri = SsoFixtures.REDIRECT_URI,
        state = SsoFixtures.VALID_STATE,
        initiatedByMook = true,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { verifyAuthCallback(any()) } returns SsoVerificationResult.Verified(verified)
        every { repository.currentAccount() } returns SsoAccount(email = "ada@example.com")
        coEvery { repository.mintSsoToken() } returns SsoTokenResult.Success(TOKEN)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        args: Map<String, Any?> = mapOf(
            "client_id" to SsoFixtures.CLIENT_ID,
            "redirect_uri" to SsoFixtures.REDIRECT_URI,
            "state" to SsoFixtures.VALID_STATE,
        ),
    ) = SsoAuthorizeViewModel(SavedStateHandle(args), verifyAuthCallback, repository)

    /** Olayları test boyunca toplar; ViewModel olayları tamponlu Channel ile yayınlar. */
    private fun TestScope.collectEvents(viewModel: SsoAuthorizeViewModel): List<SsoAuthorizeEvent> {
        val events = mutableListOf<SsoAuthorizeEvent>()
        backgroundScope.launch(dispatcher) { viewModel.events.collect(events::add) }
        return events
    }

    @Nested
    @DisplayName("Doğrulama")
    inner class Verification {

        @Test
        fun `navigasyon argümanları doğrulama için birebir iletilir`() = runTest(dispatcher) {
            createViewModel()

            coVerify(exactly = 1) {
                verifyAuthCallback(
                    SsoAuthorizationRequest(SsoFixtures.CLIENT_ID, SsoFixtures.REDIRECT_URI, SsoFixtures.VALID_STATE),
                )
            }
        }

        @Test
        fun `doğrulanan istek onay ekranını beyaz listedeki adla gösterir`() = runTest(dispatcher) {
            // İstekteki istemci adı ne olursa olsun ekrana beyaz listedeki ad yazılmalı.
            val viewModel = createViewModel(
                mapOf("client_id" to "walktalk", "redirect_uri" to SsoFixtures.REDIRECT_URI, "state" to SsoFixtures.VALID_STATE, "client" to "Bankanız"),
            )

            assertEquals(
                SsoAuthorizeUiState.AwaitingConsent(clientName = "WalkTalk", accountEmail = "ada@example.com"),
                viewModel.state.value,
            )
        }

        @Test
        fun `reddedilen istek sessizce engellenir ve asla token üretilmez`() = runTest(dispatcher) {
            coEvery { verifyAuthCallback(any()) } returns SsoVerificationResult.Rejected(SsoRejectionReason.REDIRECT_NOT_ALLOWED)
            val viewModel = createViewModel()

            assertEquals(SsoAuthorizeUiState.Blocked(SsoBlockReason.UNVERIFIED_REQUEST), viewModel.state.value)

            // Saldırgan, engellendikten sonra "İzin ver" çağrısını tetiklemeyi denese bile.
            viewModel.onAllowClicked()
            coVerify(exactly = 0) { repository.mintSsoToken() }
        }

        @Test
        fun `her güvenlik reddi aynı genel mesajı gösterir, yalnızca süre aşımı ayrılır`() = runTest(dispatcher) {
            val expected = mapOf(
                SsoRejectionReason.UNKNOWN_CLIENT to SsoBlockReason.UNVERIFIED_REQUEST,
                SsoRejectionReason.REDIRECT_NOT_ALLOWED to SsoBlockReason.UNVERIFIED_REQUEST,
                SsoRejectionReason.MALFORMED_STATE to SsoBlockReason.UNVERIFIED_REQUEST,
                SsoRejectionReason.STATE_MISMATCH to SsoBlockReason.UNVERIFIED_REQUEST,
                SsoRejectionReason.STATE_EXPIRED to SsoBlockReason.EXPIRED_REQUEST,
            )
            expected.forEach { (reason, blockReason) ->
                coEvery { verifyAuthCallback(any()) } returns SsoVerificationResult.Rejected(reason)
                assertEquals(SsoAuthorizeUiState.Blocked(blockReason), createViewModel().state.value, "$reason")
            }
        }

        @Test
        fun `oturum yoksa giriş ekranına yönlendirilir ve token üretilmez`() = runTest(dispatcher) {
            every { repository.currentAccount() } returns null
            // Olay, ekran dinlemeye başlamadan init içinde gönderilir; kaybolmamalı.
            val viewModel = createViewModel()
            val events = collectEvents(viewModel)

            assertEquals(listOf(SsoAuthorizeEvent.NavigateToLogin), events)
            assertEquals(SsoAuthorizeUiState.Verifying, viewModel.state.value)
            viewModel.onAllowClicked()
            coVerify(exactly = 0) { repository.mintSsoToken() }
        }
    }

    @Nested
    @DisplayName("Onay ve token teslimi")
    inner class Consent {

        @Test
        fun `izin verilince token yalnızca doğrulanmış adrese ve istemciye teslim edilmek üzere gönderilir`() = runTest(dispatcher) {
            val viewModel = createViewModel()
            val events = collectEvents(viewModel)

            viewModel.onAllowClicked()

            assertEquals(
                listOf(
                    SsoAuthorizeEvent.DeliverToClient(
                        redirectUrl = "${SsoFixtures.REDIRECT_URI}#token=$TOKEN&state=${SsoFixtures.VALID_STATE}",
                        client = SsoFixtures.WALKTALK,
                    ),
                ),
                events,
            )
        }

        @Test
        fun `token hiçbir zaman UI state'ine yazılmaz`() = runTest(dispatcher) {
            val viewModel = createViewModel()
            val states = mutableListOf<SsoAuthorizeUiState>()
            backgroundScope.launch(dispatcher) { viewModel.state.collect(states::add) }

            viewModel.onAllowClicked()
            viewModel.onDeliveryResult(SsoDeliveryResult.CLIENT_APP_UNAVAILABLE)

            assertTrue(states.isNotEmpty())
            states.forEach { assertFalse(it.toString().contains(TOKEN), "$it") }
        }

        @Test
        fun `çift dokunuş tek bir token üretir`() = runTest(dispatcher) {
            val pendingToken = CompletableDeferred<SsoTokenResult>()
            coEvery { repository.mintSsoToken() } coAnswers { pendingToken.await() }
            val viewModel = createViewModel()

            viewModel.onAllowClicked()
            viewModel.onAllowClicked()
            viewModel.onAllowClicked()
            pendingToken.complete(SsoTokenResult.Success(TOKEN))

            coVerify(exactly = 1) { repository.mintSsoToken() }
        }

        @Test
        fun `token üretilirken yükleniyor durumu gösterilir`() = runTest(dispatcher) {
            val pendingToken = CompletableDeferred<SsoTokenResult>()
            coEvery { repository.mintSsoToken() } coAnswers { pendingToken.await() }
            val viewModel = createViewModel()

            viewModel.onAllowClicked()

            assertTrue((viewModel.state.value as SsoAuthorizeUiState.AwaitingConsent).isAuthorizing)
        }

        @Test
        fun `başarılı teslimattan sonra ekran kapanır ve istek yeniden kullanılamaz`() = runTest(dispatcher) {
            val viewModel = createViewModel()
            val events = collectEvents(viewModel)

            viewModel.onAllowClicked()
            viewModel.onDeliveryResult(SsoDeliveryResult.DELIVERED)
            viewModel.onAllowClicked()

            assertEquals(SsoAuthorizeEvent.Close, events.last())
            coVerify(exactly = 1) { repository.mintSsoToken() }
        }

        @Test
        fun `reddedince ekran kapanır ve token üretilmez`() = runTest(dispatcher) {
            val viewModel = createViewModel()
            val events = collectEvents(viewModel)

            viewModel.onDenyClicked()
            viewModel.onAllowClicked()

            assertEquals(listOf(SsoAuthorizeEvent.Close), events)
            coVerify(exactly = 0) { repository.mintSsoToken() }
        }
    }

    @Nested
    @DisplayName("Hata yönetimi")
    inner class Errors {

        @Test
        fun `ağ hatası tekrar denenebilir hata gösterir ve tekrar deneme başarılı olur`() = runTest(dispatcher) {
            coEvery { repository.mintSsoToken() } returnsMany listOf(
                SsoTokenResult.Failure(SsoTokenFailure.NETWORK),
                SsoTokenResult.Success(TOKEN),
            )
            val viewModel = createViewModel()
            val events = collectEvents(viewModel)

            viewModel.onAllowClicked()
            assertEquals(
                SsoAuthorizeUiState.AwaitingConsent("WalkTalk", "ada@example.com", isAuthorizing = false, error = SsoConsentError.NETWORK),
                viewModel.state.value,
            )

            viewModel.onAllowClicked()
            assertEquals(null, (viewModel.state.value as SsoAuthorizeUiState.AwaitingConsent).error)
            assertTrue(events.single() is SsoAuthorizeEvent.DeliverToClient)
        }

        @Test
        fun `sunucu hatası tekrar denenebilir hata gösterir`() = runTest(dispatcher) {
            coEvery { repository.mintSsoToken() } returns SsoTokenResult.Failure(SsoTokenFailure.SERVER)
            val viewModel = createViewModel()

            viewModel.onAllowClicked()

            assertEquals(SsoConsentError.SERVER, (viewModel.state.value as SsoAuthorizeUiState.AwaitingConsent).error)
        }

        @Test
        fun `oturum süresi dolmuşsa giriş ekranına yönlendirilir`() = runTest(dispatcher) {
            coEvery { repository.mintSsoToken() } returns SsoTokenResult.Failure(SsoTokenFailure.SESSION_EXPIRED)
            val viewModel = createViewModel()
            val events = collectEvents(viewModel)

            viewModel.onAllowClicked()

            assertEquals(listOf(SsoAuthorizeEvent.NavigateToLogin), events)
        }

        @Test
        fun `istemci uygulama yoksa kullanıcı güncellemeye yönlendirilir ve tekrar deneyebilir`() = runTest(dispatcher) {
            val viewModel = createViewModel()

            viewModel.onAllowClicked()
            viewModel.onDeliveryResult(SsoDeliveryResult.CLIENT_APP_UNAVAILABLE)

            val state = viewModel.state.value as SsoAuthorizeUiState.AwaitingConsent
            assertEquals(SsoConsentError.CLIENT_APP_UNAVAILABLE, state.error)
            assertFalse(state.isAuthorizing)

            viewModel.onAllowClicked()
            coVerify(exactly = 2) { repository.mintSsoToken() }
        }

        @Test
        fun `imzası güvenilmeyen istemci uygulama engellenir ve tekrar deneme sunulmaz`() = runTest(dispatcher) {
            val viewModel = createViewModel()

            viewModel.onAllowClicked()
            viewModel.onDeliveryResult(SsoDeliveryResult.CLIENT_APP_UNTRUSTED)
            viewModel.onAllowClicked()

            assertEquals(SsoAuthorizeUiState.Blocked(SsoBlockReason.UNVERIFIED_REQUEST), viewModel.state.value)
            coVerify(exactly = 1) { repository.mintSsoToken() }
        }
    }

    private companion object {
        const val TOKEN = "eyJhbGciOiJSUzI1NiJ9.custom-token.signature"
    }
}
