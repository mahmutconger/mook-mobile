package com.mcclabs.mook.sso

import com.mcclabs.mook.data.sso.platformSecureRandomBytes
import com.mcclabs.mook.domain.sso.AuthStateStore
import com.mcclabs.mook.domain.sso.AuthStateValidator
import com.mcclabs.mook.domain.sso.EpochClock
import com.mcclabs.mook.domain.sso.GenerateAuthStateUseCase
import com.mcclabs.mook.domain.sso.PendingAuthState
import com.mcclabs.mook.domain.sso.SecureRandomSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AuthStateTest {

    private var now = SsoFixtures.NOW
    private val clock = EpochClock { now }

    @Nested
    @DisplayName("GenerateAuthStateUseCase")
    inner class Generation {

        private val store = mockk<AuthStateStore>()
        private val saved = slot<PendingAuthState>()

        init {
            coEvery { store.save(capture(saved)) } just runs
        }

        @Test
        fun `32 baytlık entropiyi dolgusuz base64url olarak kodlar ve biçim doğrulamasından geçer`() = runTest {
            val random = mockk<SecureRandomSource>()
            every { random.nextBytes(32) } returns ByteArray(32) { 0xFB.toByte() }

            val state = GenerateAuthStateUseCase(random, store, clock)(SsoFixtures.CLIENT_ID)

            assertEquals(43, state.length)
            // 0xFB baytları standart base64'te '+' ve '/' üretirdi; URL güvenli alfabe '-' ve '_' kullanmalı.
            assertTrue(state.all { it.isLetterOrDigit() || it == '-' || it == '_' }, state)
            assertFalse(state.contains('='))
            assertTrue(AuthStateValidator(clock).isWellFormed(state))
            verify(exactly = 1) { random.nextBytes(32) }
        }

        @Test
        fun `üretilen state istemci kimliği ve zaman damgasıyla saklanır`() = runTest {
            val useCase = GenerateAuthStateUseCase(SecureRandomSource(::platformSecureRandomBytes), store, clock)

            val state = useCase(SsoFixtures.CLIENT_ID)

            assertEquals(PendingAuthState(state, SsoFixtures.CLIENT_ID, SsoFixtures.NOW), saved.captured)
        }

        @Test
        fun `gerçek CSPRNG ile ardışık üretimler çakışmaz`() = runTest {
            val useCase = GenerateAuthStateUseCase(SecureRandomSource(::platformSecureRandomBytes), store, clock)

            val states = List(2_000) { useCase(SsoFixtures.CLIENT_ID) }

            assertEquals(states.size, states.toSet().size)
        }

        @Test
        fun `rastgele kaynak eksik bayt verirse akış durur ve hiçbir şey saklanmaz`() = runTest {
            val random = SecureRandomSource { ByteArray(16) }

            assertThrows<IllegalStateException> {
                GenerateAuthStateUseCase(random, store, clock)(SsoFixtures.CLIENT_ID)
            }
            coVerify(exactly = 0) { store.save(any()) }
        }

        @Test
        fun `depolama hatası çağırana iletilir ki arayüz akışı zarifçe sürdürebilsin`() = runTest {
            val failingStore = mockk<AuthStateStore>()
            coEvery { failingStore.save(any()) } throws RuntimeException("disk dolu")

            assertThrows<RuntimeException> {
                GenerateAuthStateUseCase(SecureRandomSource(::platformSecureRandomBytes), failingStore, clock)(
                    SsoFixtures.CLIENT_ID,
                )
            }
        }
    }

    @Nested
    @DisplayName("AuthStateValidator")
    inner class Validation {

        private val validator = AuthStateValidator(clock, ttlMillis = SsoFixtures.TTL)

        @Test
        fun `geçerli biçimdeki state kabul edilir`() {
            assertTrue(validator.isWellFormed(SsoFixtures.VALID_STATE))
            assertTrue(validator.isWellFormed("a".repeat(32)))
            assertTrue(validator.isWellFormed("a".repeat(128)))
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(
            strings = [
                "",
                "short",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 31 karakter: yetersiz entropi
                "Q2FsbGJhY2tTdGF0ZVZhbHVlXzAxMjM0NTY3ODlh+/=", // standart base64 karakterleri
                "Q2FsbGJhY2tTdGF0ZVZhbHVlXzAxMjM0NTY3ODlh&token=x",
                "Q2FsbGJhY2tTdGF0ZVZhbHVlXzAxMjM0NTY3ODlh%00",
                "Q2FsbGJhY2tTdGF0ZVZhbHVlXzAxMjM0NTY3ODlh ",
                "Q2FsbGJhY2tTdGF0ZVZhbHVlXzAxMjM0NTY3ODlhçğ",
                "<script>alert(1)</script>aaaaaaaaaaaaaaaaaaaa",
            ],
        )
        fun `bozuk ya da enjeksiyon içeren state reddedilir`(state: String) {
            assertFalse(validator.isWellFormed(state))
        }

        @Test
        fun `null ve 128 karakteri aşan state reddedilir`() {
            assertFalse(validator.isWellFormed(null))
            assertFalse(validator.isWellFormed("a".repeat(129)))
        }

        @Test
        fun `birebir aynı değerler eşleşir`() {
            assertTrue(validator.matches(SsoFixtures.VALID_STATE, SsoFixtures.VALID_STATE))
        }

        @Test
        fun `tek karakter farkı, önek ve uzunluk farkı eşleşmez`() {
            val tampered = SsoFixtures.VALID_STATE.dropLast(1) + "d"
            assertFalse(validator.matches(SsoFixtures.VALID_STATE, tampered))
            assertFalse(validator.matches(SsoFixtures.VALID_STATE, SsoFixtures.VALID_STATE.dropLast(1)))
            assertFalse(validator.matches(SsoFixtures.VALID_STATE, SsoFixtures.VALID_STATE + "a"))
            assertFalse(validator.matches(SsoFixtures.VALID_STATE, ""))
        }

        @Test
        fun `süre sınırına kadar geçerli, bir milisaniye sonra süresi dolmuş sayılır`() {
            val pending = PendingAuthState(SsoFixtures.VALID_STATE, SsoFixtures.CLIENT_ID, SsoFixtures.NOW)

            now = SsoFixtures.NOW + SsoFixtures.TTL
            assertFalse(validator.isExpired(pending))

            now = SsoFixtures.NOW + SsoFixtures.TTL + 1
            assertTrue(validator.isExpired(pending))
        }

        @Test
        fun `gelecekte üretilmiş görünen kayıt süresi dolmuş sayılır`() {
            val pending = PendingAuthState(SsoFixtures.VALID_STATE, SsoFixtures.CLIENT_ID, SsoFixtures.NOW + 60_000)
            assertTrue(validator.isExpired(pending))
        }
    }
}
