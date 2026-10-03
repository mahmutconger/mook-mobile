import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { dayKey } from "./monetization";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

/** Gereksinim (Altyapı): `users/{uid}`de saat dilimi eksikse uygulanacak güvenli varsayılan
 *  — `userTimeZone()`/`dayKey()`'in halihazırda kullandığı varsayılanla BİREBİR aynı
 *  (bkz. `monetization.ts` -> `userTimeZone`), böylece bu göç sonrası davranış DEĞİŞMEZ,
 *  yalnızca örtük varsayılan artık belgede AÇIKÇA yazılı olur. */
const DEFAULT_TIME_ZONE = "Europe/Istanbul";

/** Tek bir çağrıda taranacak kullanıcı sayısı — `bootstrapMonetization`daki sayfa boyutuyla
 *  BİREBİR aynı (Firestore batch limiti 500'ün altında güvenli bir tampon). */
const PAGE_SIZE = 250;

interface MigrationResult {
    scanned: number;
    publicProfilesWritten: number;
    timeZonesDefaulted: number;
    usageBackfilled: number;
    nextAfterUid: string | null;
}

/** `users/{uid}` dokümanından, Discover'ın dışarı açmasına izin verilen alanları çıkarır.
 *  `syncPublicProfile` (canlı tetikleyici) ve `bootstrapMonetization` ile BİREBİR AYNI alan
 *  seti — üç yerde de sürüklenme (drift) olmaması için tek bir gerçeklik kaynağı gibi
 *  davranmalıdır. */
function toPublicProfile(data: FirebaseFirestore.DocumentData) {
    return {
        displayName: data.displayName ?? "",
        birthDateMillis: data.birthDateMillis ?? null,
        countryCode: data.countryCode ?? null,
        languageCode: data.languageCode ?? null,
        avatarUrl: data.avatarUrl ?? null,
        discoveryPhotos: Array.isArray(data.discoveryPhotos) ? data.discoveryPhotos : [],
        bio: data.bio ?? "",
        interests: Array.isArray(data.interests) ? data.interests : [],
        verified: data.verified === true,
        lastActiveTimestamp: data.lastActiveTimestamp ?? 0,
        isMookActive: data.isMookActive === true,
        discoverVisible: data.discoverVisible !== false,
        incognito: data.incognito === true,
        visibleTo: Array.isArray(data.visibleTo) ? data.visibleTo : [],
        boostUntil: typeof data.boostUntil === "number" ? data.boostUntil : 0,
    };
}

/** `data.timeZone` geçerliyse onu, değilse [DEFAULT_TIME_ZONE]'u döner — `userTimeZone()`
 *  ile BİREBİR aynı doğrulama kuralı (tip + uzunluk). */
function resolveTimeZone(data: FirebaseFirestore.DocumentData): string {
    const zone = data.timeZone;
    return typeof zone === "string" && zone.length > 0 && zone.length <= 64 ? zone : DEFAULT_TIME_ZONE;
}

/**
 * Tek seferlik, devam ettirilebilir (resumable) yönetici göç işlemi. `bootstrapMonetization`
 * ile AYNI güvenlik modelini (yalnızca `admin: true` özel talebi taşıyan çağıran) ve sayfalama
 * desenini (`afterUid` imleci, `PAGE_SIZE` sınırlı sayfalar) izler; deploy sırasında/sonrasında
 * birden fazla kez ve KESİNTİDEN SONRA KALDIĞI YERDEN çağrılabilir.
 *
 * Üç bağımsız göç adımı uygular:
 *
 * 1) **Genel profil projeksiyonu**: her `users/{uid}` belgesinden `public_profiles/{uid}`'e
 *    Discover'ın dışarı açtığı alanları kopyalar — `syncPublicProfile` canlı tetikleyicisi
 *    yalnızca BUNDAN SONRAKİ yazımları yakalar; bu adım GEÇMİŞTEKİ hesapları kapsar.
 *
 * 2) **Saat dilimi varsayılanı**: `timeZone` alanı eksik/geçersiz olan kullanıcılara
 *    [DEFAULT_TIME_ZONE] YAZAR (yalnızca `userTimeZone()`'un zaten ÖRTÜK olarak uyguladığı
 *    varsayılanı belgeye AÇIKÇA işler — çalışma zamanı davranışını DEĞİŞTİRMEZ).
 *
 * 3) **Bugünün etkileşim sayısının `usage/{uid}`'e taşınması**: `usage/{uid}.day` bugünün
 *    anahtarıyla (kullanıcının çözümlenen saat dilimine göre) eşleşmiyorsa, `interactions`
 *    koleksiyonundaki (değişmez denetim izi) bugüne ait `like` sayısını hesaplar ve
 *    `usage/{uid}`e yazar. Bu adım OLMADAN, deploy günü `usage` dokümanı henüz "bugün"e
 *    işaretlenmemiş bir kullanıcı, `resetUsage()`'ın tembel sıfırlama mantığı yüzünden
 *    GERÇEKTE bugün zaten tükettiği hakları YENİDEN kazanmış gibi görünebilir (günlük kota
 *    sıfırlanması). Yazım YALNIZCA `merge: true` ile ve MEVCUT sayıyı asla AZALTMAYACAK
 *    şekilde (`Math.max`) yapılır — bu adım birden çok kez çalıştırılsa bile GÜVENLİDİR.
 *
 * Üçü de birbirinden BAĞIMSIZDIR: biri başarısız olsa diğerleri etkilenmez, ve tekrar
 * çağrıldığında zaten tamamlanmış kullanıcılar için YALNIZCA gerekli olmayan yazımları atlar
 * (idempotent).
 */
export const migrateExistingUsers = onCall(
    // Gereksinim 2 (Faz 6): Kapsamlı App Check Uygulaması kapsamının KASITLI istisnası --
    // bu, mobil istemci tarafından ÇAĞRILMAZ; bir mühendisin tek seferlik bir geri
    // dönüşüm (backfill) operasyonu için Admin SDK ayrıcalığıyla (kendi kimlik doğrulanmış
    // oturumuyla, App Check jetonu OLMADAN) tetiklediği bir bakım betiğidir. `enforceAppCheck: true`
    // yapmak bu meşru operasyonel kullanımı KİLİTLERDİ.
    { region: "us-central1", enforceAppCheck: false },
    async (request): Promise<MigrationResult> => {
        if (!request.auth?.uid) throw new HttpsError("unauthenticated", "Authentication required.");
        if (request.auth.token?.admin !== true) {
            throw new HttpsError("permission-denied", "administrator-required");
        }

        const afterUid = (request.data as { afterUid?: unknown }).afterUid;
        if (afterUid !== undefined && typeof afterUid !== "string") {
            throw new HttpsError("invalid-argument", "Invalid cursor.");
        }

        let query = db.collection("users")
            .orderBy(admin.firestore.FieldPath.documentId())
            .limit(PAGE_SIZE);
        if (typeof afterUid === "string" && afterUid) query = query.startAfter(afterUid);
        const users = await query.get();

        // Adım 1 + 2: tek bir batch'te toplu yaz (Firestore batch limiti 500; burada en fazla
        // PAGE_SIZE * 2 = 500 yazım olabilir, güvenli sınırda).
        const batch = db.batch();
        let publicProfilesWritten = 0;
        let timeZonesDefaulted = 0;
        const resolvedZones = new Map<string, string>();

        for (const document of users.docs) {
            const data = document.data();
            batch.set(db.collection("public_profiles").doc(document.id), toPublicProfile(data), { merge: true });
            publicProfilesWritten += 1;

            const hasValidTimeZone = typeof data.timeZone === "string"
                && data.timeZone.length > 0 && data.timeZone.length <= 64;
            if (!hasValidTimeZone) {
                batch.set(document.ref, { timeZone: DEFAULT_TIME_ZONE }, { merge: true });
                timeZonesDefaulted += 1;
            }
            resolvedZones.set(document.id, resolveTimeZone(data));
        }
        await batch.commit();

        // Adım 3: `usage/{uid}` her kullanıcı için AYRI ele alınır (transaction gerektirmez —
        // yalnızca MEVCUT sayıyı YÜKSELTİR, hiçbir zaman düşürmez, bu yüzden yarış koşulu
        // en kötü ihtimalle bir sonraki `swipe`/`sendMessage` çağrısının kazandığı sayıyı
        // ez­meyecek şekilde zararsızdır).
        let usageBackfilled = 0;
        for (const document of users.docs) {
            const uid = document.id;
            const zone = resolvedZones.get(uid) ?? DEFAULT_TIME_ZONE;
            const today = dayKey(zone);

            const usageRef = db.collection("usage").doc(uid);
            const usageSnapshot = await usageRef.get();
            const usage = usageSnapshot.data() ?? {};
            if (usage.day === today) continue; // Canlı sistem bugünü zaten doğru işlemiş.

            const todaysLikes = await db.collection("interactions")
                .where("fromUserId", "==", uid)
                .where("day", "==", today)
                .where("type", "==", "like")
                .get();

            await usageRef.set({
                day: today,
                likes: Math.max(todaysLikes.size, Number(usage.likes ?? 0)),
            }, { merge: true });
            usageBackfilled += 1;
        }

        const last = users.docs.at(-1)?.id ?? null;
        return {
            scanned: users.size,
            publicProfilesWritten,
            timeZonesDefaulted,
            usageBackfilled,
            nextAfterUid: users.size === PAGE_SIZE ? last : null,
        };
    },
);
