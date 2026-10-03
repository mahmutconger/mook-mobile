import * as admin from "firebase-admin";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { assertNotBanned } from "./moderation";
import { dayKey, limitsFor, monthKey, resetUsage, resolveTierFor, userTimeZone, type UsageData } from "./monetization";
import { likedMeUnlocksRemaining } from "./rewards";
import {
    deriveTokenKey,
    encodeEntryToken,
    planLikedMeEntries,
    type LikedMeEntry,
    type LikerCandidate,
    type PublicLikerProfile,
} from "./likedMePlan";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

/**
 * "Beni Beğenenler" giriş jetonlarını şifreleyen gizli anahtar (en az 16 karakter):
 * `firebase functions:secrets:set LIKED_ME_TOKEN_SECRET`. `getLikedMe` ve `unlockLikedMe`
 * AYNI anahtara bağlı olmalıdır.
 */
export const LIKED_ME_TOKEN_SECRET = defineSecret("LIKED_ME_TOKEN_SECRET");

/** Tek istekte değerlendirilen en fazla beğeni (okuma maliyetini sınırlar). */
const MAX_LIKERS = 100;

export interface LikedMeResponse {
    entries: LikedMeEntry[];
    /** Listelenebilir bekleyen beğeni sayısı (üst sayaç). */
    totalCount: number;
    /** [MAX_LIKERS] sınırına ulaşıldıysa `true` (sayaç "100+" gösterilir). */
    hasMore: boolean;
    /** Premium: tüm profiller açık. */
    revealAll: boolean;
    /** Bugün kalan açma hakkı; `null` = sınırsız. */
    unlocksRemainingToday: number | null;
}

function toPublicProfile(data: admin.firestore.DocumentData | undefined): PublicLikerProfile | null {
    if (!data) return null;
    const photos = Array.isArray(data.discoveryPhotos)
        ? data.discoveryPhotos.filter((url: unknown): url is string => typeof url === "string" && url.length > 0)
        : [];
    return {
        displayName: typeof data.displayName === "string" ? data.displayName : "",
        age: typeof data.age === "number" ? data.age : null,
        countryCode: typeof data.countryCode === "string" ? data.countryCode : null,
        languageCode: typeof data.languageCode === "string" ? data.languageCode : null,
        photoUrl: photos[0] ?? (typeof data.avatarUrl === "string" ? data.avatarUrl : null),
        bio: typeof data.bio === "string" ? data.bio : "",
        verified: data.verified === true,
        isMookActive: data.isMookActive === true,
        incognito: data.incognito === true,
        visibleTo: Array.isArray(data.visibleTo) ? data.visibleTo.filter((v: unknown): v is string => typeof v === "string") : [],
    };
}

/**
 * Oturum açmış kullanıcıyı beğenen (ve henüz karşılık vermediği) kişileri döner.
 *
 * - İstemci `interactions` koleksiyonunu "bana gelen beğeniler" olarak SORGULAYAMAZ; liste
 *   yalnızca bu callable üzerinden, gizlilik kuralları uygulanarak üretilir.
 * - Kilitli girişlerde ad, yaş, biyografi ve uid DÖNMEZ; yalnızca opak jeton ve istemcinin
 *   bulanıklaştıracağı önizleme fotoğrafı döner.
 * - Premium (`likedMeUnlocksPerDay === null`) tüm profilleri açık görür.
 */
export const getLikedMe = onCall(
    { region: "us-central1", enforceAppCheck: true, secrets: [LIKED_ME_TOKEN_SECRET] },
    async (request): Promise<LikedMeResponse> => {
        const uid = request.auth?.uid;
        if (!uid) throw new HttpsError("unauthenticated", "Authentication required.");
        await assertNotBanned(uid);

        const tier = await resolveTierFor(uid, request);
        const limits = await limitsFor(tier);
        const zone = await userTimeZone(uid);
        const [likesSnapshot, usageSnapshot] = await Promise.all([
            db.collection("interactions")
                .where("toUserId", "==", uid)
                .where("type", "==", "like")
                .orderBy("timestamp", "desc")
                .limit(MAX_LIKERS)
                .get(),
            db.collection("usage").doc(uid).get(),
        ]);

        const likes = likesSnapshot.docs
            .map((doc) => ({ likerUid: doc.data().fromUserId, likedAt: Number(doc.data().timestamp ?? 0) }))
            .filter((like): like is { likerUid: string; likedAt: number } => typeof like.likerUid === "string" && like.likerUid.length > 0);

        const candidates: LikerCandidate[] = [];
        if (likes.length > 0) {
            const reverseRefs = likes.map((like) => db.collection("interactions").doc(`${uid}_${like.likerUid}`));
            const unlockRefs = likes.map((like) => db.collection("liked_me_unlocks").doc(`${uid}_${like.likerUid}`));
            const profileRefs = likes.map((like) => db.collection("public_profiles").doc(like.likerUid));
            const snapshots = await db.getAll(...reverseRefs, ...unlockRefs, ...profileRefs);
            likes.forEach((like, index) => {
                candidates.push({
                    likerUid: like.likerUid,
                    likedAt: like.likedAt,
                    viewerAlreadyActed: snapshots[index].exists,
                    unlocked: snapshots[likes.length + index].exists,
                    profile: toPublicProfile(snapshots[2 * likes.length + index].data()),
                });
            });
        }

        const key = deriveTokenKey(LIKED_ME_TOKEN_SECRET.value());
        const revealAll = limits.likedMeUnlocksPerDay === null;
        const entries = planLikedMeEntries({
            viewerUid: uid,
            candidates,
            revealAll,
            now: Date.now(),
            makeToken: (likerUid) => encodeEntryToken(key, uid, likerUid),
        });

        const usage = resetUsage((usageSnapshot.data() ?? {}) as UsageData, dayKey(zone), monthKey(zone));
        return {
            entries,
            totalCount: entries.length,
            hasMore: likesSnapshot.size >= MAX_LIKERS,
            revealAll,
            unlocksRemainingToday: likedMeUnlocksRemaining({
                tier,
                basePerDay: limits.likedMeUnlocksPerDay,
                rewardedUnlocksToday: Number(usage.rewardedLikedMeUnlocks ?? 0),
                usedToday: Number(usage.likedMeUnlocks ?? 0),
            }),
        };
    },
);
