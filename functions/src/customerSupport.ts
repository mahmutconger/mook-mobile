import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

// AYNI desen: `deleteAccount.ts` / `subscriptionVerification.ts` -- RevenueCat gizli (secret)
// API anahtarı koda gömülmez, yalnızca Firebase Secret Manager üzerinden okunur.
const REVENUECAT_API_KEY = defineSecret("REVENUECAT_API_KEY");

// RevenueCat'in Promosyonel Hak (Promotional Entitlement) REST API'sinin (`POST
// /v1/subscribers/{app_user_id}/entitlements/{entitlement_identifier}/promotional`) kabul
// ettiği TEK doğruluk kaynağı olan `duration` değerleri.
const VALID_DURATIONS = new Set([
    "daily",
    "weekly",
    "monthly",
    "three_month",
    "six_month",
    "yearly",
    "lifetime",
]);

// `monetization.ts`teki `tierFromEntitlements` ile AYNI küçük harfli hak (entitlement)
// tanımlayıcıları -- yalnızca BU üç değer RevenueCat panelinde gerçekten TANIMLIDIR; rastgele
// bir dize RevenueCat'te sessizce YENİ, tanımsız bir hak oluşturabileceğinden beyaz listeyle
// (whitelist) sınırlanır.
const VALID_ENTITLEMENTS = new Set(["economy", "standard", "premium"]);

/**
 * Gereksinim 7 (Faz 6, Gözlemlenebilirlik & Destek Araçları): yetkili bir müşteri desteği
 * temsilcisi/yöneticisi, bir HATA (bug) YAŞAYAN kullanıcıya RevenueCat'in KENDİ REST API'si
 * üzerinden GEÇİCİ bir promosyonel hak (ör. 7 günlük Premium) tanır -- ödeme YAPILMADAN,
 * mağaza (Play/App Store) faturalandırmasını hiç TETİKLEMEDEN.
 *
 * YETKİLENDİRME: `moderateUser`/`bootstrapMonetization` (bkz. `moderation.ts`/`monetization.ts`)
 * ile BİREBİR AYNI desen -- yalnızca özel `admin: true` Firebase Auth claim'ine sahip
 * çağıranlar kullanabilir; istemci taraflı HİÇBİR rol bayrağına GÜVENİLMEZ. Bu claim bu
 * fonksiyonun KAPSAMI DIŞINDA, güvenilir bir destek yöneticisine Firebase Console/Admin SDK
 * betiği üzerinden manuel olarak verilir (`admin.auth().setCustomUserClaims(uid, { admin:
 * true })`).
 *
 * DENETİM İZİ (audit trail): her başarılı bağış `support_grants` koleksiyonuna, HANGİ
 * yöneticinin (`adminUid`), HANGİ kullanıcıya (`targetUid`), NE ZAMAN ve NEDEN (`reason` --
 * destek bileti/hata açıklaması) bir hak tanıdığını KALICI olarak kaydeder -- bu hem kötüye
 * kullanımı caydırır hem de "bu kullanıcıya ne zaman/kim tarafından/neden promosyon tanındı?"
 * sorularını yanıtlanabilir kılar. Bu kayıt ÖLÜMCÜL DEĞİLDİR: RevenueCat'e bağış zaten
 * BAŞARILI olduysa, yalnızca logun kendisinin yazılamaması işlemi GERİ ALMAZ.
 *
 * GÖZLEMLENEBİLİRLİK NOTU (kavramsal Cloud Monitoring eşikleri, Gereksinim 7): bu callable'ın
 * hata ORANI (log tabanlı metrik: `severity=ERROR` VE
 * `resource.labels.function_name="grantPromotionalEntitlement"`) %5'i AŞARSA (RevenueCat API
 * kesintisi/anahtar sorunu ihtimaline karşı) bir UYARI (alert) tetiklenmelidir -- bkz.
 * `admobRewardedSsv.ts`teki AYNI %5 eşik deseni. Ayrıca günlük bağış SAYISI beklenmedik
 * şekilde SIÇRARSA (ör. > 50/gün -- tek bir destek ekibi için normalin ÇOK üzerinde), bu
 * KÖTÜYE KULLANIM veya SIZMIŞ bir `admin` claim belirtisi olabilir; `support_grants`
 * koleksiyonu üzerinde ayrı bir günlük sayaç metriği + eşik olarak izlenmelidir.
 */
export const grantPromotionalEntitlement = onCall(
    { region: "us-central1", enforceAppCheck: true, secrets: [REVENUECAT_API_KEY] },
    async (request) => {
        const adminUid = request.auth?.uid;
        if (!adminUid) {
            throw new HttpsError("unauthenticated", "Bu işlem için giriş yapmış olmanız gerekir.");
        }
        if (request.auth?.token?.admin !== true) {
            throw new HttpsError("permission-denied", "admin-only");
        }

        const data = request.data as {
            targetUid?: unknown;
            entitlementIdentifier?: unknown;
            duration?: unknown;
            reason?: unknown;
        };
        if (typeof data.targetUid !== "string" || !data.targetUid) {
            throw new HttpsError("invalid-argument", "Geçersiz hedef kullanıcı kimliği.");
        }
        if (typeof data.entitlementIdentifier !== "string" || !VALID_ENTITLEMENTS.has(data.entitlementIdentifier)) {
            throw new HttpsError("invalid-argument", "Geçersiz hak (entitlement) tanımlayıcısı.");
        }
        if (typeof data.duration !== "string" || !VALID_DURATIONS.has(data.duration)) {
            throw new HttpsError("invalid-argument", "Geçersiz süre.");
        }
        const reason = typeof data.reason === "string" && data.reason.trim().length > 0
            ? data.reason.trim()
            : "unspecified-support-grant";

        const targetUid = data.targetUid;
        const entitlementIdentifier = data.entitlementIdentifier;
        const duration = data.duration;

        const response = await fetch(
            `https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(targetUid)}` +
                `/entitlements/${encodeURIComponent(entitlementIdentifier)}/promotional`,
            {
                method: "POST",
                headers: {
                    Authorization: `Bearer ${REVENUECAT_API_KEY.value()}`,
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({ duration }),
            },
        );

        if (!response.ok) {
            console.error(
                `[grantPromotionalEntitlement] RevenueCat isteği başarısız oldu ` +
                    `(status=${response.status}, adminUid=${adminUid}, targetUid=${targetUid}).`,
            );
            throw new HttpsError("internal", "Promosyonel hak tanımlanırken bir sorun oluştu.");
        }

        // Denetim izi -- bkz. fonksiyon KDoc'u: ölümcül DEĞİLDİR.
        try {
            await db.collection("support_grants").add({
                adminUid,
                targetUid,
                entitlementIdentifier,
                duration,
                reason,
                grantedAt: Date.now(),
            });
        } catch (error) {
            console.error("[grantPromotionalEntitlement] Denetim izi kaydı başarısız:", error);
        }

        return { success: true };
    },
);
