package com.mcclabs.mook.domain.model

/**
 * Bir kullanıcının açık oda (dil) slotlarından biri ve en son ne zaman aktif oda olarak
 * kullanıldığı (Gereksinim 1.7).
 *
 * Kademe düşüşünde hangi fazla odaların "en eski kullanılan" sayılıp otomatik
 * kapatılacağına karar vermek için gereklidir — bkz.
 * [com.mcclabs.mook.domain.billing.TierDowngradeUseCase].
 *
 * @property code Odanın dil kodu (ör. "TR", "EN-US") — [MatchSettings.roomLanguageCode]
 *   ile aynı katalog.
 * @property lastActiveAtMillis Bu odanın en son ne zaman AKTİF oda olarak seçildiği
 *   (epoch milisaniye). Sunucuda `users/{uid}.roomLastActiveAt` haritasından okunur;
 *   hiç aktif olmamış (yalnızca eklenmiş) bir oda için 0 olabilir.
 */
data class RoomUsage(
    val code: String,
    val lastActiveAtMillis: Long,
)
