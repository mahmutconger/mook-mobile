package com.mcclabs.mook.domain.billing

import com.mcclabs.mook.util.getCurrentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone

/**
 * Günlük Premium upsell kartının tek sahibi.
 *
 * Bir geçiş reklamı kapatıldığında [onInterstitialClosed] çağrılır; kart kullanıcının yerel
 * takvim gününde EN FAZLA BİR KEZ görünür (`lastUpsellTimestamp` yerelde saklanır). Kart,
 * hangi ekranda olursa olsun uygulama düzeyindeki ana bilgisayar (`AppNavGraph`) tarafından
 * çizilir — beğeni sonrası kapanan profil ekranı gibi durumlarda kart kaybolmaz.
 */
class DailyUpsellCoordinator(
    private val adCounters: AdCounterRepository,
    private val now: () -> Long = { getCurrentTimeMillis() },
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val mutableVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = mutableVisible.asStateFlow()
    private val mutex = Mutex()

    /** Bir geçiş reklamı kapatıldı; bugün henüz gösterilmediyse kartı gösterir ve zamanı kaydeder. */
    suspend fun onInterstitialClosed() = mutex.withLock {
        val current = now()
        if (!AdFrequencyRules.isDailyUpsellDue(adCounters.lastUpsellTimestamp(), current, timeZone())) return@withLock
        adCounters.setLastUpsellTimestamp(current)
        mutableVisible.value = true
    }

    /** Kart kapatıldı veya kullanıcı Paywall'a yönlendirildi. */
    fun dismiss() {
        mutableVisible.value = false
    }
}

/**
 * Herhangi bir geçiş reklamı gösterilip KAPATILDIKTAN sonra günlük upsell'i tetikleyen
 * dekoratör. ViewModel'ler upsell'den habersizdir; kural tek noktada uygulanır.
 */
class UpsellAwareInterstitialGateway(
    private val delegate: LikeInterstitialGateway,
    private val upsellCoordinator: DailyUpsellCoordinator,
) : LikeInterstitialGateway {

    override suspend fun attemptShowAfterTransition(isFree: Boolean, likesEver: Int): com.mcclabs.mook.ads.LikeInterstitialAttempt {
        val attempt = delegate.attemptShowAfterTransition(isFree, likesEver)
        if (attempt.adShown) upsellCoordinator.onInterstitialClosed()
        return attempt
    }

    override fun recordAction(isFree: Boolean, attempt: com.mcclabs.mook.ads.LikeInterstitialAttempt, succeeded: Boolean) {
        delegate.recordAction(isFree, attempt, succeeded)
    }

    override suspend fun showProfileVisitInterstitial(isFree: Boolean): Boolean {
        val shown = delegate.showProfileVisitInterstitial(isFree)
        if (shown) upsellCoordinator.onInterstitialClosed()
        return shown
    }
}
