package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.LikedMePage

/**
 * "Beni Beğenenler" veri kaynağı. İstemci `interactions` koleksiyonunu "bana gelen beğeniler"
 * olarak sorgulayamaz (Firestore kuralları); liste ve kilit açma yalnızca sunucu üzerinden yapılır.
 */
interface LikedMeRepository {

    /** Kullanıcıyı beğenen ve henüz karşılık verilmemiş kişileri getirir. */
    suspend fun load(): Result<LikedMePage>

    /**
     * Opak giriş jetonuyla bir profilin kilidini açar (günlük hak veya ödüllü reklam bonusu düşülür).
     * Hata fırlatmaz; her sonuç türlü bir değer olarak döner.
     */
    suspend fun unlock(entryToken: String): LikedMeUnlockResult
}
