package com.mcclabs.mook.domain.repository

/**
 * Kullanıcının WalkMatch (Mook) profilinin sunucu tarafında etkinleştirilmesi.
 *
 * `users/{uid}.isMookActive` istemci tarafından YAZILAMAZ (Firestore kuralları reddeder);
 * bayrak yalnızca `activateMookProfile` Cloud Function'ı tarafından açılır.
 */
interface MookProfileRepository {

    /**
     * Oturum açmış kullanıcının profilini Discover için etkinleştirir. İdempotenttir ve her
     * oturum açılışında güvenle çağrılabilir.
     *
     * @return Profil sunucuda etkin ise `true`; ağ hatası, App Check reddi ya da hesap yasağı
     *   durumunda `false`. Hata asla fırlatılmaz — girişi engellemez, bir sonraki açılışta
     *   yeniden denenir.
     */
    suspend fun activate(): Boolean
}
