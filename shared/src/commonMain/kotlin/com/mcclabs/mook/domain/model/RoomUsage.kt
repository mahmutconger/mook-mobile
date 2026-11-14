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

/**
 * Kullanıcının BUGÜNKÜ oda değiştirme kullanımı (sunucunun saat dilimine göre, `getUsage`).
 *
 * @property roomSwitches Bugün YENİ oda açmak için harcanan hak sayısı. Açık odalar arası
 *   geçiş hak düşürmez (bkz. `functions/src/roomSwitch.ts`).
 * @property rewardedRoomSwitches Bugün ödüllü reklamla kazanılan ek hak sayısı.
 * @property isKnown Sunucudan okunabildiyse `true`; okunamadıysa arayüz kalan hakkı
 *   bilinmiyor sayar ve sayıyı göstermez (sunucu yine de kotayı uygular).
 */
data class RoomSwitchUsage(
    val roomSwitches: Int = 0,
    val rewardedRoomSwitches: Int = 0,
    val isKnown: Boolean = false,
)
