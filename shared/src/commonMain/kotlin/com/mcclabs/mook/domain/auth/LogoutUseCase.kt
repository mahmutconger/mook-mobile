package com.mcclabs.mook.domain.auth

import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.repository.AuthRepository
import com.mcclabs.mook.domain.repository.PushTokenRepository
import com.mcclabs.mook.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * [LogoutUseCase] sonucunda hangi temizlik adımlarının gerçekten tamamlandığı. Kimlik oturumu
 * her durumda kapatılır; bu alanlar yalnızca gözlemlenebilirlik (log/analitik) içindir.
 */
data class LogoutResult(
    /** Cihazın FCM jetonu `users/{uid}.fcmTokens` dizisinden silindi mi? */
    val pushTokenRemoved: Boolean,
    /** RevenueCat kimliği cihazdan ayrıldı mı (`Purchases.logOut()`)? */
    val billingIdentityDetached: Boolean,
)

/**
 * "Katı Çıkış Sırası" — uygulamadaki TEK tam çıkış noktası (Ayarlar, EULA reddi, hesap yasağı).
 *
 * Adımlar KESİN olarak bu sırayla ve birbiri ardına çalışır:
 * 1. **FCM jetonunu sil** ([PushTokenRepository.unregisterCurrentDevice]) — kimlik oturumu hâlâ
 *    açıkken yapılmalıdır; `signOut()` sonrasında kullanıcının belgesine yazma izni kalmaz ve
 *    jeton "zombi" olarak kalıp önceki kullanıcının bildirimlerini bu cihaza taşımaya devam eder.
 * 2. **RevenueCat kimliğini ayır** ([SubscriptionRepository.logOut]) — bir sonraki kullanıcı bu
 *    kullanıcının yetkisini/önbelleğini devralmasın.
 * 3. **Kimlik oturumunu kapat** ([AuthRepository.logout] → Firebase `signOut()`).
 *
 * Hata toleransı: 1. ve 2. adımdaki bir hata ya da [stepTimeoutMillis] süresini aşan bir
 * bekleme (ör. çevrimdışı Firestore yazması sunucu onayını sonsuza dek bekleyebilir) loglanır
 * ve akış DEVAM eder — kullanıcı asla çıkış yapamaz halde bırakılmaz. 3. adım her zaman çalışır.
 *
 * İptal güvenliği: dizi [NonCancellable] bağlamda çalışır; çağıran ekran (ViewModel kapsamı)
 * akışın ortasında yok edilse bile "jeton silindi ama oturum açık kaldı" gibi yarım bir çıkış
 * oluşmaz. Toplam süre en fazla `2 × stepTimeoutMillis` + `signOut()` süresiyle sınırlıdır.
 */
class LogoutUseCase(
    private val pushTokenRepository: PushTokenRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val authRepository: AuthRepository,
    private val stepTimeoutMillis: Long = DEFAULT_STEP_TIMEOUT_MILLIS,
) {

    suspend operator fun invoke(): LogoutResult = withContext(NonCancellable) {
        val pushTokenRemoved = runCleanupStep("FCM jetonu silme") {
            pushTokenRepository.unregisterCurrentDevice()
        }
        val billingIdentityDetached = runCleanupStep("RevenueCat çıkışı") {
            subscriptionRepository.logOut()
        }
        authRepository.logout()
        LogoutResult(pushTokenRemoved, billingIdentityDetached)
    }

    /**
     * Bir temizlik adımını zaman aşımıyla çalıştırır; başarısızlıkta `false` döner ve ASLA
     * istisna fırlatmaz (dış iptal hariç — [NonCancellable] içinde zaten oluşmaz).
     */
    private suspend fun runCleanupStep(stepName: String, block: suspend () -> Unit): Boolean = try {
        withTimeout(stepTimeoutMillis) { block() }
        true
    } catch (timeout: TimeoutCancellationException) {
        Log.e("Çıkış adımı zaman aşımına uğradı, devam ediliyor: $stepName", timeout)
        false
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e("Çıkış adımı başarısız oldu, devam ediliyor: $stepName", error)
        false
    }

    companion object {
        /** Her temizlik adımı için azami bekleme süresi. */
        const val DEFAULT_STEP_TIMEOUT_MILLIS: Long = 5_000L
    }
}
