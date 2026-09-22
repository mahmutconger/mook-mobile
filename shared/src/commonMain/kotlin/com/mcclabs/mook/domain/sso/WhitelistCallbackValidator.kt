package com.mcclabs.mook.domain.sso

/** Tarayıcıların ve Intent çözümleyicilerinin sessizce kısalttığı adreslere karşı üst sınır. */
private const val MAX_REDIRECT_URI_LENGTH = 512

/**
 * Yalnızca şu biçime izin verir: `https://alan.adi/yol/segmentleri`
 *
 * Bilerek reddedilenler: kullanıcı bilgisi (`@`), port, sorgu (`?`), fragman (`#`),
 * ters eğik çizgi, yüzde kodlaması, büyük harfli alan adı, sondaki nokta, `.`/`..`
 * segmentleri ve ASCII dışı karakterler (homoglif saldırıları).
 */
private val STRICT_HTTPS_CALLBACK = Regex(
    "^https://" +
        "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?" +
        "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+" +
        "(?:/[A-Za-z0-9_~-][A-Za-z0-9._~-]*)*$",
)

internal fun isStrictHttpsCallbackUri(uri: String): Boolean =
    uri.length <= MAX_REDIRECT_URI_LENGTH && STRICT_HTTPS_CALLBACK.matches(uri)

/**
 * Gelen `redirect_uri` değerini istemcinin beyaz listesine karşı doğrular.
 *
 * Alan adı karşılaştırması değil, **tam adres** karşılaştırması yapılır: yalnızca alan
 * adını doğrulamak, izinli alan adındaki herhangi bir açık yönlendirme sayfasının token
 * sızdırmasına kapı açardı. Hiçbir normalleştirme yapılmaz; `HTTPS://` ya da sondaki `/`
 * gibi zararsız görünen farklar da reddedilir. Katı olmak burada bilinçli bir tercihtir:
 * meşru istemci adresi bizim belirlediğimiz tek biçimde gönderir.
 */
class WhitelistCallbackValidator {

    fun isAllowed(redirectUri: String?, client: SsoClientPolicy): Boolean {
        if (redirectUri.isNullOrEmpty()) return false
        // Biçim kontrolü, tam eşleşmeden önce derinlemesine savunma katmanıdır: ileride
        // biri eşleşmeyi gevşetse bile bozuk bir adres buradan geçemez.
        if (!isStrictHttpsCallbackUri(redirectUri)) return false
        return redirectUri in client.allowedRedirectUris
    }
}
