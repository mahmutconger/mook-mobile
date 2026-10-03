package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.MatchResult

/**
 * Gereksinim 2.12 (Faz 4): aktif ya da az önce sona ermiş bir Boost'un sunucudaki özeti --
 * `functions/src/monetization.ts` — `getBoostSummary` callable'ının istemci karşılığı.
 *
 * [viewsGained], bu Boost aktıfken (`recordProfileVisit`'in arttırdığı) profilin ekstra
 * kaç kez görüntülendiğini taşır -- [BoostManagerUseCase][com.mcclabs.mook.domain.billing.BoostManagerUseCase]
 * bunu, Boost bittiğinde gösterilen özet diyalogda ("Boost bitti! Profilin X kişiye
 * fazladan gösterildi") kullanır. [boostUntilMillis] sunucunun hala GEÇERLİ bildiği bir
 * Boost varsa onun bitiş anını taşır; hiçbir Boost hiç başlatılmadıysa ya da tamamen
 * unutulmuşsa `null`dır.
 */
data class BoostSummary(val viewsGained: Int, val boostUntilMillis: Long?)

/**
 * "Beni beğenenler" listesinden bir profil açma denemesinin sonucu.
 * Sunucu: `unlockLikedMe` (ödüllü reklam sayacı taban hakka eklenir; aynı profil iki kez
 * açılırsa hak düşülmez).
 */
sealed interface LikedMeUnlockResult {
    data class Unlocked(val alreadyUnlocked: Boolean) : LikedMeUnlockResult

    /**
     * Günlük açma hakkı doldu. Ücretsiz/Ekonomik kullanıcıya limit sayfasında
     * `RewardType.LIKED_ME_UNLOCK` ödüllü reklamı teklif edilmelidir.
     */
    data object DailyLimitReached : LikedMeUnlockResult

    /** Bu kademede ödüllü yol da yok; yalnızca yükseltme çözer. */
    data object UpgradeRequired : LikedMeUnlockResult

    /** Profil kullanıcıyı (artık) beğenmiyor; hak düşülmedi. */
    data object NotLikedByProfile : LikedMeUnlockResult

    data class Failed(val message: String?) : LikedMeUnlockResult
}

interface InteractionRepository {
    suspend fun swipeUser(toUserId: String, isLike: Boolean): MatchResult
    suspend fun checkMutualMatch(withUserId: String): Boolean

    /** Bkz. [LikedMeUnlockResult]. Hata fırlatmaz; her sonuç türlü bir değer olarak döner. */
    suspend fun unlockLikedMe(profileUid: String): LikedMeUnlockResult

    /** Removes the caller's latest pass through the server-authoritative rewind flow. */
    suspend fun rewindLastPass(): Result<String>

    /** Starts the server-authoritative 30-minute discovery Boost. */
    suspend fun activateBoost(): Result<Long>

    /**
     * Gereksinim 2.12 (Faz 4): sunucudaki `getBoostSummary` callable'ını çağırır --
     * bkz. [BoostSummary] KDoc'u.
     */
    suspend fun getBoostSummary(): Result<BoostSummary>
}
