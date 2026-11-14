package com.mcclabs.mook.support

import com.mcclabs.mook.domain.support.SupportContact
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Destek iletişim bilgisi uygulama, hesap silme sayfası ve Play mağaza kaydı arasında tutarlı
 * kalmalı. Play, hesap silme sayfasının mağazadaki uygulama veya geliştirici adını içermesini ister.
 */
class SupportContactConsistencyTest {
    private val shared = listOf(File("."), File("shared")).first { File(it, "src/commonMain/kotlin").isDirectory }.canonicalFile
    private val deletionPage = File(shared.parentFile, "hosting/mook-sso/account-deletion.html")

    @Test fun mailtoUriPointsToTheSupportEmail() {
        assertEquals("mailto:${SupportContact.EMAIL}", SupportContact.MAILTO_URI)
    }

    @Test fun deletionPageUsesTheSupportEmailAndStoreNames() {
        assertTrue(deletionPage.isFile, "Hesap silme sayfası bulunamadı: ${deletionPage.path}")
        val html = deletionPage.readText()
        assertTrue("mailto:${SupportContact.EMAIL}?" in html, "Silme talebi bağlantısı destek e-postasını kullanmalı")
        assertFalse("destek@walktalkk.com" in html, "Eski destek adresi silme sayfasında kalmamalı")
        assertTrue("Mook" in html && "WALKTALK" in html, "Sayfa mağazadaki uygulama ve geliştirici adını içermeli")
        assertFalse("konum tercihleri" in html, "Uygulama konum toplamaz; silinen veriler doğru adlandırılmalı")
    }

    @Test fun appSourcesHaveNoHardcodedOldSupportEmail() {
        val offenders = File(shared, "src").walkTopDown()
            .filter { it.extension == "kt" && "Test" !in it.path && "destek@walktalkk.com" in it.readText() }
            .map { it.path }.toList()
        assertTrue(offenders.isEmpty(), "Destek adresi SupportContact üzerinden kullanılmalı: ${offenders.joinToString()}")
    }
}
