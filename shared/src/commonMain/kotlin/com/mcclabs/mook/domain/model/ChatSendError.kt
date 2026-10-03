package com.mcclabs.mook.domain.model

/**
 * Why a [com.mcclabs.mook.domain.repository.ChatRepository.sendMessage] call failed,
 * reduced to the cases the chat screen words differently.
 *
 * The raw callable error code never reaches the UI: it is neither translatable nor
 * meaningful to a user, and the screen must show a localized sentence.
 */
enum class ChatSendError {

    /** The two users are not matched — the server refuses to open the conversation. */
    NOT_MATCHED,

    /** The per-user send quota for the current minute is spent. */
    RATE_LIMITED,

    /**
     * Gereksinim 2.2: bu takvim günü ya da ayı için karakter tabanlı çeviri kotası
     * tükendi (bkz. `CharacterQuotaManager` ve sunucudaki `enforceMessageQuota`).
     * [RATE_LIMITED]'dan kasıtlı olarak AYRI bir durumdur: biri "çok hızlı gönderiyorsun,
     * biraz bekle", diğeri "bugünkü/bu ayki çeviri hakkın bitti, yarın/gelecek ay
     * sıfırlanacak" anlamına gelir — ikisi aynı metinle gösterilirse kullanıcı yanlış
     * eylemi (birkaç saniye bekleyip tekrar denemek) yapar.
     */
    CHARACTER_QUOTA_EXHAUSTED,

    /** Anything else: offline, function down, malformed payload. */
    GENERIC,
}

/** Thrown by the repository so callers can react without touching Firebase types. */
class ChatSendException(
    val reason: ChatSendError,
    cause: Throwable? = null,
) : Exception(reason.name, cause)

/**
 * Maps a Firebase callable error code onto a [ChatSendError].
 *
 * Accepts both spellings the Firebase SDKs use — the enum form (`RESOURCE_EXHAUSTED`)
 * and the wire form (`resource-exhausted`) — because Android and iOS do not agree on
 * which one surfaces. Kept pure so the table is testable without Firebase.
 *
 * [message] is the callable's `HttpsError` message string (ör. "daily-message-limit",
 * "character-quota-daily-exhausted") — sunucu tarafında BİRDEN FAZLA farklı sebep aynı
 * `RESOURCE_EXHAUSTED` koduyla fırlatıldığından (bkz. `enforceMessageQuota`), yalnızca
 * kodun kendisi hangi durumun oluştuğunu ayırt etmeye yetmez; Gereksinim 2.2'nin
 * karakter kotası durumunu diğerlerinden (dakikalık hız sınırı, günlük mesaj sayısı)
 * ayırmak için bu metne bakılır. [message] verilmezse (ör. eski çağrı yerleri veya test
 * kolaylığı için) tüm `RESOURCE_EXHAUSTED` durumları eskisi gibi [ChatSendError.RATE_LIMITED]
 * olarak ele alınır — bu, davranışı asla KIRMAYAN, yalnızca daha spesifik hale getiren
 * geriye dönük uyumlu bir varsayılandır.
 */
fun chatSendErrorFor(code: String?, message: String? = null): ChatSendError {
    val normalized = code?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_')
    return when (normalized) {
        // The callable rejects an unmatched peer with failed-precondition.
        "FAILED_PRECONDITION", "PERMISSION_DENIED" -> ChatSendError.NOT_MATCHED
        "RESOURCE_EXHAUSTED" -> {
            val reason = message?.trim()?.lowercase()
            if (reason == "character-quota-daily-exhausted" || reason == "character-quota-monthly-exhausted") {
                ChatSendError.CHARACTER_QUOTA_EXHAUSTED
            } else {
                ChatSendError.RATE_LIMITED
            }
        }
        else -> ChatSendError.GENERIC
    }
}
