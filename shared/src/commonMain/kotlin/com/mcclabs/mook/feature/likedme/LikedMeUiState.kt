package com.mcclabs.mook.feature.likedme

import com.mcclabs.mook.domain.billing.EntitlementState
import com.mcclabs.mook.domain.billing.LimitReason
import com.mcclabs.mook.domain.model.LikedMeEntry

/** "Beni Beğenenler" listesindeki bir satır: profil ya da yerel reklam yuvası. */
sealed interface LikedMeListItem {
    val key: String

    data class Profile(val entry: LikedMeEntry) : LikedMeListItem {
        override val key: String get() = "liker_${entry.entryToken}"
    }

    /** Her 8. konumdaki (7, 15, 23 …) yerel reklam yuvası. */
    data class NativeAd(val slot: Int) : LikedMeListItem {
        override val key: String get() = "native_ad_$slot"
    }
}

/**
 * "Beni Beğenenler" ekranının tek durum nesnesi.
 *
 * @property items Ekranda çizilecek satırlar (reklam yuvaları yerleştirilmiş hâli).
 * @property totalCount Üst sayaç; [hasMore] ise "100+" biçiminde gösterilir.
 * @property unlockingToken Kilidi açılmakta olan giriş (düğmede ilerleme göstergesi için).
 * @property limitReason `null` değilse limit sayfası (ödüllü reklam / yükseltme) gösterilir.
 * @property errorMessage Liste yüklenemediyse tam ekran hata metni.
 * @property message Tek seferlik bilgilendirme (snackbar).
 */
data class LikedMeUiState(
    val isLoading: Boolean = true,
    val entries: List<LikedMeEntry> = emptyList(),
    val items: List<LikedMeListItem> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val revealAll: Boolean = false,
    val unlocksRemainingToday: Int? = null,
    val entitlement: EntitlementState = EntitlementState(),
    val unlockingToken: String? = null,
    val limitReason: LimitReason? = null,
    val errorMessage: String? = null,
    val message: String? = null,
) {
    /** Sayaç metni için sayı: sunucu sınırına ulaşıldıysa "100+". */
    val counterLabel: String get() = if (hasMore) "$totalCount+" else totalCount.toString()

    /** Reklamlı (Ücretsiz) planda listeye yerel reklam yuvaları eklenir. */
    val showsAds: Boolean get() = entitlement.isResolved && entitlement.limits.showsAds
}
