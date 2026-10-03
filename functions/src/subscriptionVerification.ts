import { onCall, HttpsError } from "firebase-functions/v2/https";
import type { Tier } from "./monetization";
import { REVENUECAT_API_KEY, RevenueCatUnavailableError, syncCustomerEntitlements } from "./revenuecatSync";

/**
 * Immediate Authorization Fallback (Altyapı Gereksinimi): bir satın almanın HEMEN ardından,
 * RevenueCat'in ASENKRON webhook'u henüz gelmemişken sunucu eylemlerinin yanlışlıkla
 * reddedilmemesi için istemcinin (`SubscriptionRepository.verifyEntitlementNow`,
 * `EntitlementFallbackVerifier`) SON ÇARE olarak çağırdığı ANINDA doğrulama.
 *
 * Webhook ile AYNI birleşik senkronizasyon adımını (`syncCustomerEntitlements`) kullanır:
 * RevenueCat REST API'sinden mutlak güncel durumu okur, `customers/{uid}` belgesini ve
 * Firebase Auth özel talebini günceller, kademe düştüyse fazla odaları/Boost'u sunucuda
 * temizler. Gizli API anahtarı YALNIZCA sunucuda yaşar, istemciye ASLA gömülmez.
 */
export const verifyEntitlementNow = onCall(
    // Gereksinim 2 (Faz 6, Kapsamlı App Check Uygulaması): "highly sensitive operation"
    // (bkz. görev tanımı: "purchase validation") -- standart (önbelleğe alınabilen,
    // TEKRAR kullanılabilen) bir App Check jetonu YETERLİ DEĞİLDİR. `consumeAppCheckToken:
    // true`, istemcinin (bkz. `LimitedUseAppCheckCallableInvoker.android.kt`) gönderdiği
    // TEK KULLANIMLIK jetonu framework düzeyinde doğrular VE tüketir -- AYNI jeton
    // İKİNCİ kez ASLA kabul edilmez (tekrar oynatma/replay saldırısına karşı savunma).
    // Manuel bir `admin.appCheck().verifyToken()` çağrısına GEREK YOKTUR: bu ikili seçenek
    // (`enforceAppCheck` + `consumeAppCheckToken`) framework'ün KENDİSİ tarafından ele alınır.
    { region: "us-central1", enforceAppCheck: true, consumeAppCheckToken: true, secrets: [REVENUECAT_API_KEY] },
    async (request): Promise<{ tier: Tier }> => {
        const uid = request.auth?.uid;
        if (!uid) throw new HttpsError("unauthenticated", "Authentication required.");

        try {
            const result = await syncCustomerEntitlements(uid, REVENUECAT_API_KEY.value(), "verifyEntitlementNow");
            return { tier: result.tier };
        } catch (error) {
            if (error instanceof RevenueCatUnavailableError) {
                throw new HttpsError("unavailable", "RevenueCat verification failed.");
            }
            throw error;
        }
    },
);
