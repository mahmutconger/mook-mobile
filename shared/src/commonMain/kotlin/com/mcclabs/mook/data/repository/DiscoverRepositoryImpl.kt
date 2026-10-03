package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.appHttpsCallable
import com.mcclabs.mook.domain.model.Country
import com.mcclabs.mook.domain.model.DiscoverProfile
import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.domain.model.LikedProfile
import com.mcclabs.mook.domain.model.MatchSettings
import com.mcclabs.mook.domain.repository.DiscoverRepository
import com.mcclabs.mook.domain.repository.LikeUsage
import com.mcclabs.mook.util.calculateAge
import com.mcclabs.mook.util.getCountryName
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.mcclabs.mook.data.appFirestore
import dev.gitlive.firebase.firestore.where
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

        // Profil Gizlilik Kuralı: başkalarının profilinde doğum tarihi YOKTUR; sunucunun
        // hesapladığı `age` okunur. Kendi profilim (`users` belgesi) doğum tarihini taşır.
        val age = runCatching { document.get<Int?>("age") }.getOrNull()
            ?: calculateAge(runCatching { document.get<Long?>("birthDateMillis") }.getOrNull())

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
            lastActiveMillis = lastActiveMillis,
            // Premium rozeti: `public_profiles.subscriptionTier` yalnızca sunucu tarafından yazılır.
            isPremium = runCatching { document.get<String?>("subscriptionTier") }.getOrNull() == "PREMIUM"
        )
    }

    /**
     * Altyapı Gereksinimi (Server-Driven Discover Feed): [DiscoverFeedProfileDto] (sunucudan,
     * `getDiscoverFeed` callable'ından gelen HAM veri) -> [DiscoverProfile] (istemcinin
     * gösterdiği türetilmiş model). [mapToProfile]'daki (hâlâ [getProfileDetails] tarafından
     * kullanılan) türetme mantığıyla BİREBİR AYNIDIR — tek fark kaynağın artık bir Firestore
     * `DocumentSnapshot` değil, sunucunun JSON yanıtı olmasıdır.
     */
    private fun mapFeedProfile(dto: DiscoverFeedProfileDto): DiscoverProfile {
        // Sunucu yalnızca yaşı gönderir (doğum tarihi herkese açık değildir).
        val age = dto.age
        val country = dto.countryCode?.takeIf { it.isNotBlank() }?.let { code ->
            Country(code = code, name = getCountryName(code) ?: code)
        }
        val language = Languages.fromCode(dto.languageCode)
        val photoUrls = buildList {
            if (!dto.avatarUrl.isNullOrBlank()) add(dto.avatarUrl)
            addAll(dto.discoveryPhotos.filter { it.isNotBlank() })
        }.filter { it.startsWith("http://") || it.startsWith("https://") }

        return DiscoverProfile(
            id = dto.id,
            name = dto.displayName.ifBlank { "Unknown" },
            age = age,
            country = country,
            language = language,
            photoUrls = photoUrls,
            bio = dto.bio,
            interests = dto.interests,
            verified = dto.verified,
            hasLikedMe = dto.hasLikedMe,
            isPremium = dto.subscriptionTier == "PREMIUM",
            lastActiveMillis = dto.lastActiveTimestamp.takeIf { it > 0L },
        )
    }

    /** `getDiscoverFeed` callable'ının döndürdüğü sonraki sayfa imleci — artık bir Firestore
     *  `DocumentSnapshot` DEĞİL, yalnızca son profilin kimliği (sunucu, imleci kendi
     *  sorgusunda YENİDEN çözümler). `null` ilk sayfa anlamına gelir. */
    private var nextCursor: String? = null

    /**
     * Set once the server reports the room is exhausted for the current filters. Cleared
     * whenever paging restarts (new filters, or [resetDiscoverPaging]).
     */
    private var reachedEnd = false

    /**
     * Bu OTURUMDA (uygulamayı yeniden başlatana/[resetDiscoverPaging] çağrılana kadar)
     * kullanıcının üzerinde işlem yaptığı (beğendi/geçti/engelledi/şikayet etti) profil
     * kimlikleri. Sunucu artık `interactions` koleksiyonu üzerinden KALICI dışlamayı kendisi
     * yapıyor (bkz. `getDiscoverFeed`); bu küme yalnızca YARIŞ KOŞULUNA karşı bir güvenlik
     * ağıdır — az önce işlem yapılan biri, sunucudaki yazım henüz tamamlanmadan gelen bir
     * sonraki sayfada YİNE görünürse burada elenir.
     */
    private var sessionActedOnIds = mutableSetOf<String>()

    override fun hasMoreProfiles(): Boolean = !reachedEnd

    override fun markActedOn(profileId: String) {
        sessionActedOnIds.add(profileId)
    }

    override fun unmarkActedOn(profileId: String) {
        sessionActedOnIds.remove(profileId)
    }

    override fun actedOnProfileIds(): Set<String> = sessionActedOnIds.toSet()

    override fun resetDiscoverPaging() {
        nextCursor = null
        reachedEnd = false
        // Rebuild the exclusion set too: likes and blocks made elsewhere (another device, the
        // profile screen) must be reflected. The server re-derives its own exclusions from
        // `interactions`/`blockedUsers` on every call, so nothing needs to be re-fetched here.
        sessionActedOnIds = mutableSetOf()
    }

    /**
     * Altyapı Gereksinimi (Server-Driven Discover Feed): istemci artık `public_profiles`i
     * DOĞRUDAN sorgulamaz. Free Roam doğrulaması, Boost sıralaması ve Incognito filtrelemesi
     * dahil TÜM erişim/sıralama mantığı `getDiscoverFeed` Cloud Function'ında yaşar (bkz. o
     * dosyanın KDoc'u); burada yalnızca YAŞ ve ÜLKE filtreleri kalır — ikisi de cihaza özgü
     * yerelleştirme (`calculateAge`in cihaz saat dilimi, `getCountryName`in cihaz dili)
     * gerektirdiğinden sunucuda YENİDEN ÜRETİLEMEZ ve saf birer GÖRÜNTÜLEME tercihidir, bir
     * güvenlik/iş kuralı sınırı değildir.
     */
    override suspend fun getDiscoverProfiles(settings: MatchSettings): List<DiscoverProfile> {
        val currentUid = Firebase.auth.currentUser?.uid ?: return emptyList()
        if (reachedEnd) return emptyList()

        // A stale/corrupted roomLanguageCode equal to the viewer's own native language would
        // otherwise hide everyone; treat the room filter as absent in that case.
        val ownLanguageCode = runCatching {
            appFirestore.collection("users").document(currentUid).get().get<String>("languageCode")
        }.getOrNull()
        val effectiveSettings = if (
            settings.roomLanguageCode != null &&
            settings.roomLanguageCode.equals(ownLanguageCode, ignoreCase = true)
        ) {
            settings.copy(roomLanguageCode = null)
        } else {
            settings
        }

        Log.d("Discover akışı isteniyor (uid=$currentUid, oda=${effectiveSettings.roomLanguageCode}, imleç=$nextCursor)")

        val response = appHttpsCallable("getDiscoverFeed")
            .invoke(DiscoverFeedRequest(roomLanguageCode = effectiveSettings.roomLanguageCode, afterUid = nextCursor))
            .data<DiscoverFeedResponse>()

        nextCursor = response.nextAfterUid
        reachedEnd = response.reachedEnd

        val profiles = response.profiles
            .asSequence()
            .filter { it.id !in sessionActedOnIds }
            .map { mapFeedProfile(it) }
            .filter { profile ->
                val ok = profile.matches(effectiveSettings)
                if (!ok) {
                    Log.d("Discover: ${profile.id} filtrelere takıldı (yaş=${profile.age}, ülke=${profile.country?.name}, dil=${profile.language?.name})")
                }
                ok
            }
            .toList()

        Log.d("Discover sonuç: ${profiles.size} profil gösterilecek (sunucu taraflı akış)")
        return profiles
    }

    /**
     * Applies the age and country filters in memory — the two filters that only make sense
     * with the viewer's own locale (see the KDoc on [getDiscoverProfiles]). Room/incognito
     * exclusion already happened server-side in `getDiscoverFeed`.
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
        // down to speakers of that language. (The server already applies this same room
        // filter when building the page; re-checking it here is a harmless no-op for the
        // normal case and a safety net if a future server change ever loosens it.)
        if (settings.roomLanguageCode != null &&
            !settings.roomLanguageCode.equals(Languages.LANGUAGE_INDEPENDENT_ROOM_CODE, ignoreCase = true)
        ) {
            if (!language?.code.equals(settings.roomLanguageCode, ignoreCase = true)) return false
        }

        return true
    }

    override suspend fun getLikeUsageToday(): LikeUsage {
        return try {
            // Uses the server's time-zone calculation. Reading interactions here would make
            // the client-side display disagree with the authoritative quota transaction.
            val usage = appHttpsCallable("getUsage").invoke().data<UsageResponse>()
            LikeUsage(likes = usage.likes, rewardedLikes = usage.rewardedLikes, likesEver = usage.likesEver)
        } catch (e: Exception) {
            Log.e("Günlük kaydırma sayısı okunamadı", e)
            LikeUsage()
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
private data class UsageResponse(val likes: Int = 0, val rewardedLikes: Int = 0, val likesEver: Int = 0)

/** `getDiscoverFeed` callable'ına gönderilen istek gövdesi — sunucudaki (`discoverFeed.ts`)
 *  `roomLanguageCode`/`afterUid` alanlarıyla BİREBİR AYNI. */
@Serializable
private data class DiscoverFeedRequest(
    val roomLanguageCode: String? = null,
    val afterUid: String? = null,
)

/** `getDiscoverFeed`in döndürdüğü HER BİR profilin ham alan seti — `public_profiles`
 *  dokümanının aynısı, artı sunucunun hesapladığı `hasLikedMe`. */
@Serializable
private data class DiscoverFeedProfileDto(
    val id: String,
    val displayName: String = "",
    /** Sunucuda hesaplanan yaş (bkz. functions/src/profilePrivacy.ts). */
    val age: Int? = null,
    val countryCode: String? = null,
    val languageCode: String? = null,
    val avatarUrl: String? = null,
    val discoveryPhotos: List<String> = emptyList(),
    val bio: String = "",
    val interests: List<String> = emptyList(),
    val verified: Boolean = false,
    val lastActiveTimestamp: Long = 0,
    val hasLikedMe: Boolean = false,
    /** Sunucunun yazdığı abonelik kademesi (bkz. functions/src/discoverFeed.ts). */
    val subscriptionTier: String = "FREE",
)

/** `getDiscoverFeed`in tam yanıtı — bir sayfa profil, sonraki sayfa imleci ve oda bitti mi
 *  bayrağı. */
@Serializable
private data class DiscoverFeedResponse(
    val profiles: List<DiscoverFeedProfileDto> = emptyList(),
    val nextAfterUid: String? = null,
    val reachedEnd: Boolean = false,
)
