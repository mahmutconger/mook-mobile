import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

const INVALID_TOKEN_CODES = new Set([
    "messaging/invalid-registration-token",
    "messaging/registration-token-not-registered",
]);

export interface PushAlert {
    title: string;
    body: string;
}

/**
 * Kullanıcının kayıtlı tüm cihazlarına (`users/{uid}.fcmTokens`) bir FCM mesajı gönderir.
 *
 * - Android'e VERİ-yalnızca mesaj gider: `onMessageReceived()` uygulama ön planda, arka planda
 *   veya kapalıyken HER durumda tetiklenir ve bildirimi istemci kendi kanalında gösterir
 *   (bkz. `RevenueCatWebhookService`). `alert` verilirse başlık/metin `data.title`/`data.body`
 *   olarak da eklenir.
 * - iOS'ta veri-yalnızca mesaj görünmez; bu yüzden `alert` verilirse APNs bloğuna görünür
 *   bir uyarı eklenir.
 * - Geçersiz/kayıtsız jetonlar kullanıcının belgesinden temizlenir.
 *
 * Bildirim gönderimi her zaman "en iyi çaba"dır: hata fırlatmaz, yalnızca loglar.
 */
export async function sendPushToUser(uid: string, data: Record<string, string>, alert?: PushAlert): Promise<void> {
    try {
        const userRef = db.collection("users").doc(uid);
        const snapshot = await userRef.get();
        const raw = snapshot.data()?.fcmTokens;
        const tokens: string[] = Array.isArray(raw)
            ? raw.filter((token): token is string => typeof token === "string" && token.length > 0)
            : [];
        if (tokens.length === 0) return;

        const payload: Record<string, string> = alert ? { ...data, title: alert.title, body: alert.body } : data;
        const response = await admin.messaging().sendEachForMulticast({
            tokens,
            data: payload,
            android: { priority: "high" },
            ...(alert ? {
                apns: { payload: { aps: { alert: { title: alert.title, body: alert.body }, sound: "default" } } },
            } : {}),
        });
        if (response.failureCount === 0) return;

        const invalidTokens = response.responses
            .map((result, index) => (!result.success && result.error && INVALID_TOKEN_CODES.has(result.error.code)
                ? tokens[index]
                : null))
            .filter((token): token is string => token !== null);
        if (invalidTokens.length > 0) {
            await userRef.update({ fcmTokens: FieldValue.arrayRemove(...invalidTokens) });
        }
    } catch (error) {
        console.error("FCM push failed", { uid, type: data.type, error });
    }
}
