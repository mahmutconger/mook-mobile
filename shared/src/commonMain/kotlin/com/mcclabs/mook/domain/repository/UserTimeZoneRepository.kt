package com.mcclabs.mook.domain.repository

/**
 * Cihazın saat dilimini sunucuyla eşitler.
 *
 * Sunucudaki günlük/aylık kota sıfırlamaları (beğeni, mesaj, çeviri karakteri, oda değiştirme)
 * `users/{uid}.timeZone` alanındaki saat dilimine göre "gün" ve "ay" sınırını hesaplar. Bu alan
 * istemci tarafından doğrudan YAZILAMAZ; yalnızca `updateTimeZone` callable'ı ile güncellenir.
 */
interface UserTimeZoneRepository {

    /**
     * Cihazın şu anki IANA saat dilimini (ör. `Europe/Istanbul`) sunucuya gönderir. Uygulama
     * açılışında ve her girişte çağrılır; sunucu, dilim değişmemişse hiçbir şey yazmaz.
     *
     * @return Sunucudaki saat dilimi cihazınkiyle eşleşiyorsa `true`; ağ hatası, App Check reddi
     *   veya 7 günlük değişiklik bekleme süresi nedeniyle güncellenemediyse `false`. Hata asla
     *   fırlatılmaz — açılışı engellemez, bir sonraki açılışta yeniden denenir.
     */
    suspend fun syncDeviceTimeZone(): Boolean
}
