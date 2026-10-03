package com.mcclabs.mook.billing

import com.mcclabs.mook.domain.analytics.AnalyticsParameterSanitizer
import com.mcclabs.mook.domain.analytics.SensitiveAnalyticsKeyPolicy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Gereksinim 4 (Faz 6, Veri Gizliliği/TFUA): [AnalyticsParameterSanitizer]'ın SAF eleme
 * mantığını doğrular -- [SensitiveAnalyticsKeyPolicy] hassas bulduğu HER anahtarı sonuçtan
 * çıkarır, güvenli anahtarlara DOKUNMAZ ve olayın TAMAMINI asla iptal etmez.
 */
class AnalyticsParameterSanitizerTest {

    private val policy = mockk<SensitiveAnalyticsKeyPolicy>()

    @Test
    @DisplayName("hassas olarak işaretlenen anahtarları sonuçtan çıkarır")
    fun removesSensitiveKeys() {
        every { policy.isSensitive("age") } returns true
        every { policy.isSensitive("room_selection") } returns true
        every { policy.isSensitive("placement") } returns false

        val sanitizer = AnalyticsParameterSanitizer(policy)
        val result = sanitizer.sanitize(
            mapOf("age" to 25, "room_selection" to "istanbul", "placement" to "discover_banner"),
        )

        assertEquals(mapOf("placement" to "discover_banner"), result)
        assertFalse(result.containsKey("age"))
        assertFalse(result.containsKey("room_selection"))
    }

    @Test
    @DisplayName("hiçbir anahtar hassas değilse tüm parametreleri OLDUĞU GİBİ bırakır")
    fun keepsAllParametersWhenNoneAreSensitive() {
        every { policy.isSensitive(any()) } returns false

        val sanitizer = AnalyticsParameterSanitizer(policy)
        val input = mapOf("viewer_is_premium" to true, "ad_format" to "BANNER")
        val result = sanitizer.sanitize(input)

        assertEquals(input, result)
    }

    @Test
    @DisplayName("tüm anahtarlar hassas olsa dahi boş bir harita döner, ASLA fırlatmaz")
    fun neverThrowsWhenAllKeysAreSensitive() {
        every { policy.isSensitive(any()) } returns true

        val sanitizer = AnalyticsParameterSanitizer(policy)
        val result = sanitizer.sanitize(mapOf("age" to 25, "gender" to "x"))

        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("politikaya HER anahtar için ayrı ayrı danışır")
    fun consultsPolicyForEveryKey() {
        every { policy.isSensitive(any()) } returns false

        val sanitizer = AnalyticsParameterSanitizer(policy)
        sanitizer.sanitize(mapOf("a" to 1, "b" to 2, "c" to 3))

        verify(exactly = 1) { policy.isSensitive("a") }
        verify(exactly = 1) { policy.isSensitive("b") }
        verify(exactly = 1) { policy.isSensitive("c") }
    }
}
