package com.mcclabs.mook.data.ads

import com.mcclabs.mook.domain.billing.AdCounterRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [AdCounterRepository]'nin yerel uygulaması. Anahtarlar kullanıcı kimliğiyle ayrıştırılır;
 * artırma işlemleri bir [Mutex] ile sıralanır (art arda iki ziyaret aynı değeri okuyamaz).
 *
 * @param currentUserId Oturum açmış kullanıcının uid'si; oturum yoksa "anonymous".
 */
class AdCounterRepositoryImpl(
    private val local: AdCounterLocalDataSource,
    private val currentUserId: () -> String = { Firebase.auth.currentUser?.uid ?: "anonymous" },
) : AdCounterRepository {

    private val mutex = Mutex()

    private fun visitsKey() = "profile_visits_${currentUserId()}"
    private fun upsellKey() = "last_upsell_${currentUserId()}"

    override suspend fun incrementProfileVisits(): Int = mutex.withLock {
        val key = visitsKey()
        val next = (local.getLong(key) ?: 0L) + 1
        local.putLong(key, next)
        next.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override suspend fun resetProfileVisits() = mutex.withLock { local.remove(visitsKey()) }

    override suspend fun lastUpsellTimestamp(): Long? = local.getLong(upsellKey())

    override suspend fun setLastUpsellTimestamp(timestampMillis: Long) {
        local.putLong(upsellKey(), timestampMillis)
    }
}
