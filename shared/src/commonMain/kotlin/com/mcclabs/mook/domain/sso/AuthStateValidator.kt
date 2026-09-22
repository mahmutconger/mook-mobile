package com.mcclabs.mook.domain.sso

/**
 * base64url alfabesi, 32–128 karakter. 32 karakterin altı yeterli entropi taşımaz;
 * üst sınır ise URL ve depolama kötüye kullanımını engeller.
 */
private val STATE_FORMAT = Regex("^[A-Za-z0-9_-]{32,128}$")

/** State değerinin biçimini, eşleşmesini ve süresini doğrular. Yan etkisizdir. */
class AuthStateValidator(
    private val clock: EpochClock,
    private val ttlMillis: Long = SsoConfig.STATE_TTL_MILLIS,
) {

    fun isWellFormed(state: String?): Boolean = state != null && STATE_FORMAT.matches(state)

    /**
     * Sabit zamanlı karşılaştırma: eşleşen önek uzunluğu süre farkından çıkarılamaz.
     * Uzunluk farkı da erken dönüş yapmadan farka katılır.
     */
    fun matches(expected: String, actual: String): Boolean {
        var diff = expected.length xor actual.length
        val length = maxOf(expected.length, actual.length)
        for (i in 0 until length) {
            val a = if (i < expected.length) expected[i].code else 0
            val b = if (i < actual.length) actual[i].code else 0
            diff = diff or (a xor b)
        }
        return diff == 0
    }

    /** Gelecekte üretilmiş görünen kayıt (cihaz saati geri alınmış) da süresi dolmuş sayılır. */
    fun isExpired(pending: PendingAuthState): Boolean {
        val age = clock.nowMillis() - pending.issuedAtMillis
        return age < 0 || age > ttlMillis
    }
}
