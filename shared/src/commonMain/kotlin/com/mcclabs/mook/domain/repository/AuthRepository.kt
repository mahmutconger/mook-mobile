package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.AuthResult
import com.mcclabs.mook.domain.model.UserProfile

/**
 * Repository interface defining authentication and profile management operations.
 *
 * Implementations handle the actual data source communication (e.g., remote API,
 * local fake data) while consumers depend only on this contract.
 */
interface AuthRepository {

    /**
     * Authenticates a user with email and password credentials.
     *
     * @param email The user's email address.
     * @param password The user's password.
     * @return [AuthResult.Success] with the authenticated [UserProfile] on success,
     *         or [AuthResult.Error] with a descriptive message on failure.
     */
    suspend fun login(email: String, password: String): AuthResult<UserProfile>

    /**
     * Registers a new user account with the provided credentials.
     *
     * @param email The email address for the new account.
     * @param password The password for the new account.
     * @param displayName The display name for the new user.
     * @return [AuthResult.Success] with the newly created [UserProfile] on success,
     *         or [AuthResult.Error] with a descriptive message on failure.
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): AuthResult<UserProfile>

    /**
     * Completes or updates a user's profile with additional information.
     *
     * @param userId The unique identifier of the user whose profile is being completed.
     * @param displayName The user's display name.
     * @param bio A short biography or description.
     * @param interests A list of the user's interests.
     * @param avatarUri An optional URI pointing to the user's avatar image.
     * @param discoveryPhotos URIs of the user's Discover gallery photos.
     * @param gender The user's selected gender identity, if provided.
     * @param birthDateMillis The user's date of birth as epoch milliseconds, if provided.
     * @param languageCode The user's preferred language code (e.g. "EN-US").
     * @param countryCode ISO region code of the user's country, if provided.
     * @param discoverVisible Whether the user opts in to appear in Discover.
     * @return [AuthResult.Success] with the updated [UserProfile] on success,
     *         or [AuthResult.Error] with a descriptive message on failure.
     */
    suspend fun completeProfile(
        userId: String,
        displayName: String,
        bio: String,
        interests: List<String>,
        avatarUri: String?,
        discoveryPhotos: List<String> = emptyList(),
        gender: com.mcclabs.mook.domain.model.Gender? = null,
        birthDateMillis: Long? = null,
        languageCode: String? = null,
        countryCode: String? = null,
        discoverVisible: Boolean = true
    ): AuthResult<UserProfile>

    /**
     * Whether the signed-in user has accepted the EULA / UGC policy.
     *
     * Google Play requires every user to accept before entering the app, so this
     * gates the post-login flow for accounts created before the EULA existed.
     * Returns `false` if there is no signed-in user or the field is absent.
     */
    suspend fun hasAcceptedEula(): Boolean

    /**
     * Records that the signed-in user accepted the EULA now, writing both
     * `acceptedEula = true` and `acceptedEulaAt` (epoch millis) to their document.
     */
    suspend fun acceptEula()

    /**
     * YALNIZCA kimlik oturumunu kapatır (Firebase `signOut()`).
     *
     * Tam çıkış için bunu doğrudan ÇAĞIRMAYIN; FCM jetonu ve RevenueCat kimliği de temizlenmelidir.
     * Doğru sıra `com.mcclabs.mook.domain.auth.LogoutUseCase` tarafından uygulanır.
     */
    suspend fun logout()

    /**
     * Giriş yapmış kullanıcının e-posta adresini döndürür (yoksa `null`).
     *
     * Yerel oturumdan okunan senkron bir değerdir; ViewModel'ın doğrudan Firebase'e
     * bağımlı olmaması ve birim testlerde kolayca sahtelenebilmesi için bu arayüzde
     * soyutlanmıştır.
     */
    fun currentUserEmail(): String?

    /**
     * Kullanıcının hesabını ve tüm ilişkili verisini kalıcı olarak siler.
     *
     * Silme işlemi güvenli bir sunucu ortamında (`deleteAccount` Cloud Function) yapılır:
     * Storage dosyaları, Firestore dokümanları (interactions/matches/usage/users),
     * RevenueCat aboneci kaydı ve son olarak Firebase Auth hesabı temizlenir. İstemci
     * bu ağır silmeyi doğrudan yapamaz çünkü güvenlik kuralları buna izin vermez.
     *
     * Hesap oluşturmaya izin veren her uygulamanın uygulama içi hesap silme yolu
     * sunması Google Play tarafından zorunludur (2024).
     *
     * @return Başarıda [AuthResult.Success] (`Unit`), aksi halde açıklayıcı bir
     *         mesajla [AuthResult.Error].
     */
    suspend fun deleteAccount(): AuthResult<Unit>
}
