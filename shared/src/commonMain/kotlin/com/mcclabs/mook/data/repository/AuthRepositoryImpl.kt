package com.mcclabs.mook.data.repository

import com.mcclabs.mook.domain.model.AuthResult
import com.mcclabs.mook.domain.model.UserProfile
import com.mcclabs.mook.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.storage.storage
import dev.gitlive.firebase.functions.functions
import com.mcclabs.mook.data.appFirestore
import com.mcclabs.mook.util.Log

class AuthRepositoryImpl : AuthRepository {

    /**
     * Uploads a locally picked photo to Storage and returns its public download URL.
     *
     * Picked URIs point at the device's own filesystem, so they are meaningless to every
     * other user — the bytes have to be uploaded before the profile can reference them.
     * A [uri] that is already an `http(s)` URL has been uploaded before and is returned
     * unchanged. Returns `null` if the upload fails.
     */
    private suspend fun uploadPhoto(userId: String, uri: String, fileName: String): String? {
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            Log.d("Fotoğraf zaten yüklenmiş, atlanıyor: $fileName")
            return uri
        }
        val path = "users/$userId/$fileName"
        return try {
            val file = com.mcclabs.mook.util.storageFileFromUri(uri)
            if (file == null) {
                Log.e("Fotoğraf URI'si çözümlenemedi: $uri")
                return null
            }
            Log.d("Yükleniyor: $path")
            val reference = Firebase.storage.reference.child(path)
            // The Storage rules reject a non-image content type, so each platform
            // supplies the answer it can actually be sure of.
            reference.putFile(file, com.mcclabs.mook.util.photoUploadMetadata())
            val downloadUrl = reference.getDownloadUrl()
            Log.d("Yüklendi: $path")
            downloadUrl
        } catch (e: Exception) {
            Log.e("Yükleme başarısız: $path (kaynak URI: $uri)", e)
            null
        }
    }

    override suspend fun login(email: String, password: String): AuthResult<UserProfile> {
        return try {
            val authResult = Firebase.auth.signInWithEmailAndPassword(email, password)
            val uid = authResult.user?.uid ?: return AuthResult.Error("User not found")

            // Activate Mook
            appFirestore.collection("users").document(uid).set(mapOf("isMookActive" to true), merge = true)

            // Fetch profile
            val document = appFirestore.collection("users").document(uid).get()
            
            val fetchedEmail = runCatching { document.get<String>("email") }.getOrDefault(email)
            val displayName = runCatching { document.get<String>("displayName") }.getOrDefault("Unknown User")
            val bio = runCatching { document.get<String>("bio") }.getOrDefault("")
            val interests = runCatching { document.get<List<String>>("interests") }.getOrDefault(emptyList())
            val avatarUrl = runCatching { document.get<String>("avatarUrl") }.getOrNull()

            AuthResult.Success(
                data = UserProfile(
                    id = uid,
                    email = fetchedEmail,
                    displayName = displayName,
                    bio = bio,
                    interests = interests,
                    avatarUrl = avatarUrl
                )
            )
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.Error("Invalid credentials")
        } catch (e: FirebaseAuthException) {
            AuthResult.Error(e.message ?: "Authentication failed")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "An unknown error occurred")
        }
    }

    override suspend fun logout() {
        Firebase.auth.signOut()
    }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): AuthResult<UserProfile> {
        return try {
            val authResult = Firebase.auth.createUserWithEmailAndPassword(email, password)
            val uid = authResult.user?.uid ?: return AuthResult.Error("User not found")

            val timestamp = com.mcclabs.mook.util.getCurrentTimeMillis()
            val userData = mapOf(
                "email" to email,
                "displayName" to displayName,
                "isMookActive" to true,
                "lastActiveTimestamp" to timestamp,
                "acceptedEula" to true,
                // Google Play requires recording when the user accepted the EULA/UGC policy.
                "acceptedEulaAt" to timestamp
            )

            // users is a shared collection (Mook + WalkTalk). Merge so we never
            // overwrite WalkTalk's fields on a uid that already has a WalkTalk profile.
            appFirestore.collection("users").document(uid).set(userData, merge = true)

            AuthResult.Success(
                data = UserProfile(
                    id = uid,
                    email = email,
                    displayName = displayName,
                    lastActiveTimestamp = timestamp
                )
            )
        } catch (e: FirebaseAuthException) {
            AuthResult.Error(e.message ?: "Authentication failed")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Registration failed")
        }
    }

    override suspend fun hasAcceptedEula(): Boolean {
        val uid = Firebase.auth.currentUser?.uid ?: return false
        return try {
            val document = appFirestore.collection("users").document(uid).get()
            runCatching { document.get<Boolean>("acceptedEula") }.getOrNull() ?: false
        } catch (e: Exception) {
            com.mcclabs.mook.util.Log.e("EULA durumu okunamadı", e)
            false
        }
    }

    override suspend fun acceptEula() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val now = com.mcclabs.mook.util.getCurrentTimeMillis()
        appFirestore.collection("users").document(uid).set(
            mapOf("acceptedEula" to true, "acceptedEulaAt" to now),
            merge = true
        )
    }

    override suspend fun completeProfile(
        userId: String,
        displayName: String,
        bio: String,
        interests: List<String>,
        avatarUri: String?,
        discoveryPhotos: List<String>,
        gender: com.mcclabs.mook.domain.model.Gender?,
        birthDateMillis: Long?,
        languageCode: String?,
        countryCode: String?,
        discoverVisible: Boolean
    ): AuthResult<UserProfile> {
        return try {
            // A photo the user picked must never be dropped silently: registration requires
            // an avatar and at least one discovery photo, so a failed upload is a failed
            // profile rather than a profile that is quietly missing pictures.
            val timestamp = com.mcclabs.mook.util.getCurrentTimeMillis()
            val uploadedAvatarUrl = avatarUri?.let {
                uploadPhoto(userId, it, "avatar_$timestamp.jpg")
                    ?: return AuthResult.Error("Your profile photo could not be uploaded. Please try again.")
            }
            val uploadedDiscoveryPhotos = discoveryPhotos.mapIndexed { index, uri ->
                uploadPhoto(userId, uri, "discovery_${timestamp}_$index.jpg")
                    ?: return AuthResult.Error("One of your discovery photos could not be uploaded. Please try again.")
            }

            val updateData = mutableMapOf<String, Any>(
                "displayName" to displayName,
                "bio" to bio,
                "interests" to interests,
                "discoveryPhotos" to uploadedDiscoveryPhotos,
                "discoverVisible" to discoverVisible
            )
            if (uploadedAvatarUrl != null) {
                updateData["avatarUrl"] = uploadedAvatarUrl
            }
            if (gender != null) {
                updateData["gender"] = gender.name
            }
            if (birthDateMillis != null) {
                updateData["birthDateMillis"] = birthDateMillis
            }
            if (languageCode != null) {
                updateData["languageCode"] = languageCode
            }
            if (countryCode != null) {
                updateData["countryCode"] = countryCode
            }

            appFirestore.collection("users").document(userId).set(updateData, merge = true)

            val document = appFirestore.collection("users").document(userId).get()
            val fetchedEmail = runCatching { document.get<String>("email") }.getOrDefault("")
            val fetchedAvatarUrl = runCatching { document.get<String>("avatarUrl") }.getOrNull()
            val fetchedDiscoveryPhotos = runCatching { document.get<List<String>>("discoveryPhotos") }.getOrDefault(emptyList())

            AuthResult.Success(
                data = UserProfile(
                    id = userId,
                    email = fetchedEmail,
                    displayName = displayName,
                    bio = bio,
                    interests = interests,
                    avatarUrl = fetchedAvatarUrl,
                    discoveryPhotos = fetchedDiscoveryPhotos,
                    gender = gender,
                    birthDateMillis = birthDateMillis,
                    languageCode = languageCode,
                    countryCode = countryCode,
                    discoverVisible = discoverVisible
                )
            )
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Profile update failed")
        }
    }

    override fun currentUserEmail(): String? = Firebase.auth.currentUser?.email

    override suspend fun deleteAccount(): AuthResult<Unit> {
        // Oturum yoksa çağrıya hiç çıkmayız.
        Firebase.auth.currentUser
            ?: return AuthResult.Error("Oturum açmış bir kullanıcı bulunamadı.")

        return try {
            // Ağır silme işlemini sunucu yapar: güvenlik kuralları istemci tarafında
            // toplu silmeyi engellediğinden, Storage + Firestore + RevenueCat + Auth
            // temizliği `deleteAccount` Cloud Function içinde Admin SDK ile yürütülür.
            Firebase.functions.httpsCallable("deleteAccount").invoke()

            // Sunucu Auth kaydını sildi; yerel oturumu da temizleyelim ki uygulama
            // Login'e dönebilsin ve önbellekteki kullanıcı kalmasın.
            runCatching { Firebase.auth.signOut() }

            Log.d("Hesap silindi (Cloud Function)")
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            // Sunucu ayrıntılarını kullanıcıya sızdırmadan logla, genel mesaj döndür.
            Log.e("Hesap silme başarısız", e)
            AuthResult.Error("Hesap silinemedi. Lütfen tekrar deneyin.")
        }
    }
}
