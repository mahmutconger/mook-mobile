import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { resolveTierFor, limitsFor } from "./monetization";
import { discoverPriority, sortByDiscoverPriority } from "./discoverPriority";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

/** `DiscoverRepositoryImpl.kt`deki (istemci) `PAGE_SIZE`/`QUERY_BATCH_SIZE` sabitleriyle
 *  BİREBİR AYNI — bu göç, istemcinin eski sayfalama DAVRANIŞINI korur, yalnızca sorgunun
 *  ÇALIŞTIĞI yeri değiştirir. */
const PAGE_SIZE = 10;
const QUERY_BATCH_SIZE = 20;

/** Bir çağrıda taranacak en fazla aday SAYFASI. Çok kalabalık olmayan/çoğu kaydırılmış bir
 *  odada bile fonksiyonun sınırsız süre çalışmamasını garanti eder — oda gerçekten
 *  tükenmemişse istemci, dönen `nextAfterUid` imleciyle basitçe bir SONRAKİ çağrıyı yapar
 *  (`reachedEnd: false` ile ayırt edilir). */
const MAX_ITERATIONS = 6;

/** İstemcideki `Languages.LANGUAGE_INDEPENDENT_ROOM_CODE` sabitiyle BİREBİR AYNI değer
 *  (bkz. `shared/src/commonMain/kotlin/com/mcclabs/mook/domain/model/Languages.kt`).
 *  KMP tarafı bu sabiti Cloud Functions ile paylaşamadığından İKİSİ DE ELLE senkron
 *  tutulmalıdır. */
const LANGUAGE_INDEPENDENT_ROOM_CODE = "ALL";

interface DiscoverFeedProfile {
    id: string;
    displayName: string;
    /** Sunucuda hesaplanan yaş; doğum tarihi ASLA istemciye gönderilmez. */
    age: number | null;
    countryCode: string | null;
    languageCode: string | null;
    avatarUrl: string | null;
    discoveryPhotos: string[];
    bio: string;
    interests: string[];
    verified: boolean;
    lastActiveTimestamp: number;
    hasLikedMe: boolean;
    /** "FREE" | "ECONOMY" | "STANDARD" | "PREMIUM" — Premium rozeti için. */
    subscriptionTier: string;
}

function requireUid(request: { auth?: { uid?: string } | null }): string {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Authentication required.");
    return uid;
}

/** `public_profiles/{id}` dokümanını istemcinin `DiscoverProfile`sine dönüştürülebilecek
 *  ham alan setine çevirir — istemcideki `mapToProfile()` ile BİREBİR AYNI alanlar, tek
 *  fark kaynağın artık bir `DocumentSnapshot` değil bu callable'ın JSON yanıtı olması. */
function toFeedProfile(doc: FirebaseFirestore.QueryDocumentSnapshot, hasLikedMe: boolean): DiscoverFeedProfile {
    const data = doc.data();
    return {
        id: doc.id,
        displayName: typeof data.displayName === "string" ? data.displayName : "",
        age: typeof data.age === "number" ? data.age : null,
        countryCode: typeof data.countryCode === "string" ? data.countryCode : null,
        languageCode: typeof data.languageCode === "string" ? data.languageCode : null,
        avatarUrl: typeof data.avatarUrl === "string" ? data.avatarUrl : null,
        discoveryPhotos: Array.isArray(data.discoveryPhotos) ? data.discoveryPhotos : [],
        bio: typeof data.bio === "string" ? data.bio : "",
        interests: Array.isArray(data.interests) ? data.interests : [],
        verified: data.verified === true,
        lastActiveTimestamp: typeof data.lastActiveTimestamp === "number" ? data.lastActiveTimestamp : 0,
        hasLikedMe,
        // Premium rozeti için: kademe yalnızca sunucu tarafından yazılır (bkz. revenuecatSync).
        subscriptionTier: typeof data.subscriptionTier === "string" ? data.subscriptionTier : "FREE",
    };
}

/**
 * Server-Driven Discover Feed (Altyapı Gereksinimi): Discover akışının TEK sunucu tarafı
 * giriş noktası. İstemci (`DiscoverRepositoryImpl.kt`) artık `public_profiles`i DOĞRUDAN
 * sorgulamaz — yalnızca bu callable'ı çağırır ve zaten filtrelenmiş/sıralanmış/yetkilendirilmiş
 * bir sayfa alır. Üç iş kuralı, istemcinin asla erişemeyeceği/güvenilemeyeceği bu sunucu
 * tarafında uygulanır:
 *
 * 1) **Free Roam doğrulaması**: `roomLanguageCode`, kullanıcının kendi aktif odasıyla
 *    (`users/{uid}.roomLanguageCode`) AYNI olmadığı sürece (ya da hiç oda seçilmemişse),
 *    yalnızca planı `freeRoam` hakkına sahip (PREMIUM) kullanıcılar için izinlidir — eskiden
 *    istemci bu kısıtlamayı hiç UYGULAMIYORDU (yerel ayarı değiştirip odasının dışına
 *    bedavaya çıkabilirdi).
 * 2) **Boost sıralaması**: `boostUntil > şimdi` olan (aktif Boost'lu) profiller, her aday
 *    sayfası İÇİNDE kararlı (stabil) biçimde öne alınır.
 * 3) **Incognito filtreleme**: `incognito == true` olan hiçbir profil sorgunun WHERE
 *    koşulunda ASLA döndürülmez — artık bunun tek gerçeklik kaynağı burasıdır.
 *
 * Yaş ve ülke filtreleri BİLİNÇLİ olarak burada UYGULANMAZ: yaş, cihazın kendi
 * `calculateAge()`siyle EKRANDA GÖSTERİLEN yaşla tutarlı kalmalıdır; ülke filtresi ise
 * cihazın yerel diline göre çözümlenen ÜLKE ADINA (`getCountryName()`) göre çalışır —
 * ikisi de sunucuda yeniden üretilemeyecek istemciye özgü yerelleştirme mantığıdır. Bu ikisi,
 * `DiscoverRepositoryImpl.matches()` içinde SUNUCUDAN gelen (zaten yetkilendirilmiş) sayfa
 * üzerinde bellek-içi uygulanmaya DEVAM eder — saf bir GÖRÜNTÜLEME tercihidir, bir
 * güvenlik/iş kuralı sınırı değildir, bu yüzden istemci tarafında kalması güvenlidir.
 */
export const getDiscoverFeed = onCall(
    // Gereksinim 2 (Faz 6): `public_profiles` projeksiyonunun TEK kaynağı bu callable'dır
    // -- App Check kapsamlı uygulamasının açıkça hedeflediği koleksiyon budur (bkz. görev
    // tanımı: "Enforce App Check on direct Firestore reads, especially for the
    // `public_profiles` collection").
    { region: "us-central1", enforceAppCheck: true },
    async (request): Promise<{
        profiles: DiscoverFeedProfile[];
        nextAfterUid: string | null;
        reachedEnd: boolean;
    }> => {
        const uid = requireUid(request);
        const data = request.data as { roomLanguageCode?: unknown; afterUid?: unknown };

        if (data.roomLanguageCode !== undefined && data.roomLanguageCode !== null && typeof data.roomLanguageCode !== "string") {
            throw new HttpsError("invalid-argument", "Invalid room language code.");
        }
        if (data.afterUid !== undefined && data.afterUid !== null && typeof data.afterUid !== "string") {
            throw new HttpsError("invalid-argument", "Invalid cursor.");
        }
        const requestedRoom = typeof data.roomLanguageCode === "string" && data.roomLanguageCode.length > 0
            ? data.roomLanguageCode
            : null;
        let cursorId: string | null = typeof data.afterUid === "string" ? data.afterUid : null;

        const ownUserSnapshot = await db.collection("users").doc(uid).get();
        const ownUser = ownUserSnapshot.data() ?? {};
        const ownRoom = typeof ownUser.roomLanguageCode === "string" ? ownUser.roomLanguageCode : null;
        const blockedUsers = new Set(
            Array.isArray(ownUser.blockedUsers)
                ? (ownUser.blockedUsers as unknown[]).filter((entry): entry is string => typeof entry === "string")
                : [],
        );

        // 1) Free Roam doğrulaması. Sunucu henüz kullanıcının bir odasını bilmiyorsa
        // (`ownRoom === null`, ör. onboarding sırasında `RoomGate` tamamlanmadan önceki
        // geçici bir durum) hiçbir şeyi kısıtlamıyoruz — kısıtlanacak bir "kendi odası"
        // henüz YOK; bu yüzden bu dar belirsizlikte GÜVENLİ TARAF açık bırakmaktır.
        const requestedNormalized = requestedRoom?.toLowerCase() ?? null;
        const ownNormalized = ownRoom?.toLowerCase() ?? null;
        const isOwnRoomRequest = ownNormalized === null || requestedNormalized === ownNormalized;
        if (!isOwnRoomRequest) {
            const tier = await resolveTierFor(uid, request);
            const limits = await limitsFor(tier);
            if (!limits.freeRoam) throw new HttpsError("permission-denied", "upgrade-required");
        }

        const profiles: DiscoverFeedProfile[] = [];
        let reachedEnd = false;
        let iterations = 0;

        while (profiles.length < PAGE_SIZE && !reachedEnd && iterations < MAX_ITERATIONS) {
            iterations += 1;

            let query: FirebaseFirestore.Query = db.collection("public_profiles")
                .where("isMookActive", "==", true)
                .where("incognito", "==", false)
                .orderBy("lastActiveTimestamp", "desc")
                .limit(QUERY_BATCH_SIZE);

            if (cursorId) {
                const cursorSnapshot = await db.collection("public_profiles").doc(cursorId).get();
                if (cursorSnapshot.exists) query = query.startAfter(cursorSnapshot);
            }

            const snapshot = await query.get();
            if (snapshot.empty) {
                reachedEnd = true;
                break;
            }
            cursorId = snapshot.docs[snapshot.docs.length - 1].id;
            if (snapshot.docs.length < QUERY_BATCH_SIZE) reachedEnd = true;

            // Kendisi, engellenenler ve ODA filtresi — bu üçü locale'den BAĞIMSIZDIR (saf
            // kimlik/metin karşılaştırması), bu yüzden yaş/ülke filtresinin AKSİNE burada
            // güvenle uygulanabilir. Oda filtresi, `null`/"ALL"ı "herkesi göster" sayan
            // istemcideki `matches()` mantığıyla BİREBİR AYNIDIR.
            const roomFilterActive = requestedRoom !== null
                && requestedRoom.toLowerCase() !== LANGUAGE_INDEPENDENT_ROOM_CODE.toLowerCase();
            const candidates = snapshot.docs.filter((doc) => {
                if (doc.id === uid || blockedUsers.has(doc.id)) return false;
                if (roomFilterActive) {
                    const profileLanguage = doc.data().languageCode;
                    if (typeof profileLanguage !== "string" || profileLanguage.toLowerCase() !== requestedRoom!.toLowerCase()) {
                        return false;
                    }
                }
                return true;
            });
            if (candidates.length === 0) continue;

            // Zaten kaydırılmış (like/pass) olanları ELE: `interactions/{uid}_{candidateId}`
            // belge VARLIĞINI toplu (batched, `getAll`) okuma ile kontrol et. İstemcinin
            // eskiden yaptığı gibi TÜM etkileşim geçmişini önceden çekmek yerine yalnızca
            // BU sayfanın adaylarını sorar — çok daha ucuz ve durumsuz (stateless).
            const interactionRefs = candidates.map((doc) => db.collection("interactions").doc(`${uid}_${doc.id}`));
            const interactionSnapshots = await db.getAll(...interactionRefs);
            const notYetActedOn = candidates.filter((_doc, index) => !interactionSnapshots[index].exists);
            if (notYetActedOn.length === 0) continue;

            // "Beni beğendi mi?" — TERS yöndeki etkileşimi (`{candidateId}_{uid}`) topluca oku.
            const reverseRefs = notYetActedOn.map((doc) => db.collection("interactions").doc(`${doc.id}_${uid}`));
            const reverseSnapshots = await db.getAll(...reverseRefs);

            // 2) Öncelik sıralaması (bkz. discoverPriority.ts): Boost → Premium → Standart →
            // diğerleri. Sıralama sayfa İÇİNDE ve kararlıdır; grup içi sıra (son aktiflik) korunur.
            const now = Date.now();
            const withMetadata = sortByDiscoverPriority(
                notYetActedOn.map((doc, index) => ({
                    doc,
                    priority: discoverPriority(
                        { boostUntil: doc.data().boostUntil, subscriptionTier: doc.data().subscriptionTier },
                        now,
                    ),
                    hasLikedMe: reverseSnapshots[index].exists && reverseSnapshots[index].data()?.type === "like",
                })),
                (entry) => entry.priority,
            );

            for (const entry of withMetadata) {
                if (profiles.length >= PAGE_SIZE) break;
                profiles.push(toFeedProfile(entry.doc, entry.hasLikedMe));
            }
        }

        return {
            profiles,
            nextAfterUid: reachedEnd ? null : cursorId,
            reachedEnd,
        };
    },
);
