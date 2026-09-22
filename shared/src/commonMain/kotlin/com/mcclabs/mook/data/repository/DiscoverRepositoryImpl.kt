package com.mcclabs.mook.data.repository

import com.mcclabs.mook.domain.model.Country
import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.domain.model.LikedProfile
import com.mcclabs.mook.domain.model.MatchSettings
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.util.calculateAge
import com.mcclabs.mook.util.getCountryName
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.mcclabs.mook.data.appFirestore
import dev.gitlive.firebase.firestore.where
import dev.gitlive.firebase.functions.functions
import com.mcclabs.mook.util.Log
import kotlinx.serialization.Serializable

/** How many matching profiles one [DiscoverRepositoryImpl.getDiscoverProfiles] call aims to return. */
private const val PAGE_SIZE = 10

/** How many `users` documents each underlying Firestore query reads before filtering. */
private const val QUERY_BATCH_SIZE = 20

class DiscoverRepositoryImpl : DiscoverRepository {

    private fun mapToProfile(document: dev.gitlive.firebase.firestore.DocumentSnapshot): DiscoverProfile {
        val id = document.id
        val name = runCatching { document.get<String>("displayName") }.getOrNull() ?: "Unknown"

        // Age is derived rather than stored, so it stays correct as the user gets older.
        val birthDateMillis = runCatching { document.get<Long>("birthDateMillis") }.getOrNull()
        val age = calculateAge(birthDateMillis)

        val countryCode = runCatching { document.get<String>("countryCode") }.getOrNull()
        val country = countryCode?.takeIf { it.isNotBlank() }?.let { code ->
            Country(code = code, name = getCountryName(code) ?: code)
        }

        val language = Languages.fromCode(runCatching { document.get<String>("languageCode") }.getOrNull())

        val avatarUrl = runCatching { document.get<String>("avatarUrl") }.getOrNull()
        val discoveryPhotos = runCatching { document.get<List<String>>("discoveryPhotos") }.getOrNull() ?: emptyList()
        // Only remote URLs are shareable: a local content:// or file:// URI belongs to the
        // uploader's device and throws a SecurityException for anyone else. Stale documents
        // written before uploads existed can still hold such URIs, so drop them here.
        val photoUrls = buildList {
            if (!avatarUrl.isNullOrBlank()) add(avatarUrl)
            addAll(discoveryPhotos.filter { it.isNotBlank() })
        }.filter { it.startsWith("http://") || it.startsWith("https://") }

        val bio = runCatching { document.get<String>("bio") }.getOrNull() ?: ""
        val interests = runCatching { document.get<List<String>>("interests") }.getOrNull() ?: emptyList()
        val verified = runCatching { document.get<Boolean>("verified") }.getOrNull() ?: false

        // Written as epoch millis (see UserProfile.lastActiveTimestamp). Absent on documents
        // created before the field existed, which simply read as "not online".
        val lastActiveMillis = runCatching { document.get<Long>("lastActiveTimestamp") }
            .getOrNull()
            ?.takeIf { it > 0L }

        return DiscoverProfile(
            id = id,
            name = name,
            age = age,
            country = country,
            language = language,
            photoUrls = photoUrls,
            bio = bio,
            interests = interests,
            verified = verified,
            lastActiveMillis = lastActiveMillis
        )
    }

    private var lastVisibleDocument: dev.gitlive.firebase.firestore.DocumentSnapshot? = null
    private var swipedUserIds = mutableSetOf<String>()
    private var isInteractionsFetched = false
    private var lastSettings: MatchSettings? = null

    /**
     * Set once a `users` page comes back short, meaning the collection is exhausted for the
     * current filters. Cleared whenever paging restarts (new filters, or [resetDiscoverPaging]).
     */
    private var reachedEnd = false

    override fun hasMoreProfiles(): Boolean = !reachedEnd

    override fun markActedOn(profileId: String) {
        swipedUserIds.add(profileId)
    }

    override fun unmarkActedOn(profileId: String) {
        swipedUserIds.remove(profileId)
    }

    override fun actedOnProfileIds(): Set<String> = swipedUserIds.toSet()

    override fun resetDiscoverPaging() {
        lastVisibleDocument = null
        lastSettings = null
        reachedEnd = false
        // Rebuild the exclusion set too: likes and blocks made elsewhere (another device, the
        // profile screen) must be reflected, otherwise refreshing resurfaces people already
        // acted on.
        isInteractionsFetched = false
        swipedUserIds = mutableSetOf()
    }

    override suspend fun getDiscoverProfiles(settings: MatchSettings): List<DiscoverProfile> {
        val currentUser = Firebase.auth.currentUser
        val currentUid = currentUser?.uid ?: return emptyList()
        val db = appFirestore

        // A stale/corrupted roomLanguageCode equal to the viewer's own native language
        // would otherwise hide everyone; treat the room filter as absent in that case.
        val ownLanguageCode = runCatching {
            db.collection("users").document(currentUid).get().get<String>("languageCode")
        }.getOrNull()
        val effectiveSettings = if (
            settings.roomLanguageCode != null &&
            settings.roomLanguageCode.equals(ownLanguageCode, ignoreCase = true)
        ) {
            settings.copy(roomLanguageCode = null)
        } else {
            settings
        }

        // New filters mean a different result set, so paging must start from the top
        // instead of continuing after the last document of the previous filter.
        if (effectiveSettings != lastSettings) {
            lastVisibleDocument = null
            lastSettings = effectiveSettings
            reachedEnd = false
        }

        // Nothing left for these filters — don't spend a read proving it again on every
        // scroll to the bottom.
        if (reachedEnd) return emptyList()

        Log.d("Discover sorgusu başlıyor (uid=$currentUid, yaş=${settings.ageRangeStart}-${settings.ageRangeEnd})")

        // Fetch user's past interactions once to filter them out locally
        if (!isInteractionsFetched) {
            val interactionsSnapshot = db.collection("interactions")
                .where { "fromUserId" equalTo currentUid }
                .get()

            for (doc in interactionsSnapshot.documents) {
                doc.get<String>("toUserId").let { swipedUserIds.add(it) }
            }
            
            // Fetch blocked users to exclude them from Discovery
            try {
                val userDoc = db.collection("users").document(currentUid).get()
                val blockedUsers = userDoc.get<List<String>>("blockedUsers")
                swipedUserIds.addAll(blockedUsers)
            } catch (e: Exception) {
                // Ignore if blockedUsers doesn't exist
            }

            isInteractionsFetched = true
            Log.d("Daha önce kaydırılan: ${swipedUserIds.size} kullanıcı")
        }

        val profiles = mutableListOf<DiscoverProfile>()

        // Recursive or loop fetching to ensure we get a batch of valid (unswiped) users
        while (profiles.size < PAGE_SIZE && !reachedEnd) {
            // Discover never reads private account documents. The server-maintained
            // projection contains only fields that can be exposed to another member.
            var query = db.collection("public_profiles")
                .where { "isMookActive" equalTo true }
                .where { "incognito" equalTo false }
                .orderBy("lastActiveTimestamp", dev.gitlive.firebase.firestore.Direction.DESCENDING)
                .limit(QUERY_BATCH_SIZE)

            lastVisibleDocument?.let {
                query = query.startAfter(it)
            }

            val querySnapshot = query.get()
            val documents = querySnapshot.documents

            Log.d("public_profiles sorgusu döndü: ${documents.size} doküman (isMookActive==true)")
            if (documents.isEmpty()) {
                reachedEnd = true
                break
            }

            lastVisibleDocument = documents.last()

            // A short page means this was the last one; remember it so the grid can stop
            // paging after these results are consumed.
            if (documents.size < QUERY_BATCH_SIZE) reachedEnd = true

            for (document in documents) {
                if (document.id == currentUid || swipedUserIds.contains(document.id)) continue
                try {
                    val profile = mapToProfile(document)
                    if (profile.matches(effectiveSettings)) {
                        profiles.add(profile)
                    } else {
                        Log.d("Discover: ${document.id} filtrelere takıldı (yaş=${profile.age}, ülke=${profile.country?.name}, dil=${profile.language?.name})")
                    }
                } catch (e: Exception) {
                    Log.e("Discover: ${document.id} profili okunamadı, atlandı", e)
                }
            }
        }

        Log.d("Discover sonuç: ${profiles.size} profil gösterilecek")
        
        val profilesWithLikeInfo = profiles.map { profile ->
            val interactionDocId = "${profile.id}_$currentUid"
            try {
                val doc = db.collection("interactions").document(interactionDocId).get()
                val hasLikedMe = doc.exists && runCatching { doc.get<String>("type") }.getOrNull() == "like"
                profile.copy(hasLikedMe = hasLikedMe)
            } catch (e: Exception) {
                profile
            }
        }

        return profilesWithLikeInfo
    }

    /**
     * Applies the age filter in memory.
     *
     * Firestore cannot serve this: a range filter on `birthDateMillis` would have to be
     * the first `orderBy`, which conflicts with the `lastActiveTimestamp` ordering this
     * query pages through. Profiles with no birth date are kept rather than hidden.
     */
    private fun DiscoverProfile.matches(settings: MatchSettings): Boolean {
        if (age != null && (age < settings.ageRangeStart || age > settings.ageRangeEnd)) {
            return false
        }
        
        if (settings.targetCountries.isNotEmpty()) {
            val hasCountry = settings.targetCountries.any { country?.name?.contains(it, ignoreCase = true) == true }
            if (!hasCountry) return false
        }
        
        // The language-independent room shows everyone; only a concrete room code filters
        // down to speakers of that language.
        if (settings.roomLanguageCode != null &&
            !settings.roomLanguageCode.equals(Languages.LANGUAGE_INDEPENDENT_ROOM_CODE, ignoreCase = true)
        ) {
            if (!language?.code.equals(settings.roomLanguageCode, ignoreCase = true)) return false
        }

        return true
    }

    override suspend fun getSwipesUsedToday(): Int {
        return try {
            // Uses the server's time-zone calculation. Reading interactions here would make
            // the client-side display disagree with the authoritative quota transaction.
            Firebase.functions.httpsCallable("getUsage").invoke().data<UsageResponse>().likes
        } catch (e: Exception) {
            Log.e("Günlük kaydırma sayısı okunamadı", e)
            0
        }
    }

    override suspend fun getProfileDetails(profileId: String): DiscoverProfile? {
        return try {
            val currentUid = Firebase.auth.currentUser?.uid
            // The account owner still reads their private document; every other profile
            // is resolved through the sanitised projection.
            val collection = if (profileId == currentUid) "users" else "public_profiles"
            val document = appFirestore.collection(collection).document(profileId).get()
            if (document.exists) {
                val profile = mapToProfile(document)
                Log.d("Profil yüklendi: $profileId (fotoğraf=${profile.photoUrls.size}, yaş=${profile.age}, ülke=${profile.country?.code}, dil=${profile.language?.code})")
                profile
            } else {
                // Not the same as a failure: the document genuinely is not there.
                Log.d("Profil bulunamadı: users/$profileId dokümanı yok")
                null
            }
        } catch (e: Exception) {
            Log.e("Profil okunamadı: users/$profileId", e)
            null
        }
    }

    override suspend fun getLikedProfiles(): List<LikedProfile> {
        val currentUid = Firebase.auth.currentUser?.uid ?: return emptyList()
        val db = appFirestore

        // My likes. The query filters on fromUserId only (single equality — no composite
        // index); "like" vs "pass" is separated in memory. Newest first by timestamp.
        val likeSnapshot = db.collection("interactions")
            .where { "fromUserId" equalTo currentUid }
            .get()
        // Blocked users must be excluded from the connections list too, not just Discover.
        val blockedUsers = runCatching {
            db.collection("users").document(currentUid).get()
                .get<List<String>>("blockedUsers")
        }.getOrNull()?.toSet() ?: emptySet()

        val likedIds = likeSnapshot.documents
            .mapNotNull { doc ->
                val type = runCatching { doc.get<String>("type") }.getOrNull()
                val to = runCatching { doc.get<String>("toUserId") }.getOrNull()
                val ts = runCatching { doc.get<Long>("timestamp") }.getOrNull() ?: 0L
                if (type == "like" && to != null) to to ts else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .filter { it !in blockedUsers }
        Log.d("Beğendiklerim: ${likedIds.size} kişi (engellenenler hariç)")

        // Mutual matches. array-contains returns only existing docs I'm part of, so there
        // is no non-existent-document read to trip the rules on.
        val matchedUids = runCatching {
            db.collection("matches")
                .where { "users" contains currentUid }
                .get()
                .documents
                .flatMap { runCatching { it.get<List<String>>("users") }.getOrNull() ?: emptyList() }
                .filter { it != currentUid }
                .toSet()
        }.getOrElse {
            Log.e("Eşleşmeler okunamadı", it)
            emptySet()
        }
        Log.d("Beğendiklerim: ${matchedUids.size} karşılıklı eşleşme")

        // Load each liked person's profile, badging the mutual ones.
        val result = likedIds.mapNotNull { likedId ->
            getProfileDetails(likedId)?.let { profile ->
                LikedProfile(profile = profile, isMatch = likedId in matchedUids)
            }
        }
        Log.d("Beğendiklerim sonuç: ${result.size} profil yüklendi")
        return result
    }
}

@Serializable
private data class UsageResponse(val likes: Int = 0)
