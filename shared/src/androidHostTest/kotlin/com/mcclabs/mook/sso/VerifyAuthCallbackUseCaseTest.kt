package com.mcclabs.mook.sso

import com.mcclabs.mook.data.sso.StaticSsoClientRegistry
import com.mcclabs.mook.domain.sso.AuthStateStore
import com.mcclabs.mook.domain.sso.AuthStateValidator
import com.mcclabs.mook.domain.sso.EpochClock
import com.mcclabs.mook.domain.sso.PendingAuthState
import com.mcclabs.mook.domain.sso.SsoAuthorizationRequest
import com.mcclabs.mook.domain.sso.SsoRejectionReason
import com.mcclabs.mook.domain.sso.SsoVerificationResult
import com.mcclabs.mook.domain.sso.VerifiedSsoRequest
import com.mcclabs.mook.domain.sso.VerifyAuthCallbackUseCase
import com.mcclabs.mook.domain.sso.WhitelistCallbackValidator
import com.mcclabs.mook.domain.sso.buildSsoRedirectUrl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VerifyAuthCallbackUseCase")
class VerifyAuthCallbackUseCaseTest {

    private var now = SsoFixtures.NOW
    private val store = mockk<AuthStateStore>()
    private lateinit var useCase: VerifyAuthCallbackUseCase

    @BeforeEach
    fun setUp() {
        coEvery { store.read() } returns null
        coEvery { store.clear() } just runs
        useCase = VerifyAuthCallbackUseCase(
            registry = StaticSsoClientRegistry(listOf(SsoFixtures.WALKTALK, SsoFixtures.OTHER_CLIENT)),
            redirectValidator = WhitelistCallbackValidator(),
            stateValidator = AuthStateValidator(EpochClock { now }, ttlMillis = SsoFixtures.TTL),
            store = store,
        )
    }

    private fun request(
        clientId: String? = SsoFixtures.CLIENT_ID,
        redirectUri: String? = SsoFixtures.REDIRECT_URI,
        state: String? = SsoFixtures.VALID_STATE,
        mookState: String? = null,
    ) = SsoAuthorizationRequest(clientId, redirectUri, state, mookState)

    /** Mook'un ürettiği değer; istemcinin `state` değerinden bilinçli olarak farklıdır. */
    private val mookState = SsoFixtures.OTHER_VALID_STATE

    private fun pending(
        value: String = SsoFixtures.VALID_STATE,
        clientId: String = SsoFixtures.CLIENT_ID,
        issuedAt: Long = SsoFixtures.NOW,
    ) = PendingAuthState(value, clientId, issuedAt)

    private fun rejected(reason: SsoRejectionReason) = SsoVerificationResult.Rejected(reason)

    @Nested
    @DisplayName("Meşru istekler")
    inner class Legitimate {

        @Test
        fun `istemcinin başlattığı akış mook_state olmadan doğrulanır ve depolamaya dokunulmaz`() = runTest {
            val result = useCase(request())

            val verified = (result as SsoVerificationResult.Verified).request
            assertEquals(SsoFixtures.WALKTALK, verified.client)
            assertEquals(SsoFixtures.REDIRECT_URI, verified.redirectUri)
            assertEquals(SsoFixtures.VALID_STATE, verified.state)
            assertFalse(verified.initiatedByMook)
            confirmVerified(store)
        }

        @Test
        fun `istemcinin başlattığı akış, Mook'ta ilgisiz aktif bir kayıt olsa da engellenmez ve kayıt korunur`() = runTest {
            coEvery { store.read() } returns pending(value = mookState)

            assertTrue(useCase(request()) is SsoVerificationResult.Verified)
            confirmVerified(store)
        }

        @Test
        fun `Mook'un başlattığı akışta mook_state eşleşince doğrulanır ve kayıt tek kullanımlık olarak silinir`() = runTest {
            coEvery { store.read() } returns pending(value = mookState)
            now = SsoFixtures.NOW + 30_000

            val verified = (useCase(request(mookState = mookState)) as SsoVerificationResult.Verified).request

            assertTrue(verified.initiatedByMook)
            // Geri dönüşe istemcinin kendi state'i iletilir, Mook'un değeri değil.
            assertEquals(SsoFixtures.VALID_STATE, verified.state)
            coVerify(exactly = 1) { store.clear() }
        }
    }

    @Nested
    @DisplayName("Kötü niyetli istekler")
    inner class Malicious {

        @Test
        fun `bilinmeyen istemci reddedilir ve depolamaya dokunulmaz`() = runTest {
            assertEquals(rejected(SsoRejectionReason.UNKNOWN_CLIENT), useCase(request(clientId = "evil")))
            assertEquals(rejected(SsoRejectionReason.UNKNOWN_CLIENT), useCase(request(clientId = null)))
            assertEquals(rejected(SsoRejectionReason.UNKNOWN_CLIENT), useCase(request(clientId = "WALKTALK")))
            confirmVerified(store)
        }

        @Test
        fun `sahte alan adına yönlendirme reddedilir ve kullanıcının aktif akışı bozulmaz`() = runTest {
            coEvery { store.read() } returns pending(value = mookState)

            val result = useCase(
                request(redirectUri = "https://walktalkk.com.evil.com/sso-callback", mookState = mookState),
            )

            assertEquals(rejected(SsoRejectionReason.REDIRECT_NOT_ALLOWED), result)
            // Saldırgan bağlantısı meşru akışın kaydını ne okumalı ne de silmeli.
            confirmVerified(store)
        }

        @Test
        fun `eksik redirect_uri reddedilir`() = runTest {
            assertEquals(rejected(SsoRejectionReason.REDIRECT_NOT_ALLOWED), useCase(request(redirectUri = null)))
        }

        @Test
        fun `geçerli istemci kimliği başka istemcinin adresiyle birleştirilemez`() = runTest {
            val result = useCase(request(redirectUri = "https://partner.example.com/sso-callback"))
            assertEquals(rejected(SsoRejectionReason.REDIRECT_NOT_ALLOWED), result)
        }

        @Test
        fun `eksik ya da bozuk state reddedilir`() = runTest {
            assertEquals(rejected(SsoRejectionReason.MALFORMED_STATE), useCase(request(state = null)))
            assertEquals(rejected(SsoRejectionReason.MALFORMED_STATE), useCase(request(state = "")))
            assertEquals(rejected(SsoRejectionReason.MALFORMED_STATE), useCase(request(state = "abc")))
            assertEquals(
                rejected(SsoRejectionReason.MALFORMED_STATE),
                useCase(request(state = SsoFixtures.VALID_STATE + "#token=x")),
            )
            confirmVerified(store)
        }

        @Test
        fun `bozuk mook_state depolamaya dokunmadan reddedilir`() = runTest {
            assertEquals(rejected(SsoRejectionReason.MALFORMED_STATE), useCase(request(mookState = "")))
            assertEquals(rejected(SsoRejectionReason.MALFORMED_STATE), useCase(request(mookState = "abc&state=x")))
            confirmVerified(store)
        }

        @Test
        fun `farklı mook_state reddedilir ve meşru kayıt korunur`() = runTest {
            coEvery { store.read() } returns pending(value = mookState)

            val result = useCase(request(mookState = SsoFixtures.VALID_STATE))

            assertEquals(rejected(SsoRejectionReason.STATE_MISMATCH), result)
            coVerify(exactly = 0) { store.clear() }
        }

        @Test
        fun `tek karakteri değiştirilmiş mook_state reddedilir`() = runTest {
            coEvery { store.read() } returns pending(value = mookState)
            val tampered = mookState.dropLast(1) + "X"

            assertEquals(rejected(SsoRejectionReason.STATE_MISMATCH), useCase(request(mookState = tampered)))
        }

        @Test
        fun `başka istemci için üretilmiş mook_state bu istemcide kullanılamaz`() = runTest {
            coEvery { store.read() } returns pending(value = mookState, clientId = SsoFixtures.OTHER_CLIENT.clientId)

            assertEquals(rejected(SsoRejectionReason.STATE_MISMATCH), useCase(request(mookState = mookState)))
            coVerify(exactly = 0) { store.clear() }
        }

        @Test
        fun `eşleşen ama süresi dolmuş mook_state reddedilir ve kayıt silinir`() = runTest {
            coEvery { store.read() } returns pending(value = mookState)
            now = SsoFixtures.NOW + SsoFixtures.TTL + 1

            assertEquals(rejected(SsoRejectionReason.STATE_EXPIRED), useCase(request(mookState = mookState)))
            coVerify(exactly = 1) { store.clear() }
        }

        @Test
        fun `kullanılmış mook_state ile aynı bağlantı tekrar oynatılamaz`() = runTest {
            coEvery { store.read() } returnsMany listOf(pending(value = mookState), null)

            assertTrue(useCase(request(mookState = mookState)) is SsoVerificationResult.Verified)
            assertEquals(rejected(SsoRejectionReason.STATE_EXPIRED), useCase(request(mookState = mookState)))
        }
    }

    @Nested
    @DisplayName("buildSsoRedirectUrl")
    inner class RedirectBuilding {

        private val verified = VerifiedSsoRequest(
            client = SsoFixtures.WALKTALK,
            redirectUri = SsoFixtures.REDIRECT_URI,
            state = SsoFixtures.VALID_STATE,
            initiatedByMook = false,
        )

        @Test
        fun `token ve state sorgu yerine fragmanda taşınır`() {
            assertEquals(
                "https://walktalkk.com/sso-callback#token=abc.def-ghi&state=${SsoFixtures.VALID_STATE}",
                buildSsoRedirectUrl(verified, "abc.def-ghi"),
            )
        }

        @Test
        fun `token içindeki özel karakterler ikinci bir parametre enjekte edemez`() {
            val url = buildSsoRedirectUrl(verified, "x&state=attacker#y")

            assertEquals(
                "https://walktalkk.com/sso-callback#token=x%26state%3Dattacker%23y&state=${SsoFixtures.VALID_STATE}",
                url,
            )
            assertEquals(1, Regex("&state=").findAll(url).count())
        }
    }
}
