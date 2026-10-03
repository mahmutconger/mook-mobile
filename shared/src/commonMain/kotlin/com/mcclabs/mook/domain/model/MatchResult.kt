package com.mcclabs.mook.domain.model

sealed class MatchResult {
    object MutualMatch : MatchResult()
    object SingleLike : MatchResult()
    object Pass : MatchResult()

    /**
     * Gereksinim 1 (Faz 6): sunucuya ULAŞILAMADI (zaman aşımı ya da bağlantı kaybı) — ne
     * başarı ne kesin bir hata. Eylem [com.mcclabs.mook.domain.billing.PendingActionQueue]'ya
     * kalıcı olarak kuyruğa alındı ve bağlantı kurulduğunda otomatik yeniden denenecek (bkz.
     * [com.mcclabs.mook.domain.billing.SwipeTimeoutFallbackHandler],
     * [com.mcclabs.mook.domain.billing.RecoverPendingSwipeActionsUseCase]). Çağıranlar bunu
     * bir HATA olarak DEĞİL, bilgilendirici bir durum olarak ele almalıdır.
     */
    object QueuedOffline : MatchResult()
    data class Error(val message: String) : MatchResult()
}
