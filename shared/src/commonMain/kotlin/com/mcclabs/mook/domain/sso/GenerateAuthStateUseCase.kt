package com.mcclabs.mook.domain.sso

import kotlin.io.encoding.Base64

/**
 * Mook'un başlattığı bir SSO akışı için kriptografik olarak güvenli, tek kullanımlık
 * bir state üretir ve geri dönüşte doğrulanmak üzere saklar.
 *
 * Yeni bir çağrı öncekinin yerine geçer: aynı anda yalnızca bir aktif akış olur, böylece
 * eski ve terk edilmiş bir bağlantı sonradan kabul edilemez.
 */
class GenerateAuthStateUseCase(
    private val random: SecureRandomSource,
    private val store: AuthStateStore,
    private val clock: EpochClock,
) {

    /** @throws IllegalStateException Rastgele kaynak beklenen uzunlukta bayt vermezse. */
    suspend operator fun invoke(clientId: String): String {
        val bytes = random.nextBytes(SsoConfig.STATE_ENTROPY_BYTES)
        // Entropisi eksik bir state'i sessizce kullanmaktansa akışı durdurmak doğrudur.
        check(bytes.size == SsoConfig.STATE_ENTROPY_BYTES) { "Yetersiz rastgele bayt: ${bytes.size}" }

        val state = URL_SAFE_NO_PADDING.encode(bytes)
        store.save(PendingAuthState(value = state, clientId = clientId, issuedAtMillis = clock.nowMillis()))
        return state
    }

    private companion object {
        val URL_SAFE_NO_PADDING = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    }
}
