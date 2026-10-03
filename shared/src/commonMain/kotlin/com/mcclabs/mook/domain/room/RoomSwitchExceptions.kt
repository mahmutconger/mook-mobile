package com.mcclabs.mook.domain.room

/**
 * Kullanıcının bugünkü oda değiştirme hakkı (plan tabanı + varsa ödüllü reklam bonusu) doldu.
 *
 * Bu bir uygulama hatası DEĞİL, beklenen bir ürün durumudur: sunum katmanı bunu yakalayıp
 * genel bir hata metni yerine limit sayfasını (yükseltme / yarın tekrar dene) göstermelidir.
 * Günlük hak, kullanıcının saat dilimine göre takvim günü başında sıfırlanır (sunucu kuralı).
 */
class DailyRoomChangeLimitReachedException(cause: Throwable? = null) :
    Exception("Bugünkü oda değiştirme hakkın doldu.", cause)

/** `switchRoom` Cloud Function'ının döndürdüğü, istemcinin tanıdığı hata kodları. */
object RoomSwitchErrorCodes {
    /** Günlük oda değiştirme hakkı doldu (`functions/src/roomSwitch.ts`). */
    const val DAILY_LIMIT: String = "daily-room-switch-limit"

    /**
     * Eski sunucu sürümlerinin slot doluyken döndürdüğü kapasite hatası. Atomik takas devreye
     * girdikten sonra `switchRoom` bunu artık üretmez; sunucu ve istemci ayrı dağıtıldığı için
     * geriye dönük uyumluluk amacıyla tanınmaya devam eder.
     */
    const val SLOT_LIMIT: String = "room-slot-limit"
}
