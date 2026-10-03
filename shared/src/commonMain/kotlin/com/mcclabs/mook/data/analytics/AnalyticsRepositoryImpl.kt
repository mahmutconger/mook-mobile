package com.mcclabs.mook.data.analytics

import com.mcclabs.mook.domain.analytics.AdFormat
import com.mcclabs.mook.domain.analytics.AnalyticsEvent
import com.mcclabs.mook.domain.analytics.AnalyticsParameterSanitizer
import com.mcclabs.mook.domain.analytics.AnalyticsRepository
import com.mcclabs.mook.util.Log
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics

private const val EVENT_AD_REVENUE_PAID = "ad_impression_revenue"
private const val EVENT_MATCH_CREATED = "match_created"
private const val EVENT_FIRST_MESSAGE_REPLIED = "first_message_replied"

/**
 * [AnalyticsRepository]'nin `dev.gitlive.firebase.analytics` (KMP Firebase Analytics
 * sarmalayıcısı) üzerinden gerçek implementasyonu — `AuthRepositoryImpl`/
 * `ChatRepositoryImpl`de zaten kurulmuş "her SDK çağrısını `try/catch` ile sar, hatayı
 * [Log] üzerinden görünür kıl, akışı asla kesme" desenini izler.
 */
class AnalyticsRepositoryImpl(
    private val sanitizer: AnalyticsParameterSanitizer,
) : AnalyticsRepository {

    override fun logAdRevenuePaid(
        placement: String,
        format: AdFormat,
        valueMicros: Long,
        currencyCode: String,
        precisionType: Int,
    ) {
        logSafely(
            EVENT_AD_REVENUE_PAID,
            mapOf(
                "placement" to placement,
                "ad_format" to format.name,
                "value_micros" to valueMicros,
                "currency" to currencyCode,
                "precision_type" to precisionType,
            ),
        )
    }

    override fun logMatchCreated(viewerIsPremium: Boolean) {
        logSafely(EVENT_MATCH_CREATED, mapOf("viewer_is_premium" to viewerIsPremium))
    }

    override fun logFirstMessageReplied(replierIsPremium: Boolean) {
        logSafely(EVENT_FIRST_MESSAGE_REPLIED, mapOf("replier_is_premium" to replierIsPremium))
    }

    override fun identifyUser(uid: String) {
        // bkz. arayüz KDoc'u -- `deleteAccount.ts`deki GA4 Kullanıcı Silme isteğinin
        // eşleşebileceği tek kimlik BUDUR. Diğer tüm çağrılar gibi (bkz. `logSafely`)
        // hiçbir hata kullanıcı akışını KESMEMELİDİR.
        try {
            Firebase.analytics.setUserId(uid)
        } catch (e: Exception) {
            Log.e("Analitik Kullanıcı-Kimliği ayarlanamadı", e)
        }
    }

    override fun track(event: AnalyticsEvent) {
        logSafely(event.name, event.parameters)
    }

    /** Analitik olaylarının kullanıcı akışını hiçbir zaman kesmemesi için tüm hataları yutar. */
    private fun logSafely(name: String, parameters: Map<String, Any?>) {
        try {
            // `logEvent` null DEĞERLİ parametre kabul etmez (Firebase Analytics'in kendisi de
            // desteklemez) — burada SESSİZCE elenir, tüm olay iptal edilmez.
            val nonNullParameters: Map<String, Any> = buildMap {
                parameters.forEach { (key, value) -> if (value != null) put(key, value) }
            }
            // Gereksinim 4 (Faz 6): hassas profil alanlarına karşı son savunma hattı --
            // bkz. [AnalyticsParameterSanitizer] KDoc'u.
            Firebase.analytics.logEvent(name, sanitizer.sanitize(nonNullParameters))
        } catch (e: Exception) {
            Log.e("Analitik olayı gönderilemedi: $name", e)
        }
    }
}
