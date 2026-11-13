package com.mcclabs.mook.legal

import com.mcclabs.mook.domain.legal.LegalLinks
import com.mcclabs.mook.domain.legal.LegalLinksResolver
import kotlin.test.Test
import kotlin.test.assertEquals

/** [LegalLinksResolver]in dil koduna göre doğru yasal sayfaları seçtiğini doğrular. */
class LegalLinksResolverTest {
    private val turkish = LegalLinks(
        privacyPolicyUrl = "https://mookswipe.com/gizlilik-politikasi",
        termsOfUseUrl = "https://mookswipe.com/kullanim-kosullari",
        childSafetyUrl = "https://mookswipe.com/cocuk-guvenligi",
    )
    private val english = LegalLinks(
        privacyPolicyUrl = "https://mookswipe.com/en/privacy-policy",
        termsOfUseUrl = "https://mookswipe.com/en/terms-of-use",
        childSafetyUrl = "https://mookswipe.com/cocuk-guvenligi",
    )

    @Test fun turkceKodlarTurkceSayfalariSecer() {
        listOf("tr", "TR", "tr-TR", "tr_TR").forEach { code ->
            assertEquals(turkish, LegalLinksResolver.forLanguage(code), "dil: $code")
        }
    }

    @Test fun digerTumKodlarIngilizceSayfalaraDuser() {
        listOf("en", "de", "pt-BR", "", null, "xx").forEach { code ->
            assertEquals(english, LegalLinksResolver.forLanguage(code), "dil: $code")
        }
    }

    @Test fun cocukGuvenligiSayfasiHerDildeAyni() {
        val urls = listOf("tr", "en", "de", null, "xx").map { LegalLinksResolver.forLanguage(it).childSafetyUrl }.toSet()
        assertEquals(setOf("https://mookswipe.com/cocuk-guvenligi"), urls)
    }
}
