package com.mcclabs.mook.domain.billing

import com.mcclabs.mook.domain.model.RoomUsage
import com.mcclabs.mook.domain.repository.RoomSlotRepository

/**
 * Bir kademe düşüşü kontrolünün sonucu (Gereksinim 1.7).
 */
sealed interface TierDowngradeResult {

    /** Açık oda sayısı zaten yeni kademenin sınırı içinde — yapılacak bir şey yok. */
    data object NoActionNeeded : TierDowngradeResult

    /**
     * Açık oda sayısı sınırı aşıyor ve kullanıcı hangi fazla odaları kapatacağını
     * seçmeli. [openRooms] seçim arayüzünü doldurmak için mevcut açık odaların tümüdür;
     * [allowedSlots] yeni kademenin izin verdiği (kaç tanesinin AÇIK KALABİLECEĞİ) sayı.
     */
    data class RoomSelectionRequired(
        val openRooms: List<RoomUsage>,
        val allowedSlots: Int,
    ) : TierDowngradeResult

    /**
     * Kullanıcı seçimi yok saydı (veya arayüz otomatik moda geçti); en eski kullanılan
     * odalar otomatik olarak kapatıldı. [closedCodes] kapatılan oda kodlarıdır.
     */
    data class RoomsAutoClosed(val closedCodes: List<String>) : TierDowngradeResult
}

/**
 * Gereksinim 1.7: bir kademe düşüşü (ör. Premium → Economy) sonrasında fazla oda
 * slotlarını çözer.
 *
 * Bu bir güvenlik sınırı DEĞİLDİR — sunucu zaten her zaman yeni bir oda AÇILMASINI
 * geçerli kademenin `roomSlots` sınırına göre reddeder (`switchRoom` callable'ı, bkz.
 * `functions/src/monetization.ts`), bu yüzden kullanıcı fazladan bir slot AÇAMAZ. Bu
 * use case saf bir ürün/UX akışıdır: kullanıcı bir düşüş nedeniyle ZATEN fazla odaya
 * sahipse (ör. Premium'da 5 oda açıkken Economy'ye düşerse, sınır 3'e iner), hangi
 * odaların kapatılacağını sorar; kullanıcı isteği yok sayarsa en son kullanılanları
 * açık bırakıp gerisini otomatik kapatır.
 *
 * SOLID/tek sorumluluk: [RoomSlotRepository] yalnızca oda verisini okur/kapatır; hangi
 * odaların "en eski" sayılacağına ve ne zaman otomatik kapatılacağına karar vermek bu
 * sınıfın tek işidir — bu ayrım [roomsToAutoClose]'u depo veya ağ olmadan doğrudan
 * (MockK ile sahte bir [RoomSlotRepository] üzerinden) test edilebilir kılar.
 *
 * @param roomSlotRepository Açık odaların ve son kullanım zamanlarının okunduğu/
 *   kapatıldığı kaynak.
 */
class TierDowngradeUseCase(
    private val roomSlotRepository: RoomSlotRepository,
) {
    /**
     * @param allowedSlots Geçerli kademenin izin verdiği oda slotu sayısı
     *   ([com.mcclabs.mook.domain.billing.PlanLimits.roomSlots]); `null` ise sınırsızdır
     *   (ör. Premium) ve hiçbir zaman eylem gerekmez.
     * @param autoCloseIfOverLimit `true` ise ve açık oda sayısı sınırın üzerindeyse,
     *   kullanıcıya sormak yerine doğrudan en eski kullanılan odaları otomatik kapatır.
     *   Arayüz bunu, kullanıcı önceki [TierDowngradeResult.RoomSelectionRequired]
     *   istemini yok saydığında (ör. sayfadan ayrıldığında) geçirir.
     */
    suspend operator fun invoke(
        allowedSlots: Int?,
        autoCloseIfOverLimit: Boolean = false,
    ): TierDowngradeResult {
        // null = sınırsız (Premium) — hiçbir kademe düşüşü bu sınırı aşamaz.
        if (allowedSlots == null) return TierDowngradeResult.NoActionNeeded

        val openRooms = roomSlotRepository.openRooms()
        if (openRooms.size <= allowedSlots) return TierDowngradeResult.NoActionNeeded

        if (!autoCloseIfOverLimit) {
            return TierDowngradeResult.RoomSelectionRequired(openRooms, allowedSlots)
        }

        val toClose = roomsToAutoClose(openRooms, allowedSlots)
        roomSlotRepository.closeRooms(toClose.map { it.code })
        return TierDowngradeResult.RoomsAutoClosed(toClose.map { it.code })
    }

    /**
     * En eski KULLANILAN (`lastActiveAtMillis` en küçük olan) odaları, kalan açık oda
     * sayısı [allowedSlots]'a inene kadar seçer — yani en son kullanılan [allowedSlots]
     * kadar oda açık kalır.
     *
     * Saf fonksiyon: depoya veya ağa dokunmaz, bu yüzden doğrudan test edilebilir.
     */
    internal fun roomsToAutoClose(openRooms: List<RoomUsage>, allowedSlots: Int): List<RoomUsage> {
        if (openRooms.size <= allowedSlots) return emptyList()
        return openRooms.sortedBy { it.lastActiveAtMillis }.take(openRooms.size - allowedSlots)
    }
}
