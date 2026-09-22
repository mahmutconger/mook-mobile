package com.mcclabs.mook.domain.sso

/**
 * SSO akışının sabitleri.
 *
 * [APP_LINK_HOST] değeri `androidApp/build.gradle.kts` içindeki `ssoAppLinkHost`
 * manifest placeholder'ı ve `hosting/mook-sso/.well-known/assetlinks.json` dosyasının
 * yayınlandığı alan adıyla birebir aynı olmak zorundadır; biri değişirse üçü birlikte
 * değişmelidir, aksi halde App Link doğrulaması sessizce başarısız olur.
 */
object SsoConfig {
    const val APP_LINK_HOST: String = "mook-sso.web.app"
    const val AUTHORIZE_PATH: String = "/sso/authorize"

    /** Mook'un başlattığı bir akışın geri dönüşünü beklediği azami süre. */
    const val STATE_TTL_MILLIS: Long = 10 * 60 * 1000L

    /** 32 bayt = 256 bit entropi; base64url ile 43 karaktere kodlanır. */
    const val STATE_ENTROPY_BYTES: Int = 32
}

/**
 * Beyaz listedeki tek bir SSO istemcisinin güvenlik politikası.
 *
 * Görünen ad dahil her şey buradan okunur; istekle gelen hiçbir değer ekranda
 * gösterilmez. Böylece saldırgan, onay ekranında "WalkTalk" yazdırıp token'ı başka
 * bir yere yönlendiremez.
 *
 * @property allowedRedirectUris Birebir (karakter karakter) eşleşmesi gereken geri dönüş adresleri.
 * @property androidSigningCertSha256 İstemci uygulamanın imza sertifikası SHA-256 parmak
 *   izleri (`AB:CD:…`). Boş bırakılırsa yalnızca paket adına teslim yapılır.
 */
data class SsoClientPolicy(
    val clientId: String,
    val displayName: String,
    val allowedRedirectUris: Set<String>,
    val androidPackageName: String,
    val androidSigningCertSha256: Set<String> = emptySet(),
) {
    init {
        require(clientId.isNotBlank()) { "clientId boş olamaz" }
        require(allowedRedirectUris.isNotEmpty()) { "$clientId için en az bir redirect_uri gerekli" }
        // Yanlış yazılmış bir beyaz liste girdisi, çalışma anında sessizce açık
        // oluşturmak yerine uygulama açılışında hemen patlamalıdır.
        allowedRedirectUris.forEach { uri ->
            require(isStrictHttpsCallbackUri(uri)) { "Beyaz listedeki redirect_uri katı biçime uymuyor: $uri" }
        }
    }
}

/**
 * Dış uygulamadan gelen, henüz hiçbir kontrolden geçmemiş ham yetkilendirme isteği.
 *
 * @property state İstemcinin kendi ürettiği CSRF değeri. Mook doğrulamaz, yalnızca biçimini
 *   kontrol edip geri dönüş adresine aynen iletir; eşleşmeyi istemci yapar.
 * @property mookState Akışı Mook başlattıysa Mook'un ürettiği değer. İki değer bilinçli
 *   olarak ayrıdır: tek bir parametre paylaşılsaydı istemci, dışarıdan gelen bir değeri
 *   kendi CSRF değeri olarak kabul etmek zorunda kalır ve saldırgan önceden bildiği bir
 *   değeri yerleştirerek kurbanı kendi hesabına giriş yaptırabilirdi.
 */
data class SsoAuthorizationRequest(
    val clientId: String?,
    val redirectUri: String?,
    val state: String?,
    val mookState: String? = null,
)

/** Tüm güvenlik kontrollerinden geçmiş, token üretimine hazır istek. */
data class VerifiedSsoRequest(
    val client: SsoClientPolicy,
    val redirectUri: String,
    val state: String,
    /** `true` ise istekteki `mook_state`, Mook'un yerelde sakladığı değerle eşleşti. */
    val initiatedByMook: Boolean,
)

/** Mook'un başlattığı akış için yerelde saklanan, tek kullanımlık state kaydı. */
data class PendingAuthState(
    val value: String,
    val clientId: String,
    val issuedAtMillis: Long,
)

/** Bir isteğin reddedilme nedeni. Yalnızca iç kullanım içindir; kullanıcıya ayrıntı gösterilmez. */
enum class SsoRejectionReason {
    UNKNOWN_CLIENT,
    REDIRECT_NOT_ALLOWED,
    MALFORMED_STATE,
    STATE_MISMATCH,
    STATE_EXPIRED,
}

sealed interface SsoVerificationResult {
    data class Verified(val request: VerifiedSsoRequest) : SsoVerificationResult
    data class Rejected(val reason: SsoRejectionReason) : SsoVerificationResult
}

/** Token'ın istemci uygulamaya teslim edilme sonucu. */
enum class SsoDeliveryResult {
    DELIVERED,

    /** Uygulama yüklü değil ya da geri dönüş adresini karşılayacak güncel sürüm değil. */
    CLIENT_APP_UNAVAILABLE,

    /** Paket adı doğru ama imza sertifikası sabitlenen parmak izleriyle eşleşmiyor. */
    CLIENT_APP_UNTRUSTED,
}
