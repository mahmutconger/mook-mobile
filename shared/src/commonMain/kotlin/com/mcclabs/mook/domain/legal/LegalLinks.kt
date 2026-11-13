package com.mcclabs.mook.domain.legal

/** Uygulamanın yasal belge bağlantıları; dile göre [LegalLinksResolver] seçer. */
data class LegalLinks(
    val privacyPolicyUrl: String,
    val termsOfUseUrl: String,
    val childSafetyUrl: String,
)

/**
 * Uygulama diline göre yasal bağlantıları seçen saf çözümleyici.
 *
 * Kural: dil kodu "tr" ise (büyük/küçük harf duyarsız; "tr-TR" ve "tr_TR" dahil) Türkçe
 * sayfalar, diğer her durumda (null, boş, bilinmeyen dil) İngilizce sayfalar döner. Çocuk
 * güvenliği sayfası iki dilli tek bir sayfadır ve her dilde aynıdır.
 */
object LegalLinksResolver {
    // Tüm yasal adresler yalnızca bu dosyada tutulur.
    private const val CHILD_SAFETY_URL = "https://mookswipe.com/cocuk-guvenligi"

    val turkish = LegalLinks(
        privacyPolicyUrl = "https://mookswipe.com/gizlilik-politikasi",
        termsOfUseUrl = "https://mookswipe.com/kullanim-kosullari",
        childSafetyUrl = CHILD_SAFETY_URL,
    )

    val english = LegalLinks(
        privacyPolicyUrl = "https://mookswipe.com/en/privacy-policy",
        termsOfUseUrl = "https://mookswipe.com/en/terms-of-use",
        childSafetyUrl = CHILD_SAFETY_URL,
    )

    fun forLanguage(languageCode: String?): LegalLinks {
        val primary = languageCode?.trim()?.split('-', '_')?.firstOrNull()?.lowercase()
        return if (primary == "tr") turkish else english
    }
}
