package com.mcclabs.mook.domain.moderation

import com.mcclabs.mook.domain.auth.LogoutUseCase
import com.mcclabs.mook.domain.repository.ModerationRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Gereksinim 1.13 (Moderasyon & Yasaklamalar).
 *
 * Sunucu bir kullanıcıyı yasakladığında (`users/{uid}.isBanned == true`, yalnızca
 * `moderateUser` Cloud Function'ı — Admin SDK ile — tarafından ayarlanabilir), uygulama
 * TEK bir yerden üç şeyi garanti eder:
 * 1. **Zorla çıkış** — [enforceBan] Firebase Auth oturumunu VE RevenueCat kimliğini temizler.
 * 2. **Tüm API çağrılarının engellenmesi** — [ModerationRepository] zaten [ModerationGate]'i
 *    canlı tutar (bkz. `ModerationRepositoryImpl`), bu yüzden [isBanned] `true` olduğu AN,
 *    [enforceBan] henüz çalışmamış olsa bile, [com.mcclabs.mook.data.appHttpsCallable] üzerinden
 *    giden HER istek zaten reddedilir.
 * 3. **Kalıcı "Hesap Askıya Alındı" ekranı** — `App.kt`, [isBanned] `true` olduğunda
 *    `AppNavGraph()`'ı TAMAMEN değiştirir (koşullu olarak hiç compose etmez), böylece
 *    kullanıcı hangi ekranda olursa olsun oradan çıkamaz.
 *
 * **İş kuralı — yasaklı kullanıcılar uygulama içinden geri ödeme talep edemez**: uygulamanın
 * zaten bir "Geri Ödeme İste" akışı YOKTUR (tüm geri ödemeler Google Play'in kendi arayüzünden
 * yürütülür — Play Billing bunu başka türlü desteklemez), bu yüzden burada engellenecek bir
 * istemci akışı yoktur. Kural, Kullanım Şartları'nda ("yasaklı hesaplar için geri ödeme talebi
 * kabul edilmez") sözleşmesel olarak ifade edilir; ileride bir "Geri Ödeme İste" akışı
 * eklenirse, o akış İLK adımda bu use case'in [isBanned] durumunu kontrol ETMELİDİR.
 */
class UserModerationUseCase(
    private val moderationRepository: ModerationRepository,
    private val logoutUseCase: LogoutUseCase,
) {
    /** `App.kt`'nin, "Hesap Askıya Alındı" ekranını göstermek için gözlemlediği tek gerçek. */
    val isBanned: StateFlow<Boolean> = moderationRepository.isBanned

    /**
     * `App.kt`'deki global efekt [isBanned] `true` olduğunu gördüğü AN (yalnızca bir kez,
     * `true`'ya her geçişte) çağrılır.
     */
    suspend fun enforceBan() {
        // Yasaklı hesap da AYNI katı sırayla çıkarılır: FCM jetonu → RevenueCat → signOut.
        // Aksi halde yasaklanan kullanıcının jetonu bu cihazda zombi olarak kalırdı.
        logoutUseCase()
    }
}
