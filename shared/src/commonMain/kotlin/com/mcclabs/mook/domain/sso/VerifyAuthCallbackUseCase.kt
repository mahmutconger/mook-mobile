package com.mcclabs.mook.domain.sso

import com.mcclabs.mook.util.encodeUrlComponent

/**
 * Dışarıdan gelen yetkilendirme isteğini, token üretiminden **önce** doğrular.
 *
 * Kontrol sırası bilinçlidir: tüm biçim kontrolleri depolamaya dokunmadan yapılır. Böylece
 * sahte bir bağlantı, kullanıcının o an devam eden meşru akışının kaydını silip onu bozamaz.
 *
 * İki ayrı değer, her tarafın yalnızca kendi ürettiğini doğrulaması için:
 * - `state` (zorunlu): İstemcinin CSRF değeri. Biçimi doğrulanır ve geri dönüş adresine
 *   aynen iletilir. Eşleşme kontrolü istemcinin sorumluluğudur.
 * - `mook_state` (isteğe bağlı): Varsa akışı Mook başlatmış demektir. Yereldeki süresi
 *   dolmamış kayıtla sabit zamanlı karşılaştırmada birebir eşleşmek zorundadır ve tek
 *   kullanımlıktır. Kayıt yoksa (zaten kullanılmış ya da hiç üretilmemiş) istek reddedilir;
 *   bu, aynı bağlantının tekrar oynatılmasını da engeller.
 * - `mook_state` yoksa istek istemcinin kendi başlattığı akıştır ("Mook ile giriş yap").
 *   Mook'taki olası bir kayda dokunulmaz; koruma beyaz liste, onay ekranı ve imzası
 *   doğrulanmış teslimat ile sağlanır.
 */
class VerifyAuthCallbackUseCase(
    private val registry: SsoClientRegistry,
    private val redirectValidator: WhitelistCallbackValidator,
    private val stateValidator: AuthStateValidator,
    private val store: AuthStateStore,
) {

    suspend operator fun invoke(request: SsoAuthorizationRequest): SsoVerificationResult {
        val client = request.clientId
            ?.let(registry::findClient)
            ?: return SsoVerificationResult.Rejected(SsoRejectionReason.UNKNOWN_CLIENT)

        val redirectUri = request.redirectUri
        if (redirectUri == null || !redirectValidator.isAllowed(redirectUri, client)) {
            return SsoVerificationResult.Rejected(SsoRejectionReason.REDIRECT_NOT_ALLOWED)
        }

        val state = request.state
        if (state == null || !stateValidator.isWellFormed(state)) {
            return SsoVerificationResult.Rejected(SsoRejectionReason.MALFORMED_STATE)
        }

        val mookState = request.mookState
        if (mookState != null && !stateValidator.isWellFormed(mookState)) {
            return SsoVerificationResult.Rejected(SsoRejectionReason.MALFORMED_STATE)
        }

        if (mookState != null) {
            verifyMookState(mookState, client)?.let { return SsoVerificationResult.Rejected(it) }
        }

        return SsoVerificationResult.Verified(
            VerifiedSsoRequest(
                client = client,
                redirectUri = redirectUri,
                state = state,
                initiatedByMook = mookState != null,
            ),
        )
    }

    /** Başarılıysa `null` döner ve kaydı tüketir; aksi halde ret nedenini döner. */
    private suspend fun verifyMookState(mookState: String, client: SsoClientPolicy): SsoRejectionReason? {
        // Kayıt yok: ya süresi çoktan doldu ve temizlendi ya da bağlantı tekrar oynatılıyor.
        // Meşru kullanıcı için anlamlı mesaj "yeniden başlat" olduğundan süre aşımı döner.
        val pending = store.read() ?: return SsoRejectionReason.STATE_EXPIRED

        // İstemci kimliği kısa devre yapsa da değer karşılaştırması her durumda çalışır;
        // süre farkı hangi kontrolün başarısız olduğunu ele vermez.
        val valueMatches = stateValidator.matches(pending.value, mookState)
        if (!valueMatches || pending.clientId != client.clientId) {
            // Kayıt korunur: sahte bir istek, kullanıcının devam eden akışını geçersiz kılamaz.
            return SsoRejectionReason.STATE_MISMATCH
        }

        store.clear()
        return if (stateValidator.isExpired(pending)) SsoRejectionReason.STATE_EXPIRED else null
    }
}

/**
 * Token ve state'i, doğrulanmış geri dönüş adresine **fragman** olarak ekler.
 *
 * Sorgu yerine fragman kullanılır: bir hata sonucu adres tarayıcıda açılsa bile fragman
 * sunucuya gönderilmez, erişim günlüklerine ve Referer başlığına düşmez. Beyaz liste
 * sorgu ve fragman içeren adresleri zaten reddettiği için ekleme her zaman güvenlidir.
 */
fun buildSsoRedirectUrl(request: VerifiedSsoRequest, token: String): String =
    buildString {
        append(request.redirectUri)
        append("#token=").append(encodeUrlComponent(token))
        append("&state=").append(encodeUrlComponent(request.state))
    }
