package com.mcclabs.mook.sso

import java.io.File
import org.junit.Test
import kotlin.test.assertTrue
import android.app.Application
import org.json.JSONArray
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `mook-sso.web.app` App Links doğrulaması `assetlinks.json` dosyasına dayanır. Play App
 * Signing ile mağazadan gelen paket Google'ın anahtarıyla imzalanır; o anahtarın parmak izi
 * listede yoksa SSO bağlantıları uygulama yerine tarayıcıda açılır.
 */
// Gerçek org.json uygulaması için Robolectric ile çalışır (JVM'de Android sınıfları taklittir).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AssetLinksFileTest {
    private val shared = listOf(File("."), File("shared")).first { File(it, "src/commonMain/kotlin").isDirectory }.canonicalFile
    private val file = File(shared.parentFile, "hosting/mook-sso/.well-known/assetlinks.json")

    // Play Console > Uygulama imzalama sayfasında gösterilen anahtarlar (ilk değer yükleme anahtarıdır).
    private val requiredFingerprints = setOf(
        "62:BB:DD:32:62:84:08:10:A6:27:1B:56:FA:97:56:6C:10:9D:71:44:68:4F:49:80:84:4C:04:F6:CF:A4:5B:CF",
        "AA:B4:0B:7E:CF:F4:BC:BB:F9:F2:96:9A:2D:A8:84:6E:C5:20:4D:89:55:1F:B9:89:5D:37:C3:BC:71:2C:4D:32",
        "83:95:A9:C1:20:80:4C:E7:A5:AB:5D:01:06:BC:45:0C:B9:A7:B3:63:A5:BA:FE:E2:43:07:5A:5E:19:57:3B:B6",
        "93:E7:FA:07:44:78:FB:CA:79:6D:32:0F:34:61:66:61:08:79:CB:3F:E6:F1:6F:D8:5A:10:20:8F:20:C6:F3:CD",
    )

    @Test fun assetLinksDeclaresAppLinksForMookWithAllSigningKeys() {
        assertTrue(file.isFile, "assetlinks.json bulunamadı: ${file.path}")
        val statement = JSONArray(file.readText()).getJSONObject(0)
        val relations = statement.getJSONArray("relation").let { r -> (0 until r.length()).map(r::getString) }
        assertTrue("delegate_permission/common.handle_all_urls" in relations, "App Links ilişkisi eksik")
        val target = statement.getJSONObject("target")
        assertTrue(target.getString("package_name") == "com.mcclabs.mook", "Paket adı yanlış")
        val fingerprints = target.getJSONArray("sha256_cert_fingerprints").let { f -> (0 until f.length()).map(f::getString).toSet() }
        fingerprints.forEach { assertTrue(Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}").matches(it), "Geçersiz parmak izi: $it") }
        assertTrue(fingerprints.containsAll(requiredFingerprints), "Eksik imza parmak izi: ${requiredFingerprints - fingerprints}")
    }
}
