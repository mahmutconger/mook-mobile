import { createHmac, timingSafeEqual } from "node:crypto";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { onRequest } from "firebase-functions/v2/https";
import type { Tier } from "./monetization";
import { sendPushToUser } from "./push";
import {
    REVENUECAT_API_KEY,
    RevenueCatUnavailableError,
    syncCustomerEntitlements,
    type EntitlementSyncResult,
} from "./revenuecatSync";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

/**
 * Doğrudan RevenueCat webhook entegrasyonu için üretilen HMAC imza anahtarı (Firebase
 * Extension anahtarından bilinçli olarak AYRIDIR):
 * `firebase functions:secrets:set RC_WEBHOOK_SIGNING_SECRET`.
 */
const RC_WEBHOOK_SIGNING_SECRET = defineSecret("RC_WEBHOOK_SIGNING_SECRET");
const MAX_SIGNATURE_AGE_SECONDS = 5 * 60;
/** Yeni bir faturalandırma döngüsü başlatan olaylar — Boost kotası bunlarda sıfırlanır. */
const CYCLE_START_EVENTS = new Set(["RENEWAL", "INITIAL_PURCHASE"]);
/** Yetkiyi geri alan olaylar — bekleyen ödeme bildirimi bunlarda gönderilmez. */
const REVOKING_EVENTS = new Set(["EXPIRATION", "REFUND", "BILLING_ISSUE", "SUBSCRIPTION_PAUSED"]);

const TIER_DISPLAY_NAMES: Record<Tier, string> = {
    FREE: "Ücretsiz",
    ECONOMY: "Ekonomik",
    STANDARD: "Standart",
    PREMIUM: "Premium",
};

type RevenueCatEvent = {
    id?: unknown;
    type?: unknown;
    app_user_id?: unknown;
    original_app_user_id?: unknown;
    transferred_from?: unknown;
    transferred_to?: unknown;
    store?: unknown;
    environment?: unknown;
};

type RevenueCatEnvelope = { event?: RevenueCatEvent };

function asNonEmptyString(value: unknown): string | null {
    return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function parseSignature(header: string | undefined): { timestamp: number; signature: string } | null {
    if (!header) return null;
    const parts = new Map(
        header.split(",").map((part) => {
            const [key, ...value] = part.trim().split("=");
            return [key, value.join("=")];
        }),
    );
    const timestamp = Number(parts.get("t"));
    const signature = parts.get("v1");
    if (!Number.isFinite(timestamp) || typeof signature !== "string" || !/^[a-f0-9]{64}$/i.test(signature)) return null;
    return { timestamp, signature };
}

function hasValidSignature(rawBody: Buffer, header: string | undefined, secret: string): boolean {
    const parsed = parseSignature(header);
    if (!parsed || Math.abs(Date.now() / 1000 - parsed.timestamp) > MAX_SIGNATURE_AGE_SECONDS) return false;
    const expected = createHmac("sha256", secret)
        .update(`${parsed.timestamp}.`)
        .update(rawBody)
        .digest("hex");
    const expectedBuffer = Buffer.from(expected, "hex");
    const receivedBuffer = Buffer.from(parsed.signature, "hex");
    return expectedBuffer.length === receivedBuffer.length && timingSafeEqual(expectedBuffer, receivedBuffer);
}

function isFirebaseUid(value: string): boolean {
    // Firebase Auth UIDs are max. 128 chars. RevenueCat anonymous aliases must never
    // receive trusted claims or create a customer document in our backend.
    return value.length <= 128 && !value.startsWith("$RCAnonymousID:");
}

/**
 * Olaydan etkilenen TÜM Firebase kullanıcılarını toplar: `app_user_id` ve TRANSFER
 * olaylarında `transferred_from`/`transferred_to` listeleri. Anonim RevenueCat kimlikleri elenir.
 */
function affectedUids(event: RevenueCatEvent): string[] {
    const candidates: unknown[] = [event.app_user_id];
    if (Array.isArray(event.transferred_from)) candidates.push(...event.transferred_from);
    if (Array.isArray(event.transferred_to)) candidates.push(...event.transferred_to);
    const uids = candidates
        .map(asNonEmptyString)
        .filter((uid): uid is string => uid !== null && isFirebaseUid(uid));
    return [...new Set(uids)];
}

/** Firebase'de var olan kullanıcıları döner; geçici Auth hatası yeniden deneme için fırlatılır. */
async function existingFirebaseUids(uids: string[]): Promise<string[]> {
    const existing: string[] = [];
    for (const uid of uids) {
        try {
            await admin.auth().getUser(uid);
            existing.push(uid);
        } catch (error) {
            if ((error as { code?: string }).code !== "auth/user-not-found") throw error;
        }
    }
    return existing;
}

/**
 * RevenueCat yaşam döngüsü olayları için bağımsız v2 webhook alıcısı.
 *
 * RevenueCat REST API Doğrulama Kuralı: olay yükündeki alanlar (`entitlement_ids`, `type`
 * vb.) abonelik DURUMU için ASLA kullanılmaz — yalnızca "bir şey değişti" sinyalidir. HER
 * olayda etkilenen her kullanıcı için RevenueCat REST API'si çağrılır ve mutlak güncel durum
 * `syncCustomerEntitlements` ile yazılır (`verifyEntitlementNow` ile AYNI adım). Böylece sırası
 * bozuk, tekrarlanan veya kaçırılan olaylar durumu asla bozamaz.
 *
 * REST çağrısı başarısız olursa HTTP 500 dönülür; RevenueCat olayı yeniden gönderir.
 * Yan etkiler (Boost döngü sıfırlaması, bildirimler) olay başına YALNIZCA BİR KEZ uygulanır.
 */
export const revenueCatWebhook = onRequest(
    {
        region: "us-central1",
        secrets: [RC_WEBHOOK_SIGNING_SECRET, REVENUECAT_API_KEY],
        timeoutSeconds: 60,
        cors: false,
    },
    async (request, response) => {
        if (request.method !== "POST") {
            response.status(405).send("Method Not Allowed");
            return;
        }

        const rawBody = request.rawBody;
        const secret = RC_WEBHOOK_SIGNING_SECRET.value();
        if (!rawBody || !secret || !hasValidSignature(rawBody, request.header("X-RevenueCat-Webhook-Signature"), secret)) {
            response.status(401).send("Unauthorized");
            return;
        }

        const event = (request.body as RevenueCatEnvelope)?.event;
        const eventId = asNonEmptyString(event?.id);
        const type = asNonEmptyString(event?.type)?.toUpperCase();
        if (!event || !eventId || !type) {
            // Sözdizimsel olarak geçerli ama işlenemez bir olay: yeniden denemek işe yaramaz.
            response.status(200).send("Ignored");
            return;
        }

        const eventRef = db.collection("processed_revenuecat_events").doc(eventId);
        if ((await eventRef.get()).data()?.status === "processed") {
            response.status(200).send("OK");
            return;
        }

        let uids: string[];
        try {
            uids = await existingFirebaseUids(affectedUids(event));
        } catch (error) {
            console.error("RevenueCat webhook could not resolve Firebase users", { eventId, error });
            response.status(500).send("Retry later");
            return;
        }
        if (uids.length === 0) {
            response.status(200).send("Ignored");
            return;
        }

        // 1) Birleşik senkronizasyon: HER olayda REST API'den mutlak güncel durum.
        const results: EntitlementSyncResult[] = [];
        try {
            for (const uid of uids) {
                results.push(await syncCustomerEntitlements(uid, REVENUECAT_API_KEY.value(), `webhook:${type}`));
            }
        } catch (error) {
            const retryable = error instanceof RevenueCatUnavailableError;
            console.error("RevenueCat webhook sync failed", { eventId, type, retryable, error });
            response.status(500).send("Retry later");
            return;
        }

        // 2) Olay başına bir kez: işlenmiş olarak işaretle ve (gerekirse) Boost döngüsünü sıfırla.
        //    Gereksinim 2.12: Boost kotası takvim ayına değil, kullanıcının KENDİ faturalandırma
        //    döngüsüne göre sıfırlanır — RENEWAL/INITIAL_PURCHASE tam olarak o anı işaret eder.
        const primaryUid = asNonEmptyString(event.app_user_id);
        const firstDelivery = await db.runTransaction(async (transaction) => {
            const snapshot = await transaction.get(eventRef);
            if (snapshot.data()?.status === "processed") return false;
            if (CYCLE_START_EVENTS.has(type) && primaryUid && uids.includes(primaryUid)) {
                transaction.set(db.collection("usage").doc(primaryUid), { boosts: 0 }, { merge: true });
            }
            transaction.set(eventRef, {
                status: "processed",
                type,
                uids,
                environment: asNonEmptyString(event.environment),
                syncedTiers: Object.fromEntries(results.map((result) => [result.uid, result.tier])),
                processedAt: FieldValue.serverTimestamp(),
            }, { merge: true });
            return true;
        });

        // 3) Bildirimler (en iyi çaba; webhook yanıtını asla bozmaz).
        if (firstDelivery) {
            for (const result of results) {
                if (result.uid !== primaryUid) continue;
                await dispatchLifecycleNotification(type, asNonEmptyString(event.store), result);
            }
        }

        response.status(200).send("OK");
    },
);

/**
 * Olay türüne göre kullanıcıya Türkçe FCM bildirimi gönderir.
 * - BILLING_ISSUE: ödeme alınamadı, ödeme yöntemini güncelle.
 * - EXPIRATION: abonelik sona erdi (yalnızca kademe gerçekten düştüyse / Ücretsiz'e inildiyse —
 *   örneğin bir yükseltme sonrası eski ürünün sona ermesi kullanıcıyı yanıltmasın).
 * - Diğer yetki veren olaylar: bekleyen ödeme onaylandıysa bilgilendir (Gereksinim 1.12).
 */
async function dispatchLifecycleNotification(type: string, store: string | null, result: EntitlementSyncResult): Promise<void> {
    if (type === "BILLING_ISSUE") {
        const storeName = store === "APP_STORE" || store === "MAC_APP_STORE" ? "App Store" : "Google Play";
        await sendPushToUser(result.uid, { type: "billing_issue", store: store ?? "" }, {
            title: "Ödemen alınamadı",
            body: `Aboneliğinin kesintiye uğramaması için ${storeName} hesabındaki ödeme yöntemini güncelle.`,
        });
        return;
    }

    if (type === "EXPIRATION") {
        if (!result.downgraded && result.tier !== "FREE") return;
        const cleanupNotes: string[] = [];
        if (result.enforcement.closedRooms.length > 0) cleanupNotes.push("plan sınırını aşan odaların kapatıldı");
        if (result.enforcement.boostEnded) cleanupNotes.push("aktif Boost'un sonlandırıldı");
        const cleanup = cleanupNotes.length > 0 ? ` ${capitalize(cleanupNotes.join(" ve "))}.` : "";
        const alert = result.tier === "FREE"
            ? {
                title: "Aboneliğin sona erdi",
                body: `Artık Ücretsiz plandasın.${cleanup} Dilediğin zaman yeniden abone olabilirsin.`,
            }
            : {
                title: "Planın değişti",
                body: `Önceki aboneliğinin süresi doldu; artık ${TIER_DISPLAY_NAMES[result.tier]} plandasın.${cleanup}`,
            };
        await sendPushToUser(result.uid, { type: "subscription_expired", tier: result.tier }, alert);
        return;
    }

    if (!REVOKING_EVENTS.has(type) && result.tier !== "FREE") {
        await notifyIfPendingPurchaseResolved(result.uid);
    }
}

function capitalize(text: string): string {
    return text.length === 0 ? text : text.charAt(0).toLocaleUpperCase("tr-TR") + text.slice(1);
}

/**
 * Gereksinim 1.12: `users/{uid}.hasPendingPurchase` bayrağı ayarlıysa temizler ve cihazlara
 * bir VERİ mesajı gönderir — `RevenueCatWebhookService` bunu alıp yerel bir bildirim gösterir.
 */
async function notifyIfPendingPurchaseResolved(uid: string): Promise<void> {
    const userRef = db.collection("users").doc(uid);
    const userSnapshot = await userRef.get();
    if (!userSnapshot.data()?.hasPendingPurchase) return;
    await userRef.set({ hasPendingPurchase: FieldValue.delete() }, { merge: true });
    await sendPushToUser(uid, { type: "pending_purchase_confirmed" });
}
