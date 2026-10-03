package com.mcclabs.mook.domain.account

import com.mcclabs.mook.domain.billing.SubscriptionRepository
import com.mcclabs.mook.domain.billing.Tier

/**
 * Gereksinim 1.9 (Hesap Kurtarma ve Birden Fazla Hesap) için çakışma tespiti sonucu.
 *
 * Bir cihazda hâlihazırda RevenueCat'e tanımlı bir "aktif ücretli üyelik" varken kullanıcı
 * YENİ bir SSO sağlayıcısıyla (yeni bir Firebase UID ile) oturum açarsa, bu iki kimlikten
 * hangisinin abonelikle ilişkilendirileceği BELİRSİZDİR — sessizce üzerine yazmak, önceki
 * kullanıcının ödediği aboneliği sessizce kaybetmesine yol açabilir. Bu yüzden bu durumda
 * kullanıcıya sorulur.
 */
sealed interface AccountMergeResult {
    /**
     * Cihazda ya hiç önceden tanımlı bir kimlik yok, ya da önceden tanımlı kimlik zaten
     * giriş yapılmak istenen kimlikle AYNI (ör. uygulamanın soğuk başlangıcı — bu bir çakışma
     * DEĞİLDİR), ya da önceki kimliğin aktif ücretli bir yetkilendirmesi yok (kaybedilecek
     * bir şey olmadığından devretmeye gerek yoktur). Bu durumlarda giriş doğrudan yapılabilir.
     */
    data object NoConflict : AccountMergeResult

    /**
     * Cihazda BAŞKA bir kimliğe (öncekine) ait, hâlâ aktif bir ücretli abonelik varken yeni
     * bir kimlikle giriş yapılmak isteniyor. Kullanıcıya "Aktarım" (RevenueCat'in `logIn()`
     * çağrısı, panoda "Transfer" davranışı olarak yapılandırılmış olmalıdır — bu, aboneliği
     * eski kimlikten yeni kimliğe taşır) veya "Destek ile iletişime geç" seçenekleri sunulmalıdır.
     */
    data class ConflictDetected(val activeTier: Tier, val expiresAtMillis: Long?) : AccountMergeResult
}

/**
 * Gereksinim 1.9: yeni bir SSO oturumu açılmadan ÖNCE, cihazdaki mevcut RevenueCat kimliğiyle
 * bir çakışma olup olmadığını tespit eder ve (kullanıcı onayladığında) aboneliği yeni kimliğe
 * aktarır.
 *
 * NEDEN BURADA: RevenueCat kimliğini Firebase UID'sine bağlayan tek nokta daha önce
 * `MookApplication`'daki KOŞULSUZ bir `FirebaseAuth.AuthStateListener` idi — Firebase'in
 * `currentUser.uid` değeri, oturum açma/kayıt tamamlanır tamamlanmaz SDK içinde EŞ ZAMANLI
 * olarak değiştiğinden, herhangi bir ViewModel seviyesindeki kapı (gate) bu global dinleyici
 * tarafından her zaman "yarışta" geride kalırdı. Bu use case'in çakışma kontrolü bu yüzden
 * (`MookApplication`'ın kendisi değil) `App.kt`'deki TEK, global `signedInUid` efektinin
 * İÇİNDE, `subscriptionRepository.logIn()` çağrılmadan HEMEN ÖNCE çalıştırılmalıdır — bkz.
 * `App.kt`'deki kullanım.
 */
class AccountMergeUseCase(
    private val subscriptionRepository: SubscriptionRepository,
) {
    /**
     * @param newUserId Az önce oturum açılan/kaydolunan yeni Firebase UID'si.
     * @return Bu kimlikle güvenle `logIn()` çağrılabilir mi, yoksa kullanıcıya önce
     *   sorulmalı mı.
     */
    fun detectConflict(newUserId: String): AccountMergeResult {
        val currentId = subscriptionRepository.currentIdentifiedUserId()
        val entitlement = subscriptionRepository.state.value
        val hasActivePaidEntitlement = entitlement.isResolved && entitlement.tier != Tier.FREE
        val isDifferentIdentity = currentId != null && currentId != newUserId

        return if (isDifferentIdentity && hasActivePaidEntitlement) {
            AccountMergeResult.ConflictDetected(
                activeTier = entitlement.tier,
                expiresAtMillis = entitlement.expiresAtMillis,
            )
        } else {
            AccountMergeResult.NoConflict
        }
    }

    /**
     * Kullanıcı "Aktar" seçeneğini onayladığında çağrılır. RevenueCat panosunda "Restore
     * Behavior" ayarının "Transfer" olarak yapılandırılmış olması GEREKİR — bu sayede
     * `logIn(newUserId)` çağrısı, eski (anonim veya önceki) kimlikteki aktif yetkilendirmeyi
     * sessizce YENİ kimliğe taşır, çoğaltmaz ya da eski kimlikte bırakmaz.
     */
    suspend fun proceedWithTransfer(newUserId: String) {
        subscriptionRepository.logIn(newUserId)
    }
}
