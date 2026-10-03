import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { limitsFor, tierFromEntitlements, type Tier } from "./monetization";
import {
    activeEntitlementIds,
    isTierDowngrade,
    isTierName,
    planPlanEnforcement,
    type RevenueCatSubscriberPayload,
} from "./tierPolicy";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

/**
 * RevenueCat gizli (secret) REST API anahtarı — koda gömülmez, yalnızca Secret Manager'dan
 * okunur: `firebase functions:secrets:set REVENUECAT_API_KEY`. Bu anahtarı kullanan her
 * fonksiyon `secrets: [REVENUECAT_API_KEY]` ile bağlanmalıdır.
 */
export const REVENUECAT_API_KEY = defineSecret("REVENUECAT_API_KEY");

const REVENUECAT_TIMEOUT_MS = 10_000;

/** RevenueCat REST API'sine ulaşılamadı; çağıran yeniden denemelidir (webhook → HTTP 500). */
export class RevenueCatUnavailableError extends Error {
    constructor(message: string) {
        super(message);
        this.name = "RevenueCatUnavailableError";
    }
}

export interface PlanEnforcementResult {
    closedRooms: string[];
    boostEnded: boolean;
    incognitoDisabled: boolean;
}

export interface EntitlementSyncResult {
    uid: string;
    tier: Tier;
    previousTier: Tier;
    entitlements: string[];
    downgraded: boolean;
    /** Daha yeni bir senkronizasyon zaten yazılmışsa `true` (bu sonuç yazılmadı). */
    stale: boolean;
    enforcement: PlanEnforcementResult;
}

const NO_ENFORCEMENT: PlanEnforcementResult = { closedRooms: [], boostEnded: false, incognitoDisabled: false };

/**
 * RevenueCat REST API Doğrulama Kuralı: abonelik durumu ASLA webhook yükündeki olay
 * alanlarından türetilmez; her zaman RevenueCat'in `GET /v1/subscribers/{uid}` yanıtından
 * (mutlak güncel durum) okunur. Kullanıcı RevenueCat'te hiç yoksa (404) aktif yetki yoktur.
 */
export async function fetchActiveEntitlements(uid: string, apiKey: string, now = Date.now()): Promise<string[]> {
    let response: Response;
    try {
        response = await fetch(
            `https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(uid)}`,
            {
                headers: { Authorization: `Bearer ${apiKey}`, Accept: "application/json" },
                signal: AbortSignal.timeout(REVENUECAT_TIMEOUT_MS),
            },
        );
    } catch (error) {
        throw new RevenueCatUnavailableError(`RevenueCat REST isteği başarısız: ${String(error)}`);
    }
    if (response.status === 404) return [];
    if (!response.ok) throw new RevenueCatUnavailableError(`RevenueCat REST HTTP ${response.status}`);
    const payload = await response.json() as RevenueCatSubscriberPayload;
    return activeEntitlementIds(payload, now);
}

/**
 * Birleşik senkronizasyon adımı — hem `revenueCatWebhook` (HER olay için) hem de
 * `verifyEntitlementNow` bunu kullanır:
 *
 * 1. RevenueCat REST API'sinden güncel aktif yetkileri çeker.
 * 2. `customers/{uid}` belgesine yazar (`syncedTier`, `lastSyncedAt`). Daha yeni bir okuma zaten
 *    yazılmışsa (eşzamanlı webhook'lar) eski sonuç yazılmaz — son okunan durum kazanır.
 * 3. Firebase Auth özel talebini (`revenueCatEntitlements`) diğer talepleri koruyarak günceller.
 * 4. Yeni kademenin sınırlarını sunucu tarafında uygular: fazla odaları kapatır, kademe
 *    düşüşünde aktif Boost'u sonlandırır, desteklenmiyorsa gizli modu kapatır.
 */
export async function syncCustomerEntitlements(uid: string, apiKey: string, source: string): Promise<EntitlementSyncResult> {
    const fetchedAt = Date.now();
    const entitlements = await fetchActiveEntitlements(uid, apiKey, fetchedAt);
    const tier = tierFromEntitlements(entitlements);
    const customerRef = db.collection("customers").doc(uid);

    const outcome = await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(customerRef);
        const data = snapshot.data() ?? {};
        const previousTier: Tier = isTierName(data.syncedTier)
            ? data.syncedTier
            : tierFromEntitlements(data.revenueCatEntitlements ?? data.activeEntitlements);
        if (typeof data.lastSyncedAt === "number" && data.lastSyncedAt > fetchedAt) {
            return { previousTier, stale: true };
        }
        transaction.set(customerRef, {
            appUserId: uid,
            revenueCatEntitlements: entitlements,
            activeEntitlements: entitlements,
            syncedTier: tier,
            lastSyncedAt: fetchedAt,
            lastVerifiedAt: fetchedAt,
            lastSyncSource: source,
            updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
        return { previousTier, stale: false };
    });

    if (outcome.stale) {
        return {
            uid, tier, previousTier: outcome.previousTier, entitlements,
            downgraded: false, stale: true, enforcement: NO_ENFORCEMENT,
        };
    }

    await updateEntitlementClaim(uid, entitlements);
    const downgraded = isTierDowngrade(outcome.previousTier, tier);
    const enforcement = await enforcePlanLimits(uid, tier, downgraded);
    if (downgraded || enforcement.closedRooms.length > 0 || enforcement.boostEnded || enforcement.incognitoDisabled) {
        console.info("RevenueCat sync applied plan limits", {
            uid, source, previousTier: outcome.previousTier, tier, ...enforcement,
        });
    }
    return {
        uid, tier, previousTier: outcome.previousTier, entitlements,
        downgraded, stale: false, enforcement,
    };
}

/** Özel talebi yalnızca değiştiyse yazar; diğer talepler (ör. `admin`) korunur. */
async function updateEntitlementClaim(uid: string, entitlements: string[]): Promise<void> {
    let user: admin.auth.UserRecord;
    try {
        user = await admin.auth().getUser(uid);
    } catch (error) {
        if ((error as { code?: string }).code === "auth/user-not-found") return;
        throw error;
    }
    const claims = user.customClaims ?? {};
    const current = Array.isArray(claims.revenueCatEntitlements)
        ? [...(claims.revenueCatEntitlements as unknown[])].map(String).sort()
        : null;
    if (current !== null && current.length === entitlements.length && current.every((value, i) => value === entitlements[i])) {
        return;
    }
    await admin.auth().setCustomUserClaims(uid, { ...claims, revenueCatEntitlements: entitlements });
}

/**
 * Kademe sınırlarını `users/{uid}` üzerinde TEK transaction'da uygular (idempotent).
 * Kurallar `tierPolicy.ts`/`planPlanEnforcement` içindedir.
 */
export async function enforcePlanLimits(uid: string, tier: Tier, downgraded: boolean): Promise<PlanEnforcementResult> {
    const limits = await limitsFor(tier);
    const userRef = db.collection("users").doc(uid);
    return db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(userRef);
        if (!snapshot.exists) return NO_ENFORCEMENT;
        const user = snapshot.data() ?? {};
        const rooms = Array.isArray(user.roomLanguageCodes)
            ? user.roomLanguageCodes.filter((room): room is string => typeof room === "string")
            : [user.roomLanguageCode].filter((room): room is string => typeof room === "string");
        const lastActiveAt = (typeof user.roomLastActiveAt === "object" && user.roomLastActiveAt !== null
            ? user.roomLastActiveAt
            : {}) as Record<string, number>;
        const now = Date.now();

        const plan = planPlanEnforcement({
            downgraded,
            limits: { roomSlots: limits.roomSlots, boostsPerMonth: limits.boostsPerMonth, incognito: limits.incognito },
            user: {
                rooms,
                activeRoom: typeof user.roomLanguageCode === "string" ? user.roomLanguageCode : null,
                lastActiveAt,
                boostUntil: typeof user.boostUntil === "number" ? user.boostUntil : null,
                incognito: user.incognito === true,
            },
            now,
        });

        const updates: Record<string, unknown> = {};
        if (plan.roomTrim) {
            // `update`, haritayı bütünüyle değiştirir; kapatılan odaların kaydı gerçekten silinir.
            const roomLastActiveAt = { ...lastActiveAt };
            plan.roomTrim.closedRooms.forEach((room) => { delete roomLastActiveAt[room]; });
            updates.roomLanguageCodes = plan.roomTrim.rooms;
            updates.roomLanguageCode = plan.roomTrim.activeRoom;
            updates.roomLastActiveAt = roomLastActiveAt;
        }
        // Boost'u "şimdi" bitmiş olarak işaretlemek, Keşfet sıralamasından anında düşürür
        // (`boostUntil > now` koşulu) ve `getBoostSummary` özetini bozmaz.
        if (plan.endBoost) updates.boostUntil = now;
        if (plan.disableIncognito) updates.incognito = false;
        // Herkese açık profile yansıyan kademe (Premium rozeti, Keşfet önceliği) —
        // `syncPublicProfile` bu alanı `public_profiles`a kopyalar.
        if (user.subscriptionTier !== tier) updates.subscriptionTier = tier;
        if (Object.keys(updates).length > 0) transaction.update(userRef, updates);

        return {
            closedRooms: plan.roomTrim?.closedRooms ?? [],
            boostEnded: plan.endBoost,
            incognitoDisabled: plan.disableIncognito,
        };
    });
}
