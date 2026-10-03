package com.mcclabs.mook.domain.billing

/**
 * Kullanıcının bir işlemi şu an yapamamasının sebebini tanımlayan kod.
 *
 * Limit Sheet (alt sayfa) hangi metni ve hangi aksiyon butonlarını göstereceğine bu değere
 * göre karar verir. Örneğin [DAILY_LIKES] için "+5 beğeni izle" ödüllü reklam butonu
 * anlamlıyken; [DAILY_MESSAGES], [DAILY_NEW_CHATS] veya [ROOM_SWITCHES] için bu buton
 * anlamsızdır ve KESİNLİKLE gösterilmemelidir (bkz. Gereksinim 1.5).
 *
 * Bu sebep tek başına "yükseltme bu sorunu çözer mi" sorusuna cevap vermez — o bilgi
 * [GateDecision.LimitReached.upgradeTo] alanındadır (`null` ise en üst kademede dahi
 * uygulanan bir adil kullanım tavanıdır, yükseltme önerilmemelidir).
 */
enum class LimitReason {
    /** Günlük beğeni (swipe/like) hakkı tükendi. */
    DAILY_LIKES,

    /** Günlük mesaj gönderme hakkı tükendi — plan limiti olabileceği gibi adil kullanım tavanı da olabilir. */
    DAILY_MESSAGES,

    /** Günlük yeni sohbet başlatma hakkı tükendi. Mevcut bir sohbete yanıt vermek bu sayaçla ilgili DEĞİLDİR (bkz. Gereksinim 1.6). */
    DAILY_NEW_CHATS,

    /** Günlük oda (room) değiştirme hakkı tükendi. */
    ROOM_SWITCHES,

    /** Günlük "beni beğenenler" açma hakkı tükendi. */
    LIKED_ME_UNLOCKS,

    /** Günlük geri alma (rewind) hakkı tükendi. */
    REWINDS,

    /** Aylık boost hakkı tükendi. */
    BOOSTS,
}
