import { HttpsError, onCall } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

// Gereksinim 1.13 (Moderasyon & Yasaklamalar). Firebase, index.ts'ten önce import edilen
// modülleri yükler; bu yüzden initializeApp burada da (idempotent olarak) çağrılır — aynı
// desen `monetization.ts`/`revenuecatWebhook.ts`'de de kullanılır.
if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

/**
 * Verilen kullanıcı yasaklıysa (`users/{uid}.isBanned == true`) bir `HttpsError` fırlatır.
 *
 * Bu, istemci tarafındaki `ModerationGate` interceptor'ının SUNUCU tarafı karşılığıdır —
 * istemci değiştirilmiş/kök erişimli bir cihazdan geliyorsa istemci taraflı engelleme
 * atlatılabilir; bu fonksiyon o durumda son, gerçek güvenlik sınırıdır. Durum DEĞİŞTİREN
 * (write) her callable'ın başında çağrılmalıdır — burada en yüksek istismar riskli iki
 * callable'a (`swipe`, `sendMessage`) uygulanmıştır; yeni eklenen her callable de aynı
 * deseni izlemelidir.
 */
export async function assertNotBanned(uid: string): Promise<void> {
    const snapshot = await db.collection("users").doc(uid).get();
    if (snapshot.data()?.isBanned === true) {
        throw new HttpsError("permission-denied", "user-banned");
    }
}

/**
 * Bir kullanıcıyı yasaklar/yasağını kaldırır — Gereksinim 1.13'te "sunucu bir kullanıcıyı
 * bayraklar ve yasak uygular" olarak tarif edilen mekanizmanın somut karşılığı.
 *
 * Yalnızca özel `admin` auth claim'ine sahip çağıranlar kullanabilir — bu claim bu
 * fonksiyonun KAPSAMI DIŞINDA, güvenilir bir moderatöre Firebase Console/Admin SDK
 * betiği üzerinden manuel olarak verilmelidir (`admin.auth().setCustomUserClaims(uid,
 * { admin: true })`); yeni bir moderasyon sistemi ilk kurulurken standart bir adımdır.
 *
 * `isBanned` DOĞRUDAN Admin SDK ile (güvenlik kurallarını atlayarak) yazılır — istemci
 * `firestore.rules`'ta bu alana asla yazamaz (bkz. `users/{uid}` güncelleme kısıtlaması).
 */
// Gereksinim 2 (Faz 6): Kapsamlı App Check Uygulaması.
export const moderateUser = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    if (request.auth?.token?.admin !== true) {
        throw new HttpsError("permission-denied", "admin-only");
    }
    const data = request.data as { targetUid?: unknown; isBanned?: unknown; reason?: unknown };
    if (typeof data.targetUid !== "string" || !data.targetUid || typeof data.isBanned !== "boolean") {
        throw new HttpsError("invalid-argument", "Invalid moderation request.");
    }

    const targetRef = db.collection("users").doc(data.targetUid);
    if (data.isBanned) {
        await targetRef.set(
            {
                isBanned: true,
                banReason: typeof data.reason === "string" && data.reason.length > 0 ? data.reason : "terms-of-service-violation",
                bannedAt: Date.now(),
            },
            { merge: true },
        );
    } else {
        await targetRef.set(
            {
                isBanned: false,
                banReason: FieldValue.delete(),
                bannedAt: FieldValue.delete(),
            },
            { merge: true },
        );
    }
    return { success: true };
});
