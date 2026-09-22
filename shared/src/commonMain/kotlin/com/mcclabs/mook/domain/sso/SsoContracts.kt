package com.mcclabs.mook.domain.sso

/** Beyaz listedeki istemcileri çözen kaynak. Sabit kodlu ya da uzaktan yüklenen bir uygulaması olabilir. */
fun interface SsoClientRegistry {
    fun findClient(clientId: String): SsoClientPolicy?
}

/**
 * Mook'un başlattığı akışın state değerini süreç ölümüne dayanıklı biçimde saklar.
 *
 * Uygulamalar hata fırlatmamalıdır: okunamayan kayıt `null` sayılır. Bu, bozuk bir
 * depolamanın kullanıcıyı kilitlemesini önler; güvenlik ise beyaz liste, onay ekranı ve
 * imzası doğrulanmış teslimat tarafından zaten sağlanır.
 */
interface AuthStateStore {
    suspend fun save(state: PendingAuthState)
    suspend fun read(): PendingAuthState?
    suspend fun clear()
}

/** Kriptografik olarak güvenli rastgele bayt kaynağı. `kotlin.random.Random` bu iş için uygun değildir. */
fun interface SecureRandomSource {
    fun nextBytes(size: Int): ByteArray
}

/** Test edilebilirlik için enjekte edilen saat. */
fun interface EpochClock {
    fun nowMillis(): Long
}

/** Oturum açmış Mook hesabının onay ekranında gösterilecek bilgisi. */
data class SsoAccount(val email: String?)

/**
 * SSO token işlemlerinin soyutlaması. Firebase ayrıntıları bu arayüzün arkasında kalır.
 */
interface SsoAuthRepository {
    /** Oturum yoksa `null`. */
    fun currentAccount(): SsoAccount?

    /** Oturum açmış kullanıcı için kısa ömürlü bir özel (custom) token üretir. */
    suspend fun mintSsoToken(): SsoTokenResult
}

sealed interface SsoTokenResult {
    data class Success(val token: String) : SsoTokenResult
    data class Failure(val kind: SsoTokenFailure) : SsoTokenResult
}

enum class SsoTokenFailure {
    /** Geçici bağlantı sorunu; kullanıcı tekrar deneyebilir. */
    NETWORK,

    /** Mook oturumu geçersiz; yeniden giriş gerekir. */
    SESSION_EXPIRED,

    /** Sunucu tarafı hata; tekrar denenebilir. */
    SERVER,
}
