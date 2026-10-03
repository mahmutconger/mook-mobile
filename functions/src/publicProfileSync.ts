import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { buildPublicProfile, nextAgeRefreshAt } from "./profilePrivacy";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

const DEFAULT_TIME_ZONE = "Europe/Istanbul";
/** Tek bir zamanlanmış çalıştırmada işlenen en fazla profil (zaman aşımını önler). */
const MAX_PROFILES_PER_RUN = 2_000;
const PAGE_SIZE = 200;

/**
 * Sunucuya ait, istemcinin OKUYAMADIĞI yardımcı belge: `private_profile_meta/{uid}`.
 * Yalnızca yaşın ne zaman yeniden hesaplanacağını tutar (doğum gününü herkese açık profile
 * sızdırmamak için bu bilgi `public_profiles`ta değil burada durur). Firestore kuralları
 * bu koleksiyona istemci erişimini tamamen kapatır.
 */
export const PROFILE_META_COLLECTION = "private_profile_meta";

function timeZoneOf(data: Record<string, unknown>): string {
    return typeof data.timeZone === "string" && data.timeZone.length <= 64 ? data.timeZone : DEFAULT_TIME_ZONE;
}

/**
 * `users/{uid}` verisinden herkese açık profili ve yaş yenileme kaydını aynı toplu yazmaya
 * (batch) ekler. Eski şemadan kalan `birthDateMillis` alanı herkese açık belgeden SİLİNİR.
 */
export function addPublicProfileWrites(
    batch: admin.firestore.WriteBatch,
    uid: string,
    data: Record<string, unknown>,
    now: number,
): void {
    const zone = timeZoneOf(data);
    batch.set(db.collection("public_profiles").doc(uid), {
        ...buildPublicProfile(data, now, zone),
        birthDateMillis: FieldValue.delete(),
    }, { merge: true });
    const refreshAt = nextAgeRefreshAt(data.birthDateMillis, now, zone);
    const metaRef = db.collection(PROFILE_META_COLLECTION).doc(uid);
    if (refreshAt === null) batch.delete(metaRef);
    else batch.set(metaRef, { uid, ageRefreshAt: refreshAt, updatedAt: now });
}

/** Tek kullanıcı için herkese açık profili `users` belgesinden yeniden üretir. */
export async function writePublicProfile(uid: string, data: Record<string, unknown>, now = Date.now()): Promise<void> {
    const batch = db.batch();
    addPublicProfileWrites(batch, uid, data, now);
    await batch.commit();
}

/** Kullanıcı silindiğinde herkese açık profil ve yaş yenileme kaydı birlikte silinir. */
export async function deletePublicProfile(uid: string): Promise<void> {
    const batch = db.batch();
    batch.delete(db.collection("public_profiles").doc(uid));
    batch.delete(db.collection(PROFILE_META_COLLECTION).doc(uid));
    await batch.commit();
}

async function refreshFromUsers(uids: string[], now: number): Promise<number> {
    if (uids.length === 0) return 0;
    const userSnapshots = await db.getAll(...uids.map((uid) => db.collection("users").doc(uid)));
    const batch = db.batch();
    userSnapshots.forEach((snapshot, index) => {
        if (snapshot.exists) {
            addPublicProfileWrites(batch, uids[index], snapshot.data() ?? {}, now);
        } else {
            batch.delete(db.collection(PROFILE_META_COLLECTION).doc(uids[index]));
        }
    });
    await batch.commit();
    return uids.length;
}

/**
 * Yaşları güncel tutan zamanlanmış görev (6 saatte bir):
 * 1. Yaş yenileme anı gelmiş profiller (`private_profile_meta.ageRefreshAt <= şimdi`) — doğum
 *    günü gelen kullanıcının herkese açık yaşı bir artar.
 * 2. Eski şemadan kalan, herkese açık belgesinde hâlâ `birthDateMillis` bulunan profiller —
 *    alan silinir ve yerine `age` yazılır (tek seferlik göç; iş bitince sorgu boş döner).
 */
export const refreshPublicProfileAges = onSchedule(
    { schedule: "every 6 hours", region: "us-central1", timeZone: "Europe/Istanbul", timeoutSeconds: 540 },
    async () => {
        const now = Date.now();
        let processed = 0;

        while (processed < MAX_PROFILES_PER_RUN) {
            const due = await db.collection(PROFILE_META_COLLECTION)
                .where("ageRefreshAt", "<=", now)
                .orderBy("ageRefreshAt")
                .limit(PAGE_SIZE)
                .get();
            if (due.empty) break;
            processed += await refreshFromUsers(due.docs.map((doc) => doc.id), now);
            if (due.size < PAGE_SIZE) break;
        }

        while (processed < MAX_PROFILES_PER_RUN) {
            const legacy = await db.collection("public_profiles")
                .where("birthDateMillis", "!=", null)
                .limit(PAGE_SIZE)
                .get();
            if (legacy.empty) break;
            processed += await refreshFromUsers(legacy.docs.map((doc) => doc.id), now);
            if (legacy.size < PAGE_SIZE) break;
        }

        console.info("refreshPublicProfileAges tamamlandı", { processed });
    },
);
