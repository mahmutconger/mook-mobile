package com.mcclabs.mook.domain.billing

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Reklam Sıklığı ve Upsell Kuralları — "3 ziyaret, 8 satır, günde 1" kurallarının TEK
 * tanım noktası. Hepsi saf fonksiyondur (Firestore/AdMob'a dokunmaz) ve birim testlidir.
 */
object AdFrequencyRules {

    /** Her kaç profil ziyaretinde bir geçiş (interstitial) reklamı denenir. */
    const val PROFILE_VISITS_PER_INTERSTITIAL = 3

    /** "Beni Beğenenler" listesinde her kaç satırda bir yerel (native) reklam yer alır. */
    const val LIKED_ME_NATIVE_AD_EVERY = 8

    /**
     * [visitCount] (son gösterilen reklamdan bu yana ziyaret sayısı) için reklam zamanı mı?
     * Sayaç yalnızca reklam GERÇEKTEN gösterildiğinde sıfırlanır; reklam hazır değilse veya
     * genel sıklık sınırına takıldıysa bir SONRAKİ ziyarette yeniden denenir.
     */
    fun isProfileVisitAdDue(visitCount: Int): Boolean = visitCount >= PROFILE_VISITS_PER_INTERSTITIAL

    /**
     * Listedeki [index] konumunun (0 tabanlı) yerel reklam yuvası olup olmadığı:
     * 7, 15, 23 … (her 8. konum).
     */
    fun isLikedMeAdSlot(index: Int): Boolean = index >= 0 && (index + 1) % LIKED_ME_NATIVE_AD_EVERY == 0

    /**
     * Profil listesine her 8. konuma bir reklam yuvası yerleştirir. Reklam gösterilmeyecekse
     * ([showAds] = false) liste olduğu gibi döner. Listenin sonuna boş reklam yuvası eklenmez.
     */
    fun <T, R> interleaveLikedMeAds(items: List<T>, showAds: Boolean, item: (T) -> R, ad: (slot: Int) -> R): List<R> {
        if (!showAds) return items.map(item)
        val result = ArrayList<R>(items.size + items.size / (LIKED_ME_NATIVE_AD_EVERY - 1))
        var slot = 0
        for (entry in items) {
            if (isLikedMeAdSlot(result.size)) result += ad(slot++)
            result += item(entry)
        }
        return result
    }

    /**
     * Günlük upsell kartı gösterilebilir mi? Kart, kullanıcının YEREL takvim gününde en fazla
     * BİR kez gösterilir; [lastShownMillis] `null` ise hiç gösterilmemiştir.
     */
    fun isDailyUpsellDue(lastShownMillis: Long?, nowMillis: Long, timeZone: TimeZone): Boolean {
        if (lastShownMillis == null) return true
        if (nowMillis < lastShownMillis) return false // Saat geri alındıysa yeniden göstermeyiz.
        val lastDay = Instant.fromEpochMilliseconds(lastShownMillis).toLocalDateTime(timeZone).date
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
        return today > lastDay
    }
}
