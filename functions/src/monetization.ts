import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { assertNotBanned } from "./moderation";
import {
    MAX_FREE_REWARDED_LIKES,
    MAX_REWARDED_ROOM_SWITCHES,
    REWARDED_ELIGIBLE_TIERS,
    REWARD_DEFINITIONS,
    adsRemainingToday,
    isRewardKey,
    planLikedMeUnlock,
    planRewardGrant,
} from "./rewards";
import { DAILY_ROOM_SWITCH_LIMIT_ERROR, RoomSwitchLimitError, planRoomSwitch, type RoomSwitchPlan } from "./roomSwitch";
import { MessageQuotaError, planMessageQuota } from "./messageQuota";
import { PLAN_DEFAULTS, serializablePlans, type PlanLimits } from "./planDefaults";
import { addPublicProfileWrites, deletePublicProfile, writePublicProfile } from "./publicProfileSync";
import { readAbuseFlag, writeAbuseFlag } from "./abuseFlags";
import { decodeEntryToken, deriveTokenKey } from "./likedMePlan";
import { defineSecret } from "firebase-functions/params";

/** Bkz. `likedMe.ts` — "Beni Beğenenler" giriş jetonlarının şifreleme anahtarı. */
const LIKED_ME_TOKEN_SECRET = defineSecret("LIKED_ME_TOKEN_SECRET");
import { isTierName } from "./tierPolicy";

// Firebase loads imported modules before executing index.ts. Initializing here keeps
// deploy-time function discovery and every exported entry point safe regardless of
// import order.
if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

export type Tier = "FREE" | "ECONOMY" | "STANDARD" | "PREMIUM";


const FAIR_DAILY_LIKES = 1000;
const MESSAGE_RATE_LIMIT = 30;
const MESSAGE_RATE_WINDOW_MS = 60_000;
// Gereksinim 1.14: en fazla bu kadar geçilen profil kimliği yığında (stack) tutulur — pratikte günlük rewind tavanı (en fazla birkaç) bunun çok altında kaldığından bu yalnızca aşırı aktif bir günde belgenin sınırsız büyümesine karşı bir güvenlik sınırıdır.
const MAX_PASS_STACK = 20;
// Ödüllü reklam ekonomisi sabitleri tek kaynaktan (rewards.ts) gelir ve geriye dönük uyumluluk
// için buradan yeniden dışa aktarılır (admobRewardedSsv.ts vb. bu adlarla içe aktarır).
export {
    MAX_FREE_REWARDED_LIKES,
    MAX_REWARDED_LIKED_ME_UNLOCKS,
    MAX_REWARDED_ROOM_SWITCHES,
    REWARDED_ELIGIBLE_TIERS,
} from "./rewards";

/**
 * Gereksinim 2.2: kademe başına günlük/aylık karakter tabanlı çeviri kotası.
 *
 * İstemcideki `CharacterQuotaManager.kt` / `DefaultCharacterQuotas` ile BİLİNÇLİ olarak
 * birebir aynı tutulur (istemci yalnızca erken/iyimser bir tahmin yapar; asıl yaptırım
 * burada, `consumeMessageQuota` içindeki atomik transaction'dadır). Eskiden sınırsız
 * mesaj sayısına sahip kademeler (STANDARD/PREMIUM) için düz bir "2.000 mesaj/gün"
 * adil kullanım tavanı vardı — bu, asıl maliyeti (DeepL karakter ücreti) değil, mesaj
 * SAYISINI sınırlıyordu. Artık her kademe, harcadığı gerçek karakter hacmine göre
 * sınırlanır.
 *
 * Yalnızca Çeviri Kotası Mantığı: bu kota YALNIZCA DeepL'e giden (gerçekten çevrilen)
 * mesajlarda düşülür. Aynı dildeki mesajlar kotaya hiç dokunmaz; kota bittiğinde mesajlar
 * engellenmez, çevrilmeden gönderilir.
 */
export const CHARACTER_QUOTA_LIMITS: Record<Tier, { dailyChars: number; monthlyChars: number }> = {
    FREE: { dailyChars: 1_000, monthlyChars: 10_000 },
    ECONOMY: { dailyChars: 2_500, monthlyChars: 25_000 },
    STANDARD: { dailyChars: 5_000, monthlyChars: 50_000 },
    PREMIUM: { dailyChars: 10_000, monthlyChars: 100_000 },
};


function requireUid(request: { auth?: { uid?: string } | null }): string {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in before using this feature.");
    return uid;
}

export function tierFromEntitlements(value: unknown): Tier {
    const names = new Set<string>();
    if (Array.isArray(value)) value.forEach((entry) => names.add(String(entry).toLowerCase()));
    else if (typeof value === "string") names.add(value.toLowerCase());
    else if (value && typeof value === "object") {
        Object.entries(value as Record<string, unknown>).forEach(([key, enabled]) => {
            if (enabled) names.add(key.toLowerCase());
        });
    }
    if (names.has("premium")) return "PREMIUM";
    if (names.has("standard")) return "STANDARD";
    if (names.has("economy")) return "ECONOMY";
    return "FREE";
}

export function resolveTier(request: { auth?: { token?: Record<string, unknown> } | null }): Tier {
    return tierFromEntitlements(request.auth?.token?.revenueCatEntitlements);
}

/**
 * Sunucu eylemleri için kullanıcının GÜNCEL kademesini çözer.
 *
 * RevenueCat REST API Doğrulama Kuralı: `customers/{uid}.syncedTier`, RevenueCat REST
 * API'sinden (webhook veya `verifyEntitlementNow` ile) okunmuş mutlak durumdur ve ÖNCELİKLİDİR.
 * Firebase ID token'ındaki özel talep bir saate kadar bayat kalabilir — bir kademe düşüşünden
 * sonra eski token ile ücretli özelliklerin kullanılmaya devam etmesini önlemek için talep
 * yalnızca henüz senkronize edilmemiş (eski) hesaplarda yedek olarak kullanılır.
 */
export async function resolveTierFor(uid: string, request: { auth?: { token?: Record<string, unknown> } | null }): Promise<Tier> {
    const customer = await db.collection("customers").doc(uid).get();
    const data = customer.data();
    if (data && isTierName(data.syncedTier) && typeof data.lastSyncedAt === "number") return data.syncedTier;
    const claimed = resolveTier(request);
    if (claimed !== "FREE") return claimed;
    return tierFromEntitlements(data?.revenueCatEntitlements ?? data?.entitlements ?? data?.activeEntitlements);
}

export async function limitsFor(tier: Tier): Promise<PlanLimits> {
    const document = await db.collection("config").doc("plans").get();
    const raw = document.data()?.[tier.toLowerCase()] as Partial<PlanLimits> | undefined;
    return { ...PLAN_DEFAULTS[tier], ...raw };
}

export function dayKey(timeZone: string, now = new Date()): string {
    try {
        return new Intl.DateTimeFormat("en-CA", {
            timeZone, year: "numeric", month: "2-digit", day: "2-digit",
        }).format(now);
    } catch {
        return new Intl.DateTimeFormat("en-CA", {
            timeZone: "Europe/Istanbul", year: "numeric", month: "2-digit", day: "2-digit",
        }).format(now);
    }
}

export function monthKey(timeZone: string, now = new Date()): string {
    try {
        return new Intl.DateTimeFormat("en-CA", {
            timeZone, year: "numeric", month: "2-digit",
        }).format(now);
    } catch {
        return new Intl.DateTimeFormat("en-CA", {
            timeZone: "Europe/Istanbul", year: "numeric", month: "2-digit",
        }).format(now);
    }
}

export interface UsageData {
    day?: string;
    month?: string;
    likes?: number;
    /** Lifetime successful likes; deliberately survives daily quota resets. */
    likesEver?: number;
    messages?: number;
    newChats?: number;
    roomSwitches?: number;
    rewardedLikes?: number;
    likedMeUnlocks?: number;
    /**
     * Gereksinim 2.9 (Faz 4): bugün ödüllü reklam izlenerek AdMob SSV ile kazanılmış ekstra
     * "beni beğenenler" açma sayısı — [likedMeUnlocks] (ücretli plan hakkının TÜKETİMİ) ile
     * KARIŞTIRILMAMALIDIR; bu alan yalnızca ödül SAYACIDIR (bkz. `admobRewardedSsv.ts`).
     */
    rewardedLikedMeUnlocks?: number;
    /**
     * Gereksinim 2.9 (Faz 4): bugün ödüllü reklam izlenerek AdMob SSV ile kazanılmış ekstra
     * oda değişimi sayısı — [roomSwitches] (günlük plan hakkının TÜKETİMİ) ile
     * KARIŞTIRILMAMALIDIR; bu alan yalnızca ödül SAYACIDIR (bkz. `admobRewardedSsv.ts`).
     */
    rewardedRoomSwitches?: number;
    rewinds?: number;
    boosts?: number;
    messageWindowStart?: number;
    messageWindowCount?: number;
    /** @deprecated Gereksinim 1.14 ile `passStack`'e taşındı; yalnızca geriye dönük uyumluluk için okunur. */
    lastPass?: string;
    /** @deprecated Gereksinim 1.14 ile `passStack`'e taşındı; yalnızca geriye dönük uyumluluk için okunur. */
    lastPassDay?: string;
    /**
     * Gereksinim 1.14: bu oturumda (günlük sıfırlamaya kadar) geçilen (pas geçilen)
     * profillerin, en eskiden en yeniye sıralı kimlik yığını (stack). `rewind` dizinin
     * SONUNDAN (en son geçilen) kaldırır — böylece günlük kota izin verdiği sürece art
     * arda birden fazla geri alma, her seferinde bir önceki geçmeyi geri getirir. Eskiden
     * tek bir `lastPass` alanı vardı; bu, art arda iki geçmeden sonra yalnızca SONUNCUSUNUN
     * geri alınabilmesine, ilkinin kalıcı olarak kaybolmasına neden oluyordu.
     */
    passStack?: string[];
    /**
     * Gereksinim 2.2: bu takvim günü içinde GERÇEKTEN ÇEVRİLEN (DeepL'e giden) mesajların
     * toplam karakter sayısı. Aynı dildeki mesajlar bu sayaca eklenmez. `messages` alanı
     * SAYIYI sayar, bu alan ise çeviri HACMİNİ sayar (bkz. `consumeMessageQuota`).
     */
    translationCharsDaily?: number;
    /** Gereksinim 2.2: aynı sayaç, takvim ayı bazında (bkz. [translationCharsDaily]). */
    translationCharsMonthly?: number;
}

export function resetUsage(data: UsageData, day: string, month: string): UsageData {
    const daily = data.day === day ? data : {
        ...data,
        day, likes: 0, messages: 0, newChats: 0, roomSwitches: 0,
        rewardedLikes: 0, likedMeUnlocks: 0, rewinds: 0,
        // Gereksinim 2.9: ödüllü-reklam SAYAÇLARI da gün değiştiğinde sıfırlanır — `rewardedLikes`
        // ile aynı desen.
        rewardedLikedMeUnlocks: 0, rewardedRoomSwitches: 0,
        messageWindowStart: 0, messageWindowCount: 0,
        // Gereksinim 2.2: karakter kotası da günlük/aylık pencerelere göre sıfırlanır —
        // `messages` ile aynı "gün değiştiyse sıfırla" tembel sıfırlama deseni.
        translationCharsDaily: 0,
    };
    // Gereksinim 2.12 (Faz 4): `boosts` artık burada SıFIRLANMAZ -- takvim ayı bir Boost
    // kotası için yanlış sınırdır (kullanıcının GERÇEK faturalandırma döngüsü, aynı
    // takvim ayı içinde başlayıp bitmeyebilir). Asil sıfırlama artık RevenueCat'in
    // "RENEWAL"/"INITIAL_PURCHASE" webhook olayıyla, kullanıcının KENDİ abonelik
    // yıldönümünde tetiklenir (bkz. `revenuecatWebhook.ts`) -- bu, `BoostManagerUseCase`'in
    // istemci tarafındaki KDoc'unda da açıklanan "takvim ayı değil, faturalandırma
    // döngüsü" ilkesinin sunucu tarafındaki karşılığıdır.
    return daily.month === month
        ? daily
        : { ...daily, month, translationCharsMonthly: 0 };
}

export async function userTimeZone(uid: string): Promise<string> {
    const user = await db.collection("users").doc(uid).get();
    const zone = user.data()?.timeZone;
    return typeof zone === "string" && zone.length <= 64 ? zone : "Europe/Istanbul";
}


/**
 * Alıcının günlük mesaj kotasını, kotayı DEĞİŞTİRMEDEN salt okunur şekilde kontrol eder.
 *
 * `sendMessage` bunu mesaj başarıyla kaydedildikten SONRA çağırır (Gereksinim 1.6):
 * örneğin Premium bir kullanıcı, günlük mesaj limitini doldurmuş Free bir kullanıcıya
 * yazdığında, gönderen taraf mesajını yine de gönderebilir — sadece istemci tarafında
 * "Bu kullanıcı şu anda yanıt veremeyebilir" bilgilendirmesi gösterilir. Bu kontrol
 * `consumeMessageQuota`'nın aksine hiçbir sayaç artırmaz ve mesajın gönderilmesini
 * asla engellemez; salt bilgi amaçlıdır.
 */
export async function isRecipientAtDailyMessageLimit(peerUid: string): Promise<boolean> {
    const tier = await resolveTierFor(peerUid, {});
    const limits = await limitsFor(tier);
    const zone = await userTimeZone(peerUid);
    const today = dayKey(zone);
    const snapshot = await db.collection("usage").doc(peerUid).get();
    const usage = (snapshot.data() ?? {}) as UsageData;
    // Gün değiştiyse sayaç henüz sıfırlanmamış olabilir (tembel sıfırlama) — o durumda
    // alıcının kullanımı fiilen 0'dır, limite ulaşmış olamaz.
    if (usage.day !== today) return false;
    // Gereksinim 2.2: mesaj SAYISI tavanı olmayan kademeler (STANDARD/PREMIUM, yani
    // `dailyMessages === null`) artık düz bir "adil kullanım" sayısına da düşürülmez —
    // karakter kotası ise yalnızca çeviriyi sınırlar, mesajı değil (bkz. `consumeMessageQuota`). Bu fonksiyon
    // yalnızca SAYI tabanlı tavanı olan kademeler (FREE/ECONOMY) için anlamlıdır.
    if (limits.dailyMessages === null) return false;
    return Number(usage.messages ?? 0) >= limits.dailyMessages;
}

export interface MessageQuotaDecision {
    /** DeepL çağrılmalı mı? `true` ise karakterler kotadan düşülmüştür. */
    translate: boolean;
    /** Çeviri gerekiyordu ama karakter kotası yetmedi; mesaj çevrilmeden gönderilir. */
    translationQuotaExhausted: boolean;
}

/**
 * Yalnızca Çeviri Kotası Mantığı: mesaj sayısı, yeni sohbet ve dakikalık hız sınırlarını her
 * mesaj için atomik olarak uygular; karakter kotasını ise YALNIZCA gerçekten çevrilecek
 * mesajlarda kontrol edip düşer.
 *
 * [translationChars]: DeepL'e gidecek karakter sayısı. Aynı dildeki (veya desteklenmeyen
 * hedef dile giden) mesajlarda `sendMessage` 0 geçirir — bu mesajlar karakter kotasına HİÇ
 * dokunmaz. Kota yetmezse mesaj engellenmez; çeviri atlanır ve hiçbir karakter düşülmez
 * (bkz. `messageQuota.ts`, `planMessageQuota`).
 */
export async function consumeMessageQuota(
    uid: string,
    tier: Tier,
    isNewChat: boolean,
    translationChars: number,
): Promise<MessageQuotaDecision> {
    const limits = await limitsFor(tier);
    const characterLimits = CHARACTER_QUOTA_LIMITS[tier];
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);
    const reference = db.collection("usage").doc(uid);
    return db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(reference);
        const usage = resetUsage((snapshot.data() ?? {}) as UsageData, day, month);
        let plan;
        try {
            plan = planMessageQuota({
                usage,
                dailyMessages: limits.dailyMessages,
                dailyNewChats: limits.dailyNewChats,
                dailyChars: characterLimits.dailyChars,
                monthlyChars: characterLimits.monthlyChars,
                isNewChat,
                translationChars,
                now: Date.now(),
                rateLimit: MESSAGE_RATE_LIMIT,
                rateWindowMs: MESSAGE_RATE_WINDOW_MS,
            });
        } catch (error) {
            if (error instanceof MessageQuotaError) throw new HttpsError("resource-exhausted", error.code);
            throw error;
        }
        transaction.set(reference, { ...usage, ...plan.counters }, { merge: true });
        return { translate: plan.translate, translationQuotaExhausted: plan.translationQuotaExhausted };
    });
}

function orderedPair(a: string, b: string): string {
    return a < b ? `${a}_${b}` : `${b}_${a}`;
}

// Gereksinim 2 (Faz 6, Kapsamlı App Check Uygulaması): bu dosyadaki TÜM istemci-
// çağrımlı `onCall` fonksiyonları artık `enforceAppCheck: true` taşır -- yalnızca
// geçerli bir Play Integrity (Android) ekli isteklerin sunucuya ULAŞMASINA izin
// verilir. (`admobRewardedSsv.ts` bu kapsamın DIŞINDADIR: Google'ın reklam
// sunucuları tarafından DOĞRUDAN çağrılan bir webhook'tur, istemci SDK'sı YOKTUR --
// bkz. o dosyanın başındaki not. `migrateExistingUsers.ts` da KASITLI olarak
// DIŞARIDA bırakıldı: tek seferlik bir operasyon betiğidir, bkz. o dosyadaki not.)
//
// Gereksinim 1 (Faz 6): en sık çağrılan callable -- Discover ekranındaki her kaydırma
// bunu tetikler. `minInstances: 1`, Cloud Functions'ın konteyneri her çağrıda sıfırdan
// başlatmasını (soğuk başlangıç -- genellikle 1-3 saniye ekstra gecikme) önler; sürekli
// sıcak TUTULAN bir örnek küçük bir sabit maliyet karşılığında kullanıcı algısındaki
// gecikmeyi ortadan kaldırır. İstemci tarafında da [BillingConfig.SWIPE_TIMEOUT_MILLIS]
// (5 sn) ile bir zaman aşımı GÜVENCESİ vardır -- bu ikisi birbirini TAMAMLAR, birbirinin
// yerini tutmaz.
export const swipe = onCall(
    { region: "us-central1", enforceAppCheck: true, minInstances: 1 },
    async (request) => {
    const uid = requireUid(request);
    // Gereksinim 1.13: yasaklı bir kullanıcı bu callable'a istemci taraflı engelleme
    // atlatılarak ulaşsa bile burada durdurulur (bkz. `assertNotBanned` KDoc'u).
    await assertNotBanned(uid);
    const data = request.data as { toUserId?: unknown; isLike?: unknown };
    if (typeof data.toUserId !== "string" || !data.toUserId || data.toUserId === uid || typeof data.isLike !== "boolean") {
        throw new HttpsError("invalid-argument", "Invalid swipe request.");
    }
    const targetUid = data.toUserId;
    const isLike = data.isLike;
    const tier = await resolveTierFor(uid, request);
    const limits = await limitsFor(tier);
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);
    const interactionRef = db.collection("interactions").doc(`${uid}_${targetUid}`);
    const reverseRef = db.collection("interactions").doc(`${targetUid}_${uid}`);
    const matchRef = db.collection("matches").doc(orderedPair(uid, targetUid));
    const usageRef = db.collection("usage").doc(uid);
    const ownUserRef = db.collection("users").doc(uid);
    const targetUserRef = db.collection("users").doc(targetUid);

    return db.runTransaction(async (transaction) => {
        const [existing, reverse, usageSnapshot, ownUser, targetUser] = await Promise.all([
            transaction.get(interactionRef), transaction.get(reverseRef), transaction.get(usageRef),
            transaction.get(ownUserRef), transaction.get(targetUserRef),
        ]);
        if (!targetUser.exists) throw new HttpsError("not-found", "Profile not found.");
        const ownBlocked = (ownUser.data()?.blockedUsers ?? []) as string[];
        const targetBlocked = (targetUser.data()?.blockedUsers ?? []) as string[];
        if (ownBlocked.includes(targetUid) || targetBlocked.includes(uid)) {
            throw new HttpsError("permission-denied", "blocked-user");
        }
        if (existing.exists) {
            const wasLike = existing.data()?.type === "like";
            const mutual = wasLike && reverse.data()?.type === "like";
            return { result: mutual ? "mutual_match" : wasLike ? "single_like" : "pass", idempotent: true };
        }

        const usage = resetUsage((usageSnapshot.data() ?? {}) as UsageData, day, month);
        if (isLike) {
            const limit = limits.dailyLikes ?? FAIR_DAILY_LIKES;
            const effectiveLimit = limit + Math.min(usage.rewardedLikes ?? 0, MAX_FREE_REWARDED_LIKES);
            if ((usage.likes ?? 0) >= effectiveLimit) {
                throw new HttpsError("resource-exhausted", "daily-like-limit");
            }
            usage.likes = (usage.likes ?? 0) + 1;
            usage.likesEver = (usage.likesEver ?? 0) + 1;
            transaction.set(usageRef, usage, { merge: true });
        }

        transaction.set(interactionRef, {
            fromUserId: uid, toUserId: targetUid, type: isLike ? "like" : "pass",
            timestamp: Date.now(), day,
        });
        if (!isLike) {
            // Gereksinim 1.14: eski tekil `lastPass` yerine bir yığına (stack) EKLE. Eski
            // formattaki bir belgeden geliniyorsa (`passStack` yok ama `lastPass` var), o
            // tekil değer yığının ilk elemanı sayılarak geçiş sorunsuz tamamlanır.
            const existingStack = usage.passStack ?? (usage.lastPass ? [usage.lastPass] : []);
            const passStack = [...existingStack, targetUid].slice(-MAX_PASS_STACK);
            transaction.set(usageRef, {
                ...usage,
                passStack,
                lastPass: FieldValue.delete(),
                lastPassDay: FieldValue.delete(),
            }, { merge: true });
            return { result: "pass", idempotent: false };
        }
        if (reverse.data()?.type === "like") {
            // Gereksinim 1.7: eşleşme, o an kaydırmayı tamamlayan tarafın (uid) hangi
            // odada olduğuyla etiketlenir — bu oda daha sonra bir kademe düşüşünde
            // kapatılırsa, `sendMessage` bu değeri sohbet dokümanına kopyalar ve her iki
            // taraf da KENDİ açık oda listesine göre bu sohbetin salt-okunur olup
            // olmadığına karar verir (bkz. `RoomSlotRepository`/`ChatRoom.roomLanguageCode`).
            const roomLanguageCode = typeof ownUser.data()?.roomLanguageCode === "string"
                ? ownUser.data()!.roomLanguageCode
                : null;
            transaction.set(matchRef, {
                users: [uid, targetUid].sort(),
                timestamp: Date.now(),
                roomLanguageCode,
            }, { merge: true });
            if (targetUser.data()?.incognito === true) {
                transaction.set(targetUserRef, { visibleTo: FieldValue.arrayUnion(uid) }, { merge: true });
            }
            return { result: "mutual_match", idempotent: false };
        }
        if (targetUser.data()?.incognito === true) {
            transaction.set(targetUserRef, { visibleTo: FieldValue.arrayUnion(uid) }, { merge: true });
        }
        return { result: "single_like", idempotent: false };
    });
});

export const switchRoom = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const code = (request.data as { languageCode?: unknown }).languageCode;
    if (typeof code !== "string" || !/^[A-Za-z-]{2,16}$/.test(code)) {
        throw new HttpsError("invalid-argument", "Invalid language code.");
    }
    const tier = await resolveTierFor(uid, request);
    const limits = await limitsFor(tier);
    const userRef = db.collection("users").doc(uid);
    const usageRef = db.collection("usage").doc(uid);
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);

    // Slot seçimi, günlük hak düşümü ve aktif oda TEK bir transaction'da değişir; karar
    // `planRoomSwitch` (saf, birim testli) tarafından verilir. Slotlar doluyken yeni bir odaya
    // geçiş artık `room-slot-limit` hatası vermez: en uzun süredir kullanılmayan oda atomik
    // olarak yenisiyle takas edilir (bkz. `roomSwitch.ts` kuralları).
    return db.runTransaction(async (transaction) => {
        const [current, usageSnapshot] = await Promise.all([
            transaction.get(userRef), transaction.get(usageRef),
        ]);
        const user = current.data() ?? {};
        const rooms = Array.isArray(user.roomLanguageCodes)
            ? user.roomLanguageCodes.filter((room): room is string => typeof room === "string")
            : [user.roomLanguageCode].filter((room): room is string => typeof room === "string");
        const lastActiveAt = (typeof user.roomLastActiveAt === "object" && user.roomLastActiveAt !== null
            ? user.roomLastActiveAt
            : {}) as Record<string, number>;
        const usage = resetUsage((usageSnapshot.data() ?? {}) as UsageData, day, month);

        // Ödüllü reklamla (AdMob SSV) kazanılan ek oda değiştirme hakkı, uygun kademelerde
        // günlük tabana eklenir — istemcideki `BillingConfig.effectiveDailyRoomSwitchLimit` ile aynı.
        const dailySwitchLimit = limits.roomSwitchesPerDay === null
            ? null
            : limits.roomSwitchesPerDay + (REWARDED_ELIGIBLE_TIERS.has(tier)
                ? Math.min(Number(usage.rewardedRoomSwitches ?? 0), MAX_REWARDED_ROOM_SWITCHES)
                : 0);

        let plan: RoomSwitchPlan;
        try {
            plan = planRoomSwitch({
                rooms,
                activeRoom: typeof user.roomLanguageCode === "string" ? user.roomLanguageCode : null,
                lastActiveAt,
                target: code,
                roomSlots: limits.roomSlots,
                switchesUsedToday: Number(usage.roomSwitches ?? 0),
                dailySwitchLimit,
            });
        } catch (error) {
            if (error instanceof RoomSwitchLimitError) {
                throw new HttpsError("resource-exhausted", DAILY_ROOM_SWITCH_LIMIT_ERROR);
            }
            throw error;
        }
        if (plan.kind === "noop") return { roomLanguageCodes: plan.rooms, activeRoom: plan.activeRoom, evictedRooms: [] };

        if (plan.chargesQuota) {
            usage.roomSwitches = Number(usage.roomSwitches ?? 0) + 1;
            transaction.set(usageRef, usage, { merge: true });
        }

        // Gereksinim 1.7: her odanın en son ne zaman AKTİF yapıldığı tutulur (takasta ve kademe
        // düşüşünde "en eski kullanılan" kararı buna dayanır). Çıkarılan odaların kaydı silinir.
        const roomLastActiveAt: Record<string, number> = { ...lastActiveAt, [code]: Date.now() };
        plan.evictedRooms.forEach((room) => { delete roomLastActiveAt[room]; });
        const roomFields = {
            roomLanguageCodes: plan.rooms,
            roomLanguageCode: plan.activeRoom,
            roomLastActiveAt,
        };
        // `update` haritayı bütünüyle değiştirir (çıkarılan odaların kaydı gerçekten silinir);
        // `set(merge)` iç içe haritayı birleştirip eski anahtarları bırakırdı. Belge yoksa
        // (beklenmez) güvenli tarafta oluşturulur.
        if (current.exists) transaction.update(userRef, roomFields);
        else transaction.set(userRef, roomFields, { merge: true });
        return { roomLanguageCodes: plan.rooms, activeRoom: plan.activeRoom, evictedRooms: plan.evictedRooms };
    });
});

/**
 * Gereksinim 1.7: bir kademe düşüşü sonrasında kullanıcının seçtiği (veya istemcinin
 * otomatik olarak en eski kullanılan diye belirlediği) fazla oda slotlarını kapatır.
 *
 * Yeni bir oda AÇMAK için zaten `switchRoom`'un kademe sınırı geçerlidir — bu callable
 * yalnızca kapatma yönünde çalışır ve mevcut açık odaların sayısını asla ARTIRMAZ, bu
 * yüzden burada ayrıca bir kademe kontrolü yapmaya gerek yoktur: `keptRooms`, her zaman
 * `rooms`'un bir alt kümesidir.
 */
export const closeRoomSlots = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const raw = (request.data as { roomCodesToClose?: unknown }).roomCodesToClose;
    if (!Array.isArray(raw) || raw.length === 0 || !raw.every((entry) => typeof entry === "string")) {
        throw new HttpsError("invalid-argument", "Invalid roomCodesToClose.");
    }
    const codesToClose = new Set(raw as string[]);
    const userRef = db.collection("users").doc(uid);

    return db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(userRef);
        const user = snapshot.data() ?? {};
        const rooms = Array.isArray(user.roomLanguageCodes)
            ? user.roomLanguageCodes.filter((room): room is string => typeof room === "string")
            : [user.roomLanguageCode].filter((room): room is string => typeof room === "string");
        const keptRooms = rooms.filter((room) => !codesToClose.has(room));
        // Kapatılacak diye gönderilen tüm odaları elden çıkarmak istemeyiz — en azından
        // bir oda kalmalı, yoksa kullanıcı bir sonraki keşifte hiçbir odada olmaz.
        if (keptRooms.length === 0) {
            throw new HttpsError("invalid-argument", "cannot-close-all-rooms");
        }

        const lastActiveAt = (typeof user.roomLastActiveAt === "object" && user.roomLastActiveAt !== null
            ? user.roomLastActiveAt
            : {}) as Record<string, number>;

        let activeRoom = typeof user.roomLanguageCode === "string" ? user.roomLanguageCode : null;
        if (activeRoom === null || codesToClose.has(activeRoom)) {
            // Aktif oda kapatıldıysa, kalanlar arasından en son kullanılanı yeni aktif
            // oda yap — kullanıcı ekranı açtığında hâlâ anlamlı bir odada olsun.
            activeRoom = keptRooms.reduce((best, room) =>
                (lastActiveAt[room] ?? 0) > (lastActiveAt[best] ?? 0) ? room : best, keptRooms[0]);
        }

        transaction.set(userRef, {
            roomLanguageCodes: keptRooms,
            roomLanguageCode: activeRoom,
        }, { merge: true });
        return { roomLanguageCodes: keptRooms, activeRoom, closedRoomCodes: [...codesToClose].filter((c) => rooms.includes(c)) };
    });
});

/**
 * "Beni beğenenler" listesinden bir profili açar.
 *
 * Ödüllü reklam (SSV) ile kazanılan `rewardedLikedMeUnlocks` sayacı taban hakka EKLENİR —
 * böylece tabanı 0 olan Ücretsiz/Ekonomik kullanıcılar artık `upgrade-required` ile
 * reddedilmez, reklam izleyerek profil açabilir (bkz. `planLikedMeUnlock`).
 *
 * - İdempotent: aynı profil ikinci kez açılırsa hak düşülmez.
 * - Yalnızca sizi GERÇEKTEN beğenmiş bir profil açılabilir; aksi halde hak boşa harcanırdı.
 * - Kota kontrolü, sayaç artışı ve açma kaydı tek transaction'dadır (eşzamanlı çift dokunuş
 *   hakkı iki kez düşmez).
 */
export const unlockLikedMe = onCall({ region: "us-central1", enforceAppCheck: true, secrets: [LIKED_ME_TOKEN_SECRET] }, async (request) => {
    const uid = requireUid(request);
    // "Beni Beğenenler" ekranı uid yerine opak `entryToken` gönderir (bkz. likedMePlan.ts);
    // eski istemciler için `profileUid` da kabul edilir.
    const payload = request.data as { profileUid?: unknown; entryToken?: unknown };
    const profileUid = typeof payload.entryToken === "string"
        ? decodeEntryToken(deriveTokenKey(LIKED_ME_TOKEN_SECRET.value()), payload.entryToken, uid)
        : payload.profileUid;
    if (typeof profileUid !== "string" || !profileUid || profileUid === uid) {
        throw new HttpsError("invalid-argument", "Invalid profile.");
    }
    const tier = await resolveTierFor(uid, request);
    const limits = await limitsFor(tier);
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);
    const usageRef = db.collection("usage").doc(uid);
    const unlockRef = db.collection("liked_me_unlocks").doc(`${uid}_${profileUid}`);
    const likeRef = db.collection("interactions").doc(`${profileUid}_${uid}`);

    return db.runTransaction(async (transaction) => {
        const [unlockSnapshot, likeSnapshot, usageSnapshot] = await Promise.all([
            transaction.get(unlockRef), transaction.get(likeRef), transaction.get(usageRef),
        ]);
        if (unlockSnapshot.exists) return { unlocked: true, alreadyUnlocked: true };
        if (likeSnapshot.data()?.type !== "like") {
            throw new HttpsError("failed-precondition", "not-liked-by-profile");
        }

        const usage = resetUsage((usageSnapshot.data() ?? {}) as UsageData, day, month);
        const decision = planLikedMeUnlock({
            tier,
            basePerDay: limits.likedMeUnlocksPerDay,
            rewardedUnlocksToday: Number(usage.rewardedLikedMeUnlocks ?? 0),
            usedToday: Number(usage.likedMeUnlocks ?? 0),
        });
        if (!decision.allowed) {
            throw new HttpsError(
                decision.error === "upgrade-required" ? "permission-denied" : "resource-exhausted",
                decision.error,
            );
        }
        usage.likedMeUnlocks = Number(usage.likedMeUnlocks ?? 0) + 1;
        transaction.set(usageRef, usage, { merge: true });
        transaction.set(unlockRef, { uid, profileUid, unlockedAt: Date.now() });
        return { unlocked: true, alreadyUnlocked: false };
    });
});

/** Records a profile visit unless a Premium user has explicitly enabled incognito mode. */
export const recordProfileVisit = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const profileUid = (request.data as { profileUid?: unknown }).profileUid;
    if (typeof profileUid !== "string" || !profileUid || profileUid === uid) {
        throw new HttpsError("invalid-argument", "Invalid profile.");
    }
    const [viewer, target] = await Promise.all([
        db.collection("users").doc(uid).get(), db.collection("public_profiles").doc(profileUid).get(),
    ]);
    if (!target.exists) throw new HttpsError("not-found", "Profile not found.");
    const isIncognito = await resolveTierFor(uid, request) === "PREMIUM" && viewer.data()?.incognito === true;
    if (isIncognito) return { recorded: false };
    await db.collection("profileViews").doc(`${profileUid}_${uid}`).set({
        profileUid, viewerUid: uid, visitedAt: Date.now(),
    }, { merge: true });
    // Gereksinim 2.12 (Faz 4): görüntülenen profil ŞU AN Boost'lu ise (bkz. `activateBoost`
    // ve `syncPublicProfile` — `boostUntil`, `public_profiles`'a canlı olarak eşlenir, bu
    // yüzden burada EK bir okuma gerekmez), bu görüntülemeyi Boost'un "fazladan gösterim"
    // sayacına ekle. Bu sayaç, Boost bittiğinde istemcideki özet diyalogda ("Boost bitti!
    // Profilin X kişiye fazladan gösterildi") gösterilir (bkz. `getBoostSummary`).
    const targetBoostUntil = Number(target.data()?.boostUntil ?? 0);
    if (targetBoostUntil > Date.now()) {
        await db.collection("users").doc(profileUid).set(
            { boostViewsGained: FieldValue.increment(1) },
            { merge: true },
        );
    }
    return { recorded: true };
});

export const setIncognito = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const enabled = (request.data as { enabled?: unknown }).enabled;
    if (typeof enabled !== "boolean") throw new HttpsError("invalid-argument", "Invalid incognito preference.");
    if (enabled && await resolveTierFor(uid, request) !== "PREMIUM") {
        throw new HttpsError("permission-denied", "upgrade-required");
    }
    await db.collection("users").doc(uid).set({ incognito: enabled }, { merge: true });
    return { enabled };
});

export const rewind = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const limits = await limitsFor(await resolveTierFor(uid, request));
    if (limits.rewindsPerDay === 0) throw new HttpsError("permission-denied", "upgrade-required");
    const usageRef = db.collection("usage").doc(uid);
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);

    // Gereksinim 1.14: TEK bir `lastPass` yerine bir yığın (stack) kullanılır — her
    // "Geri Al" günlük kota izin verdiği sürece yığının SONUNDAN (en son geçilen) bir
    // profili geri getirir, böylece art arda birden fazla geçme sırayla geri alınabilir.
    // Consume the rewind and remove its pass in the same transaction. Otherwise two
    // concurrent taps could both spend quota against the same last pass.
    return db.runTransaction(async (transaction) => {
        const usageSnapshot = await transaction.get(usageRef);
        const usage = resetUsage((usageSnapshot.data() ?? {}) as UsageData, day, month);
        const stack = usage.passStack ?? (usage.lastPass ? [usage.lastPass] : []);
        if (stack.length === 0) throw new HttpsError("failed-precondition", "no-pass-to-rewind");
        const limit = limits.rewindsPerDay ?? Number.MAX_SAFE_INTEGER;
        if ((usage.rewinds ?? 0) >= limit) {
            throw new HttpsError("resource-exhausted", "daily-rewind-limit");
        }
        const target = stack[stack.length - 1];
        const remainingStack = stack.slice(0, -1);
        usage.rewinds = (usage.rewinds ?? 0) + 1;
        transaction.delete(db.collection("interactions").doc(`${uid}_${target}`));
        transaction.set(usageRef, {
            ...usage,
            passStack: remainingStack,
            lastPass: FieldValue.delete(),
            lastPassDay: FieldValue.delete(),
        }, { merge: true });
        return { profileUid: target };
    });
});

export const activateBoost = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const limits = await limitsFor(await resolveTierFor(uid, request));
    if (limits.boostsPerMonth <= 0) throw new HttpsError("permission-denied", "upgrade-required");
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);
    const usageRef = db.collection("usage").doc(uid);
    await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(usageRef);
        const usage = resetUsage((snapshot.data() ?? {}) as UsageData, day, month);
        // Gereksinim 2.12 (Faz 4): hata mesajı "boost-cycle-limit" olarak yeniden adlandırıldı
        // (eskiden "monthly-boost-limit") — tavan artık takvim ayına değil, faturalandırma
        // döngüsüne göre uygulanıyor; bkz. `revenuecatWebhook.ts`teki sıfırlama.
        if ((usage.boosts ?? 0) >= limits.boostsPerMonth) throw new HttpsError("resource-exhausted", "boost-cycle-limit");
        transaction.set(usageRef, { ...usage, boosts: (usage.boosts ?? 0) + 1 }, { merge: true });
    });
    const boostUntil = Date.now() + 30 * 60 * 1000;
    // Gereksinim 2.12 (Faz 4): `boostViewsGained` her YENİ Boost başlangıcında sıfırlanır —
    // önceki Boost'un sayacı bir SONRAKİ Boost'a asla taşınmaz (bkz. `recordProfileVisit`
    // ve `getBoostSummary`).
    await db.collection("users").doc(uid).set({ boostUntil, boostViewsGained: 0 }, { merge: true });
    return { boostUntil };
});

/**
 * Gereksinim 2.12 (Faz 4): istemcinin, aktif ya da az önce sona ermiş bir Boost'un "fazladan
 * gösterim" sayısını okuması için — Discover ekranındaki 30 dakikalık geri sayım tamamlandığında
 * özet diyalogda ("Boost bitti! Profilin X kişiye fazladan gösterildi") kullanılır.
 */
export const getBoostSummary = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const snapshot = await db.collection("users").doc(uid).get();
    const data = snapshot.data() ?? {};
    return {
        boostUntilMillis: typeof data.boostUntil === "number" ? data.boostUntil : null,
        viewsGained: Number(data.boostViewsGained ?? 0),
    };
});

export const updateTimeZone = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const timeZone = (request.data as { timeZone?: unknown }).timeZone;
    if (typeof timeZone !== "string" || timeZone.length > 64) throw new HttpsError("invalid-argument", "Invalid time zone.");
    try { Intl.DateTimeFormat(undefined, { timeZone }); } catch { throw new HttpsError("invalid-argument", "Invalid time zone."); }
    const userRef = db.collection("users").doc(uid);
    const current = await userRef.get();
    // İstemci her açılışta/girişte cihaz saat dilimini gönderir: değişmemişse hiçbir şey
    // yazılmaz ve 7 günlük bekleme süresi YENİDEN başlatılmaz.
    if (current.data()?.timeZone === timeZone) return { timeZone, changed: false };
    const lastChangedAt = Number(current.data()?.timeZoneChangedAt ?? 0);
    if (Date.now() - lastChangedAt < 7 * 24 * 60 * 60 * 1000) {
        throw new HttpsError("failed-precondition", "timezone-change-cooldown");
    }
    await userRef.set({ timeZone, timeZoneChangedAt: Date.now() }, { merge: true });
    return { timeZone, changed: true };
});

/** Read-only, server-clock-based usage view for optimistic client gates. */
export const getUsage = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const usageRef = db.collection("usage").doc(uid);
    // One-time backfill keeps the five-like onboarding grace period correct for accounts
    // that already had successful likes before likesEver was introduced.
    await db.runTransaction(async (transaction) => {
        const usageSnapshot = await transaction.get(usageRef);
        if (typeof usageSnapshot.data()?.likesEver === "number") return;
        const existingLikes = await transaction.get(
            db.collection("interactions").where("fromUserId", "==", uid),
        );
        const likesEver = existingLikes.docs.reduce(
            (total, interaction) => total + (interaction.data().type === "like" ? 1 : 0),
            0,
        );
        transaction.set(usageRef, { likesEver }, { merge: true });
    });
    const zone = await userTimeZone(uid);
    const today = dayKey(zone);
    const snapshot = await usageRef.get();
    const usage = (snapshot.data() ?? {}) as UsageData;
    if (usage.day !== today) {
        return {
            day: today, likes: 0, messages: 0, newChats: 0, roomSwitches: 0,
            rewardedLikes: 0, rewardedLikedMeUnlocks: 0, rewardedRoomSwitches: 0, likedMeUnlocks: 0,
            likesEver: Number(usage.likesEver ?? 0),
        };
    }
    return {
        day: today,
        likes: Number(usage.likes ?? 0),
        messages: Number(usage.messages ?? 0),
        newChats: Number(usage.newChats ?? 0),
        roomSwitches: Number(usage.roomSwitches ?? 0),
        rewardedLikes: Number(usage.rewardedLikes ?? 0),
        // Ödüllü reklam doğrulamasını (SSV) bekleyen istemci, ilgili sayacın artışını buradan izler.
        rewardedLikedMeUnlocks: Number(usage.rewardedLikedMeUnlocks ?? 0),
        rewardedRoomSwitches: Number(usage.rewardedRoomSwitches ?? 0),
        likedMeUnlocks: Number(usage.likedMeUnlocks ?? 0),
        likesEver: Number(usage.likesEver ?? 0),
    };
});

/**
 * Bir ödüllü reklamın İZLENMEDEN ÖNCE uygunluk kontrolü — kullanıcıya ödülü verilemeyecek bir
 * reklam asla izletilmez. `reward`, istemcinin SSV `custom_data` değeridir (bkz. rewards.ts).
 * Asıl zorlama yine SSV callback'indedir; bu yalnızca iyimser bir ön kontroldür.
 */
async function rewardEligibility(uid: string, request: { auth?: { token?: Record<string, unknown> } | null }, reward: unknown) {
    if (!isRewardKey(reward)) throw new HttpsError("invalid-argument", "Invalid reward.");
    const tier = await resolveTierFor(uid, request);
    if (!REWARDED_ELIGIBLE_TIERS.has(tier)) throw new HttpsError("permission-denied", "rewarded-ads-free-economy-only");
    const definition = REWARD_DEFINITIONS[reward];
    const zone = await userTimeZone(uid);
    const today = dayKey(zone);
    const snapshot = await db.collection("usage").doc(uid).get();
    const usage = snapshot.data() as UsageData | undefined;
    const earnedToday = usage?.day === today ? Number(usage[definition.usageField] ?? 0) : 0;
    const plan = planRewardGrant(earnedToday, definition);
    return {
        eligible: plan.granted,
        earnedToday,
        grantPerAd: definition.grantPerAd,
        dailyCap: definition.dailyCap,
        adsRemainingToday: adsRemainingToday(earnedToday, definition),
    };
}

export const canEarnReward = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    return rewardEligibility(uid, request, (request.data as { reward?: unknown })?.reward);
});

/**
 * Geriye dönük uyumluluk: eski istemci sürümleri yalnızca beğeni ödülünü bu adla sorar.
 * Yeni kural (günde 1 reklam = +5 beğeni) aynen uygulanır.
 */
export const canEarnRewardedLike = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const result = await rewardEligibility(uid, request, "bonus_like_v1");
    if (!result.eligible) throw new HttpsError("resource-exhausted", "daily-rewarded-like-limit");
    return { eligible: true, rewardedLikesToday: result.earnedToday, dailyRewardLimit: result.dailyCap };
});

/**
 * One-time, resumable administrator operation. It seeds the canonical plan document and
 * creates public profiles for existing accounts in bounded batches. The caller must carry
 * the Firebase custom claim `admin: true`; no client-side role flag is trusted.
 */
export const bootstrapMonetization = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    requireUid(request);
    if (request.auth?.token?.admin !== true) throw new HttpsError("permission-denied", "administrator-required");
    const afterUid = (request.data as { afterUid?: unknown }).afterUid;
    if (afterUid !== undefined && typeof afterUid !== "string") throw new HttpsError("invalid-argument", "Invalid cursor.");
    await db.collection("config").doc("plans").set(serializablePlans(), { merge: true });
    let query = db.collection("users").orderBy(admin.firestore.FieldPath.documentId()).limit(250);
    if (typeof afterUid === "string" && afterUid) query = query.startAfter(afterUid);
    const users = await query.get();
    const batch = db.batch();
    const now = Date.now();
    users.docs.forEach((document) => {
        // Profil Gizlilik Kuralı: herkese açık profile yalnızca yaş yazılır.
        addPublicProfileWrites(batch, document.id, document.data(), now);
    });
    await batch.commit();
    const last = users.docs.at(-1)?.id ?? null;
    return { migrated: users.size, nextAfterUid: users.size === 250 ? last : null };
});

/** Keeps only the fields Discover is allowed to expose outside the profile owner. */
export const syncPublicProfile = onDocumentWritten("users/{uid}", async (event) => {
    const after = event.data?.after;
    if (!after?.exists) {
        await deletePublicProfile(event.params.uid);
        return;
    }
    // Profil Gizlilik Kuralı: doğum tarihi değil, yalnızca yaş yayınlanır (bkz. profilePrivacy.ts).
    await writePublicProfile(event.params.uid, after.data() ?? {});
});

// ---------------------------------------------------------------------------
// Gereksinim 2.7: Win-Back (geri kazanım) indirimi kötüye kullanım koruması
// ---------------------------------------------------------------------------

/**
 * Bir Win-Back indiriminin, kullanıldıktan sonra kaç gün geçmeden TEKRAR
 * sunulamayacağı. Gereksinim metninde açıkça 90 gün istendi.
 */
const WIN_BACK_COOLDOWN_DAYS = 90;
const WIN_BACK_COOLDOWN_MS = WIN_BACK_COOLDOWN_DAYS * 24 * 60 * 60 * 1000;

/**
 * Gereksinim 2.7: bu kullanıcı şu an bir Win-Back indirimi görmeye uygun mu?
 *
 * `customers/{uid}` belgesindeki `winBackLastRedeemedAtMillis` alanı okunur — bu,
 * `revenuecatWebhook.ts`'in ZATEN yazdığı, uygulamanın RevenueCat'le ilgili tüm müşteri
 * durumunu tuttuğu AYNI belgedir ("backend/RevenueCat customer attributes" gereksinimini
 * bu tek, tutarlı kayıt karşılar). Hiç kayıt yoksa (kullanıcı daha önce hiç Win-Back
 * kullanmadı) her zaman uygundur.
 *
 * Asıl yaptırım BURADADIR — istemci yalnızca bu sonucu okuyup indirim teklifini
 * gösterip göstermeyeceğine karar verir; istemci taraflı bir kontrol atlanırsa/manipüle
 * edilirse bile bu callable her zaman gerçek durumu döndürür.
 */
export const checkWinBackEligibility = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const snapshot = await db.collection("customers").doc(uid).get();
    const lastRedeemedAt = Number(snapshot.data()?.winBackLastRedeemedAtMillis ?? 0);
    if (!lastRedeemedAt) {
        return { eligible: true, cooldownEndsAtMillis: null };
    }
    const cooldownEndsAtMillis = lastRedeemedAt + WIN_BACK_COOLDOWN_MS;
    const eligible = Date.now() >= cooldownEndsAtMillis;
    return { eligible, cooldownEndsAtMillis: eligible ? null : cooldownEndsAtMillis };
});

/**
 * Gereksinim 2.7: kullanıcı bir Win-Back indirimini FİİLEN kullandığında (satın alma
 * tamamlandığında) istemci bunu çağırır — 90 günlük soğuma süresi BURADAN itibaren
 * başlar.
 *
 * Bilerek [checkWinBackEligibility]'DEN AYRI bir callable'dır: biri salt-okunur bir
 * SORU, diğeri durumu DEĞİŞTİREN bir KOMUTTUR. Aynı fonksiyona birleştirilseydi,
 * yalnızca "uygun muyum?" diye SORAN (ör. paywall'ı açan) bir istek yanlışlıkla
 * soğuma süresini BAŞLATABİLİRDİ — bu, kullanıcı indirimi hiç KULLANMASA bile onu
 * 90 gün boyunca haksız yere uygunsuz bırakırdı.
 */
export const recordWinBackRedemption = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    await db.collection("customers").doc(uid).set(
        { winBackLastRedeemedAtMillis: Date.now() },
        { merge: true },
    );
    return { success: true };
});

/**
 * Gereksinim 1 (Faz 4): istemciden gelen cihaz kimliğinin biçimini doğrular. İstemci
 * (`PlatformDeviceIdentifier.android.kt`) yalnızca SHA-256 özetini (64 hex karakter) gönderir
 * — ham donanım kimliği bu sunucuya HİÇBİR ZAMAN ulaşmaz.
 */
function validDeviceId(value: unknown): value is string {
    return typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
}

/**
 * Gereksinim 2.7 & 2.8 (Faz 4): bu CİHAZ (istemcinin gönderdiği özetlenmiş kimlikle temsil
 * edilir) daha önce — bu hesapla YA DA aynı fiziksel cihazdaki BAŞKA bir hesapla — bir
 * deneme/giriş fiyatı teklifi tükettiyse `eligible: false` döner.
 *
 * [checkWinBackEligibility] ile aynı "salt okunur SORU / durumu DEĞİŞTİREN KOMUT" ayrımı
 * burada da geçerlidir — yalnızca uygunluğu SORMAK hiçbir kaydı DEĞİŞTİRMEZ (bkz.
 * [recordDeviceTrialConsumption]).
 */
export const checkDeviceTrialEligibility = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    requireUid(request);
    const deviceId = (request.data as { deviceId?: unknown }).deviceId;
    if (!validDeviceId(deviceId)) throw new HttpsError("invalid-argument", "Invalid deviceId.");
    const ledger = await db.collection("device_trial_ledger").doc(deviceId).get();
    return { eligible: !ledger.exists };
});

/**
 * Gereksinim 2.7 & 2.8 (Faz 4): bu cihazda bir deneme/giriş fiyatı teklifiyle satın alma
 * TAMAMLANDIĞINDA istemci bunu çağırır — bu andan itibaren [checkDeviceTrialEligibility],
 * bu cihazdaki HERHANGİ bir hesap için (mevcut hesap DAHİL) `false` döner.
 *
 * `uids` alanı, aynı cihazda hangi hesapların bu denemeyi "harcadığını" denetim/destek
 * amaçlı iz bırakır; asıl yaptırım tek bir alanın (belgenin VAR OLMASI) üzerinden yapılır —
 * bu yüzden ["belge zaten var mı?" -> `firstConsumedAtMillis`'i SADECE ilk kez oluşturulurken
 * ayarla] denetimi, iki eşzamanlı çağrının birbirini geçmesine karşı bir transaction'da yapılır.
 */
export const recordDeviceTrialConsumption = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = requireUid(request);
    const deviceId = (request.data as { deviceId?: unknown }).deviceId;
    if (!validDeviceId(deviceId)) throw new HttpsError("invalid-argument", "Invalid deviceId.");
    const ledgerRef = db.collection("device_trial_ledger").doc(deviceId);
    await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(ledgerRef);
        const previousUids: string[] = Array.isArray(snapshot.data()?.uids) ? snapshot.data()!.uids : [];
        // Aynı cihazda BAŞKA bir hesap daha önce deneme tükettiyse bu hesap işaretlenir.
        // Defter güncellemesi ve işaret AYNI transaction'dadır (biri yazılıp diğeri kaybolamaz);
        // Firestore kuralı gereği tüm okumalar yazmalardan önce yapılır.
        const otherAccounts = previousUids.filter((existing) => existing !== uid);
        const pendingFlag = otherAccounts.length > 0
            ? await readAbuseFlag(transaction, uid, "multi_account_trial", deviceId, { otherAccountCount: otherAccounts.length })
            : null;
        if (!snapshot.exists) {
            transaction.set(ledgerRef, {
                uids: [uid],
                firstConsumedAtMillis: Date.now(),
                lastConsumedAtMillis: Date.now(),
            });
        } else {
            transaction.set(ledgerRef, {
                uids: FieldValue.arrayUnion(uid),
                lastConsumedAtMillis: Date.now(),
            }, { merge: true });
        }
        if (pendingFlag) writeAbuseFlag(transaction, pendingFlag);
    });
    return { success: true };
});
