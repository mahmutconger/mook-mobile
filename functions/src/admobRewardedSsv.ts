import { createPublicKey, verify as verifySignature } from "node:crypto";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onRequest } from "firebase-functions/v2/https";
import { REWARD_DEFINITIONS, REWARDED_ELIGIBLE_TIERS, isRewardKey, planRewardGrant } from "./rewards";
import {
    dayKey,
    monthKey,
    resetUsage,
    resolveTierFor,
    userTimeZone,
    type UsageData,
} from "./monetization";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

/**
 * Gereksinim 2 (Faz 6, Kapsamlı App Check Uygulaması): bu fonksiyon KASITLI olarak
 * `enforceAppCheck`/limited-use jeton kapsamının DIŞINDADIR.
 *
 * Bu bir `onCall` DEĞİL, bir `onRequest` webhook'udur -- WalkMatch istemcisi tarafından
 * HİÇBİR ZAMAN doğrudan çağrılmaz; Google'ın kendi reklam sunucuları tarafından
 * ÇAĞRILIR (bkz. AdMob "Server-Side Verification" callback URL'si). Firebase App Check
 * bir İSTEMCİ (cihaz/uygulama) bütünlük kanıtlama mekanizmasıdır ve burada MİMARİ
 * olarak UYGULANAMAZ -- Google'ın sunucuları bir Play Integrity jetonu ÜRETEMEZ/
 * TAŞIMAZ. Bunun yerine bütünlük, ZATEN uygulanmış olan `verifyAdMobSsvQuery()`
 * (Google'ın DÖNEN ECDSA anahtarlarıyla imza doğrulaması) ile sağlanır -- bu, tam
 * olarak Google'ın SSV callback'leri için BELGELEDİĞİ güvenlik mekanizmasıdır.
 */

const KEY_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
const KEY_CACHE_TTL_MS = 23 * 60 * 60 * 1000;

/**
 * Uygulamanın TÜM ödüllü yerleşimleri (beğeni, "beni beğenenler", oda değiştirme) bu TEK AdMob
 * reklam birimini paylaşır. AdMob, bir birim için konsolda tanımlı TEK ödül öğesini/miktarını
 * gönderir; bu yüzden `reward_item`/`reward_amount` ödül TÜRÜNÜ ayırt etmek için KULLANILAMAZ
 * (eskiden her ödül için ayrı `reward_item` bekleniyordu ve bu, "beni beğenenler" ile oda
 * ödüllerinin her zaman sessizce yok sayılmasına yol açıyordu). Ödül türü YALNIZCA imzalı
 * `custom_data` ile belirlenir; verilecek hak miktarı da AdMob konsolundan değil
 * `rewards.ts`'teki tablodan gelir (ör. beğeni reklamı = +5 beğeni).
 */
const REWARDED_AD_UNIT_SUFFIX = "4818972517";

type VerifierKey = { keyId: string; publicKey: ReturnType<typeof createPublicKey> };
type KeySet = { expiresAt: number; byId: Map<string, VerifierKey["publicKey"]> };
let cachedKeys: KeySet | undefined;

type SsvFields = {
    ad_unit?: string;
    custom_data?: string;
    key_id?: string;
    reward_amount?: string;
    reward_item?: string;
    signature?: string;
    timestamp?: string;
    transaction_id?: string;
    user_id?: string;
};

function signatureBytes(value: string): Buffer | null {
    if (!/^[A-Za-z0-9_-]+={0,2}$/.test(value)) return null;
    const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
    try {
        return Buffer.from(base64, "base64");
    } catch {
        return null;
    }
}

/** Verifies the unmodified query bytes using Google's rotating AdMob ECDSA keys. */
export function verifyAdMobSsvQuery(rawQuery: string, byId: Map<string, VerifierKey["publicKey"]>): boolean {
    const signatureMarker = "&signature=";
    const markerIndex = rawQuery.lastIndexOf(signatureMarker);
    if (markerIndex <= 0) return false;
    const keyMarker = "&key_id=";
    const keyIndex = rawQuery.lastIndexOf(keyMarker);
    if (keyIndex <= markerIndex) return false;

    const content = rawQuery.slice(0, markerIndex);
    const signatureValue = rawQuery.slice(markerIndex + signatureMarker.length, keyIndex);
    const keyId = rawQuery.slice(keyIndex + keyMarker.length);
    if (!/^\d{1,20}$/.test(keyId) || !signatureValue || keyId.includes("&")) return false;

    const publicKey = byId.get(keyId);
    const signature = signatureBytes(signatureValue);
    if (!publicKey || !signature) return false;

    try {
        return verifySignature("sha256", Buffer.from(content, "utf8"), { key: publicKey, dsaEncoding: "der" }, signature);
    } catch {
        return false;
    }
}

async function loadVerifierKeys(forceRefresh = false): Promise<KeySet["byId"]> {
    if (!forceRefresh && cachedKeys && cachedKeys.expiresAt > Date.now()) return cachedKeys.byId;

    const response = await fetch(KEY_URL, { signal: AbortSignal.timeout(8_000) });
    if (!response.ok) throw new Error(`AdMob key server returned HTTP ${response.status}`);
    const payload = await response.json() as { keys?: Array<{ keyId?: number; base64?: string }> };
    const byId = new Map<string, VerifierKey["publicKey"]>();
    for (const item of payload.keys ?? []) {
        if (typeof item.keyId !== "number" || typeof item.base64 !== "string") continue;
        try {
            const publicKey = createPublicKey({
                key: Buffer.from(item.base64, "base64"),
                format: "der",
                type: "spki",
            });
            byId.set(String(item.keyId), publicKey);
        } catch (error) {
            console.warn("Skipping malformed AdMob SSV public key", item.keyId, error);
        }
    }
    if (byId.size === 0) throw new Error("AdMob key server returned no usable SSV keys");
    cachedKeys = { byId, expiresAt: Date.now() + KEY_CACHE_TTL_MS };
    return byId;
}

function parseFields(rawQuery: string): SsvFields {
    const params = new URLSearchParams(rawQuery);
    return {
        ad_unit: params.get("ad_unit") ?? undefined,
        custom_data: params.get("custom_data") ?? undefined,
        key_id: params.get("key_id") ?? undefined,
        reward_amount: params.get("reward_amount") ?? undefined,
        reward_item: params.get("reward_item") ?? undefined,
        signature: params.get("signature") ?? undefined,
        timestamp: params.get("timestamp") ?? undefined,
        transaction_id: params.get("transaction_id") ?? undefined,
        user_id: params.get("user_id") ?? undefined,
    };
}

function uidLooksValid(uid: string): boolean {
    return uid.length > 0 && uid.length <= 128 && !uid.startsWith("$RCAnonymousID:");
}

/**
 * Public endpoint called only by AdMob after a rewarded ad completion. It grants a daily
 * bonus-like allowance only after signature, ad-unit, reward, Firebase-user and plan checks.
 */
// GÖZLEMLENEBİLİRLİK NOTU (kavramsal Cloud Monitoring eşiği, Gereksinim 7, Faz 6): bu
// webhook'un BAŞARISIZLIK oranı -- log tabanlı metrik: `severity=ERROR` VE
// `resource.labels.function_name="admobRewardedSsv"` (aşağıdaki `console.error` çağrıları:
// doğrulama anahtarları yüklenemedi, kullanıcı çözümlenemedi, ödül işleme başarısız) --
// TOPLAM istek sayısına oranla %5'i AŞARSA bir UYARI (alert) tetiklenmelidir. Bu webhook
// Google'ın SSV sunucuları tarafından OTOMATİK olarak TEKRAR denendiğinden (503 yanıtı
// üzerine), sürekli bir %5 üzeri hata oranı GERÇEK bir altyapı sorununa (anahtar önbelleği,
// Firestore kullanılamıyor vb.) işaret eder, geçici tek seferlik bir hataya DEĞİL.
export const admobRewardedSsv = onRequest(
    { region: "us-central1", timeoutSeconds: 20, cors: false },
    async (request, response) => {
        if (request.method !== "GET") {
            response.status(405).send("Method Not Allowed");
            return;
        }

        const requestUrl = request.originalUrl || request.url;
        const questionMark = requestUrl.indexOf("?");
        if (questionMark < 0) {
            response.status(400).send("Missing callback query");
            return;
        }
        // Preserve the raw query and parameter ordering: those exact bytes are signed by Google.
        const rawQuery = requestUrl.slice(questionMark + 1);
        const fields = parseFields(rawQuery);
        if (!fields.signature || !fields.key_id) {
            response.status(400).send("Missing signature");
            return;
        }

        let keys: KeySet["byId"];
        try {
            keys = await loadVerifierKeys();
            if (!keys.has(fields.key_id)) keys = await loadVerifierKeys(true);
        } catch (error) {
            console.error("Unable to load AdMob SSV verification keys", error);
            response.status(503).send("Verification keys unavailable");
            return;
        }
        if (!verifyAdMobSsvQuery(rawQuery, keys)) {
            response.status(401).send("Invalid AdMob signature");
            return;
        }

        const uid = fields.user_id ?? "";
        const transactionId = fields.transaction_id ?? "";
        // Gereksinim 2.9: hangi ödülün istendiğini `custom_data`dan çöz — REWARD_DEFINITIONS (rewards.ts)
        // tablosunda karşılığı olmayan (ör. bilinmeyen/gelecekteki bir yerleşim) her istek
        // güvenli biçimde YOK SAYILIR, asla varsayılan bir ödül vermez.
        const rewardKey = isRewardKey(fields.custom_data) ? fields.custom_data : null;
        const rewardConfig = rewardKey ? REWARD_DEFINITIONS[rewardKey] : null;
        if (
            !uidLooksValid(uid) ||
            !/^[A-Za-z0-9_-]{8,128}$/.test(transactionId) ||
            !rewardKey ||
            !rewardConfig ||
            fields.ad_unit !== REWARDED_AD_UNIT_SUFFIX ||
            !/^[1-9][0-9]{0,5}$/.test(fields.reward_amount ?? "")
        ) {
            // A valid signature for another placement/reward must not be retried forever.
            response.status(200).send("Ignored");
            return;
        }

        try {
            await admin.auth().getUser(uid);
        } catch (error) {
            const code = (error as { code?: string }).code;
            if (code === "auth/user-not-found") {
                response.status(200).send("Ignored");
                return;
            }
            console.error("Could not resolve Firebase user for AdMob SSV", { transactionId, error });
            response.status(503).send("User lookup unavailable");
            return;
        }

        try {
            const tier = await resolveTierFor(uid, { auth: null });
            // Gereksinim 2.5: Economy de Free gibi ödüllü reklamla ekstra hak kazanabilir —
            // üç ödül türü de (beğeni, "beni beğenenler", oda değişimi) aynı kademe kuralına tabidir.
            if (!REWARDED_ELIGIBLE_TIERS.has(tier)) {
                response.status(200).send("Ignored");
                return;
            }

            const zone = await userTimeZone(uid);
            const now = new Date();
            const today = dayKey(zone, now);
            const month = monthKey(zone, now);
            const usageRef = db.collection("usage").doc(uid);
            const eventRef = db.collection("admob_rewarded_events").doc(transactionId);
            let result: "granted" | "duplicate" | "daily-limit" = "granted";
            const usageField = rewardConfig.usageField;

            await db.runTransaction(async (transaction) => {
                const [eventSnapshot, usageSnapshot] = await Promise.all([
                    transaction.get(eventRef),
                    transaction.get(usageRef),
                ]);
                if (eventSnapshot.exists) {
                    result = "duplicate";
                    return;
                }

                const usage = resetUsage((usageSnapshot.data() ?? {}) as UsageData, today, month);
                // Gereksinim 2.9: SUNUCUNUN uyguladığı KATI (hard) günlük tavan — istemci
                // (ör. modifiye edilmiş bir istemci) bu kontrolü atlayıp doğrudan reklamı
                // izlese ve SSV'yi tetiklese dahi, tavan aşıldıysa ödül BURADA reddedilir.
                const currentCount = Number(usage[usageField] ?? 0);
                // Tek kaynak kural: bir reklam ödülü ya TAMAMEN verilir ya hiç (bkz. planRewardGrant).
                const plan = planRewardGrant(currentCount, rewardConfig);
                if (!plan.granted) {
                    result = "daily-limit";
                    transaction.create(eventRef, {
                        uid,
                        adUnit: fields.ad_unit,
                        rewardItem: fields.reward_item,
                        rewardKey,
                        rewardAmount: 0,
                        rewardedAt: Number(fields.timestamp) || Date.now(),
                        status: "ignored_daily_limit",
                        processedAt: FieldValue.serverTimestamp(),
                    });
                    return;
                }

                transaction.set(usageRef, { ...usage, [usageField]: plan.nextCount }, { merge: true });
                transaction.create(eventRef, {
                    uid,
                    adUnit: fields.ad_unit,
                    rewardItem: fields.reward_item,
                    rewardKey,
                    rewardAmount: rewardConfig.grantPerAd,
                    rewardedAt: Number(fields.timestamp) || Date.now(),
                    status: "processed",
                    processedAt: FieldValue.serverTimestamp(),
                });
            });

            response.status(200).send(result === "granted" ? "Reward granted" : result === "duplicate" ? "Already processed" : "Daily reward limit reached");
        } catch (error) {
            console.error("AdMob SSV reward processing failed", { transactionId, uid, error });
            response.status(503).send("Retry reward processing");
        }
    },
);
