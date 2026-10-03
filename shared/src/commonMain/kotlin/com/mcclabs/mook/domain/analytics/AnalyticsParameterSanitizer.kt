package com.mcclabs.mook.domain.analytics

/**
 * Gereksinim 4 (Faz 6): belirli bir anahtarın Firebase Analytics'e (ya da bir reklam isteğine)
 * GÖNDERİLMESİ YASAK hassas bir profil alanı olup olmadığına karar verir -- Açık/Kapalı
 * (Open/Closed) ilkesiyle tutarlı: yeni bir hassas alan eklemek bu POLİTİKAYI değiştirmeyi
 * gerektirir, [AnalyticsParameterSanitizer]'ın kendisini DEĞİL.
 */
fun interface SensitiveAnalyticsKeyPolicy {
    fun isSensitive(key: String): Boolean
}

/**
 * Gereksinim 4 (Faz 6, TFUA/Veri Gizliliği): yaş, tercihler (preferences), oda seçimi (room
 * selection) gibi hassas PROFİL verilerinin -- bugün ya da GELECEKTE eklenecek bir çağrı
 * yoluyla -- Firebase Analytics'e KAZARA sızmasını engelleyen son savunma hattı (bkz.
 * `AnalyticsRepositoryImpl.logSafely`). Bir İZİN LİSTESİ (allowlist) değil, bir ENGELLEME
 * LİSTESİDİR (blocklist) -- bugün analitik olaylarının taşıdığı alanlar (`placement`,
 * `ad_format`, `viewer_is_premium` vb.) zaten güvenlidir; bu sınıf onları DEĞİL, gelecekte
 * yanlışlıkla eklenebilecek hassas alanları hedefler.
 */
class DefaultSensitiveAnalyticsKeyPolicy : SensitiveAnalyticsKeyPolicy {
    override fun isSensitive(key: String): Boolean {
        val normalized = key.lowercase()
        return SENSITIVE_KEY_FRAGMENTS.any { fragment -> normalized.contains(fragment) }
    }

    private companion object {
        /**
         * Alt dize (substring) eşleşmesiyle çalışır -- ör. "room_id", "room_selection",
         * "current_room" gibi varyasyonların HEPSİNİ "room" ortak kökü yakalar.
         */
        val SENSITIVE_KEY_FRAGMENTS = setOf(
            "age", "birthdate", "birth_date", "dob",
            "preference", "room", "gender", "sexual_orientation",
            "location", "latitude", "longitude", "address",
            "phone", "email", "full_name", "surname",
            "national_id", "tc_kimlik",
        )
    }
}

/**
 * Gereksinim 4 (Faz 6): `AnalyticsRepositoryImpl.logSafely`e giden HER olay parametre haritası
 * buradan geçer -- [policy] hassas bulduğu HERHANGİ bir anahtarı SESSİZCE eler (olayın tamamını
 * iptal ETMEZ, yalnızca o alanı çıkarır -- Analytics'in null-değer davranışıyla TUTARLI, bkz.
 * `logSafely` KDoc'u).
 */
class AnalyticsParameterSanitizer(private val policy: SensitiveAnalyticsKeyPolicy) {
    fun sanitize(parameters: Map<String, Any>): Map<String, Any> =
        parameters.filterKeys { key -> !policy.isSensitive(key) }
}
