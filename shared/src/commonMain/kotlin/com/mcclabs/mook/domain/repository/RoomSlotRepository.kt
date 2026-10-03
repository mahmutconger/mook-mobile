package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.RoomUsage

/**
 * Kullanıcının açık oda (dil) slotlarına erişim (Gereksinim 1.7).
 *
 * [SettingsRepository] yalnızca TEK bir aktif odayı ([SettingsRepository.getRoomLanguageCode])
 * bilir; bu arayüz kullanıcının sahip olduğu TÜM açık slotları ve her birinin en son ne
 * zaman kullanıldığını taşır — bir kademe düşüşünde hangi fazla odaların kapatılacağına
 * karar vermek ([com.mcclabs.mook.domain.billing.TierDowngradeUseCase]) ve kapalı bir
 * odadaki sohbetleri salt-okunur işaretlemek için gereklidir.
 */
interface RoomSlotRepository {

    /** Kullanıcının şu an açık olan tüm oda slotları, en son kullanım zamanlarıyla. */
    suspend fun openRooms(): List<RoomUsage>

    /**
     * Belirtilen oda kodlarını kapatır (`closeRoomSlots` callable'ı üzerinden — bkz.
     * `functions/src/monetization.ts`).
     *
     * Kapatılan kodlardan biri o an aktif oda ise, sunucu kalan açık odalardan en son
     * kullanılanını otomatik olarak yeni aktif oda yapar; hiç oda kalmazsa aktif oda
     * `null` olur (kullanıcı [RoomGateViewModel]'in ilk-oda seçicisiyle karşılaşır).
     *
     * @param codes Kapatılacak oda kodları — [openRooms]'un döndürdüğü kodların bir
     *   alt kümesi olmalıdır; sunucu bunu doğrular.
     */
    suspend fun closeRooms(codes: List<String>)
}
