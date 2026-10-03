package com.mcclabs.mook.domain.model

/**
 * "Beni Beğenenler" listesindeki tek bir giriş.
 *
 * Kilitli girişlerde beğenenin kimliği istemciye HİÇ gelmez: yalnızca opak [entryToken] ve
 * bulanık gösterilecek [photoUrl] bulunur; [profile] `null`dır. Kilit açılınca (Premium veya
 * günlük hak/ödüllü reklam) sunucu profili açık döner.
 */
data class LikedMeEntry(
    val entryToken: String,
    val likedAtMillis: Long,
    val isUnlocked: Boolean,
    val photoUrl: String?,
    val profile: LikedMeProfile?,
)

/** Kilidi açılmış beğenenin profil özeti. */
data class LikedMeProfile(
    val uid: String,
    val name: String,
    val age: Int?,
    val countryCode: String?,
    val languageCode: String?,
    val bio: String,
    val verified: Boolean,
)

/**
 * `getLikedMe` sonucunun tamamı.
 *
 * @property totalCount Üst sayaçta gösterilen bekleyen beğeni sayısı.
 * @property hasMore Sunucu sınırına ulaşıldı (sayaç "100+" gösterilir).
 * @property revealAll Premium: tüm profiller açık.
 * @property unlocksRemainingToday Bugün kalan açma hakkı; `null` = sınırsız.
 */
data class LikedMePage(
    val entries: List<LikedMeEntry>,
    val totalCount: Int,
    val hasMore: Boolean,
    val revealAll: Boolean,
    val unlocksRemainingToday: Int?,
)
