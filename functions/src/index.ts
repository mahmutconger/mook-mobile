import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { consumeMessageQuota, isRecipientAtDailyMessageLimit, resolveTierFor } from "./monetization";
import { assertNotBanned } from "./moderation";
import { recordAbuseSignal } from "./abuseFlags";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

// Hesap silme akışı (Google Play uyumu) ayrı modülde tutulur; buradan yeniden
// dışa aktarılır. Modül, admin.* çağrılarını yalnızca handler içinde yaptığı için
// import sırası initializeApp'ten etkilenmez.
export { deleteAccount } from "./deleteAccount";
export { activateMookProfile } from "./profileActivation"; // Giriş backend akışı: isMookActive yalnızca sunucuda
export { exportUserData } from "./exportUserData"; // Gereksinim 5 (Faz 6, KVKK Madde 11)
export { grantPromotionalEntitlement } from "./customerSupport"; // Gereksinim 7 (Faz 6)
export { revenueCatWebhook } from "./revenuecatWebhook";
export { admobRewardedSsv } from "./admobRewardedSsv";
export { migrateExistingUsers } from "./migrateExistingUsers";
export { getDiscoverFeed } from "./discoverFeed";
export { verifyEntitlementNow } from "./subscriptionVerification";
export { onReportCreated } from "./abuseFlags"; // Şikâyet → kötüye kullanım işareti
export { refreshPublicProfileAges } from "./publicProfileSync"; // Herkese açık yaşları güncel tutar
export { getLikedMe } from "./likedMe"; // "Beni Beğenenler" listesi (opak jetonlu)
export {
    activateBoost,
    bootstrapMonetization,
    canEarnReward,
    canEarnRewardedLike,
    checkDeviceTrialEligibility,
    checkWinBackEligibility,
    closeRoomSlots,
    getUsage,
    recordDeviceTrialConsumption,
    recordWinBackRedemption,
    rewind,
    recordProfileVisit,
    setIncognito,
    swipe,
    switchRoom,
    syncPublicProfile,
    unlockLikedMe,
    updateTimeZone,
} from "./monetization";
export { moderateUser } from "./moderation";

// ---------------------------------------------------------------------------
// Existing DeepL Demo Logic
// GÖZLEMLENEBİLİRLİK NOTU (kavramsal Cloud Monitoring eşiği, Gereksinim 7, Faz 6): DeepL
// karakter kullanımı SABİT bir aylık bütçeye tabidir (DeepL faturalandırma panelinden takip
// edilir, bu kod tabanında SAYILMAZ). Google Cloud Monitoring'de, DeepL'in KENDİ kullanım
// metriklerini (veya `translateText`/`sendMessage`in `translationChars*` alanlarının Log-
// based metric'e dönüştürülmüş toplamını, bkz. `monetization.ts`) izleyen bir eşik
// tanımlanmalıdır: aylık bütçenin %80'i AŞILIRSA bir UYARI (alert), %100'ü AŞILIRSA (veya
// DeepL 456 "quota exceeded" durum kodunu döndürmeye BAŞLARSA -- bkz. aşağıdaki 456
// kontrolü) bir KRİTİK (page) uyarısı tetiklenmelidir; bu, kullanıcı deneyimini (çeviri
// hatası) beklenmedik bir fatura sürprizinden ÖNCE tespit eder.
// ---------------------------------------------------------------------------

const DEEPL_AUTH_KEY = defineSecret("DEEPL_AUTH_KEY");
const MAX_TEXT_LENGTH = 500;
const RATE_LIMIT_WINDOW_MS = 60 * 1000;
const RATE_LIMIT_MAX_REQUESTS = 40;

const ALLOWED_TARGETS = new Set([
    "BG", "CS", "DA", "DE", "EL", "EN-US", "EN-GB", "ES", "ET", "FI", "FR", "HU",
    "ID", "IT", "JA", "KO", "LT", "LV", "NB", "NL", "PL", "PT-BR", "PT-PT", "RO",
    "RU", "SK", "SL", "SV", "TR", "UK", "ZH",
]);

function toSourceCode(code: string | null | undefined): string | null {
    if (!code) return null;
    const upper = String(code).toUpperCase();
    if (!ALLOWED_TARGETS.has(upper)) return null;
    return upper.split("-")[0];
}

const rateLimitBuckets = new Map<string, { windowStart: number; count: number }>();

function isRateLimited(uid: string): boolean {
    const now = Date.now();
    const bucket = rateLimitBuckets.get(uid);

    if (!bucket || now - bucket.windowStart >= RATE_LIMIT_WINDOW_MS) {
        rateLimitBuckets.set(uid, { windowStart: now, count: 1 });
        return false;
    }
    bucket.count += 1;
    return bucket.count > RATE_LIMIT_MAX_REQUESTS;
}

export const generateSsoToken = onCall(async (request) => {
    if (!request.auth || !request.auth.uid) {
        throw new HttpsError("unauthenticated", "You must be logged in to generate an SSO token.");
    }
    const uid = request.auth.uid;
    try {
        const customToken = await admin.auth().createCustomToken(uid);
        return { token: customToken };
    } catch (error) {
        console.error("Error generating custom token:", error);
        throw new HttpsError("internal", "Unable to generate SSO token.");
    }
});

export const translateText = onCall(
    { secrets: [DEEPL_AUTH_KEY], region: "us-central1", timeoutSeconds: 20 },
    async (request) => {
        if (!request.auth || !request.auth.uid) {
            throw new HttpsError("unauthenticated", "Sign in before requesting a translation.");
        }
        const uid = request.auth.uid;
        const data = request.data || {};
        const text = typeof data.text === "string" ? data.text.trim() : "";
        const target = typeof data.target === "string" ? data.target.toUpperCase() : "";
        const source = toSourceCode(data.source);

        if (!text || text.length > MAX_TEXT_LENGTH) {
            throw new HttpsError("invalid-argument", "Text is empty or too long.");
        }
        if (!ALLOWED_TARGETS.has(target)) {
            throw new HttpsError("invalid-argument", "Unsupported target language.");
        }
        if (isRateLimited(uid)) {
            throw new HttpsError("resource-exhausted", "Too many translations, slow down.");
        }

        const authKey = DEEPL_AUTH_KEY.value();
        const host = authKey.endsWith(":fx") ? "api-free.deepl.com" : "api.deepl.com";
        const body = new URLSearchParams();
        body.append("text", text);
        body.append("target_lang", target);
        if (source) body.append("source_lang", source);

        let response;
        try {
            response = await fetch(`https://${host}/v2/translate`, {
                method: "POST",
                headers: {
                    Authorization: `DeepL-Auth-Key ${authKey}`,
                    "Content-Type": "application/x-www-form-urlencoded",
                },
                body,
                signal: AbortSignal.timeout(10000),
            });
        } catch (error: any) {
            console.error("DeepL request failed:", error && error.name);
            throw new HttpsError("unavailable", "Translation provider unreachable.");
        }

        if (response.status === 429 || response.status === 456) {
            throw new HttpsError("resource-exhausted", "Translation quota exceeded.");
        }
        if (response.status === 400) {
            throw new HttpsError("invalid-argument", "Provider rejected the language pair.");
        }
        if (response.status === 401 || response.status === 403) {
            throw new HttpsError("internal", "Translation is misconfigured.");
        }
        if (!response.ok) {
            throw new HttpsError("internal", "Translation failed.");
        }

        const payload = await response.json();
        const translation = payload && payload.translations && payload.translations[0];
        if (!translation || !translation.text) {
            throw new HttpsError("internal", "Empty translation.");
        }

        return {
            translatedText: translation.text,
            detectedSource: translation.detected_source_language || null,
        };
    }
);

// ---------------------------------------------------------------------------
// Real-time Chat Messaging Logic
// ---------------------------------------------------------------------------

interface SendMessageRequest {
    chatId: string;
    peerUid: string;
    text: string;
    senderLanguage: string;
    messageId: string;
}

interface DeleteMessageRequest {
    chatId: string;
    messageId: string;
}

/**
 * Firestore document ids may not contain "/" and may not be "." or "..".
 * The client mints ids as hex-with-a-dash, so anything outside that is a caller
 * doing something the app never does.
 */
const MESSAGE_ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

function assertValidMessageId(messageId: unknown): string {
    if (typeof messageId !== "string" || !MESSAGE_ID_PATTERN.test(messageId)) {
        throw new HttpsError("invalid-argument", "Malformed messageId.");
    }
    return messageId;
}


/** The deterministic chat id for a pair of uids — must match ChatRepository.buildChatId. */
function buildChatId(uid1: string, uid2: string): string {
    return uid1 < uid2 ? `${uid1}_${uid2}` : `${uid2}_${uid1}`;
}

/**
 * Reads a user's DeepL language code out of their already-fetched profile.
 *
 * The `users` document stores this as a flat `languageCode` string ("TR", "EN-US",
 * "PT-BR") — the same catalogue the client's Languages object uses, not a nested
 * `language.code` object. Returns null when the profile is missing or has no language
 * set, so the caller decides the fallback.
 */
function readUserLanguage(profile: admin.firestore.DocumentData | undefined): string | null {
    const code = profile?.languageCode;
    return typeof code === "string" && code.length > 0 ? code.toUpperCase() : null;
}

export const sendMessage = onCall(
    { secrets: [DEEPL_AUTH_KEY], region: "us-central1" },
    async (request) => {
        if (!request.auth || !request.auth.uid) {
            throw new HttpsError("unauthenticated", "You must be logged in to send a message.");
        }

        const uid = request.auth.uid;
        // Gereksinim 1.13: yasaklı bir kullanıcının mesaj göndermesini sunucu tarafında
        // da kesin olarak engeller (istemci taraflı `ModerationGate`in gerçek yedeği).
        await assertNotBanned(uid);
        const data = request.data as SendMessageRequest;

        if (!data.chatId || !data.peerUid || !data.text || data.text.length === 0) {
            throw new HttpsError("invalid-argument", "Missing required fields.");
        }
        const messageId = assertValidMessageId(data.messageId);
        if (data.text.length > MAX_TEXT_LENGTH) {
            throw new HttpsError("invalid-argument", "Message is too long.");
        }
        if (data.peerUid === uid) {
            throw new HttpsError("invalid-argument", "You cannot message yourself.");
        }
        // The client derives chatId itself; a caller could send any string and write
        // into an unrelated room. Pin it to the pair the request actually names.
        if (data.chatId !== buildChatId(uid, data.peerUid)) {
            throw new HttpsError("invalid-argument", "chatId does not match the participants.");
        }

        // The UI only offers the compose button on a matched profile, but that is
        // cosmetic — without this check anyone could message any uid via the callable.
        const matchDoc = await db.collection("matches").doc(buildChatId(uid, data.peerUid)).get();
        if (!matchDoc.exists) {
            throw new HttpsError("failed-precondition", "not-matched");
        }

        const chatRef = db.collection("chats").doc(data.chatId);
        const messageRef = chatRef.collection("messages").doc(messageId);

        // A retry after an ambiguous network failure must not consume a second daily
        // message/new-chat allowance. The client owns messageId, so an existing document
        // is a definitive idempotency record rather than a best-effort cache.
        const existingMessage = await messageRef.get();
        if (existingMessage.exists) {
            if (existingMessage.data()?.senderUid !== uid) {
                throw new HttpsError("already-exists", "messageId is already in use.");
            }
            return {
                success: true,
                messageId,
                translatedText: existingMessage.data()?.translatedText ?? null,
                translationQuotaExhausted: false,
                recipientAtDailyLimit: await isRecipientAtDailyMessageLimit(data.peerUid),
            };
        }

        // 2. Alıcının profili bir kez okunur — hem çeviri hedef dilini hem de bildirim
        //    jetonlarını taşır. Sohbet belgesinde önbelleğe ALINMAZ: dilini sonradan
        //    değiştiren bir kullanıcı aksi halde eski dilde mesaj almaya devam ederdi.
        //    "EN-US" yalnızca son çaredir.
        const peerData = (await db.collection("users").doc(data.peerUid).get()).data();
        const targetLanguage = (readUserLanguage(peerData) ?? "EN-US").toUpperCase();
        const sourceLanguage = (data.senderLanguage || "").toUpperCase();

        // 3. Yalnızca Çeviri Kotası Mantığı: çevirinin GEREKİP GEREKMEDİĞİ, kota adımından
        //    ÖNCE belirlenir. Temel dil üzerinden karşılaştırılır (EN-US → EN-GB çevrilmez).
        //    Aynı dildeki veya desteklenmeyen hedef dile giden mesajlar için 0 karakter
        //    geçilir; bu mesajlar karakter kotasına HİÇ dokunmaz.
        const sameLanguage = sourceLanguage.split("-")[0] === targetLanguage.split("-")[0];
        const needsTranslation = !sameLanguage && ALLOWED_TARGETS.has(targetLanguage);

        // Mesaj sayısı, yeni sohbet ve dakikalık hız sınırı her mesajda uygulanır; karakter
        // kotası yalnızca çeviride düşülür. DeepL'den ÖNCE çalışır, böylece reddedilen bir
        // istek çeviri bütçesi harcamaz. Kota yetmezse mesaj çevrilmeden gönderilir.
        const chatExists = (await chatRef.get()).exists;
        let quota;
        try {
            quota = await consumeMessageQuota(
                uid,
                await resolveTierFor(uid, request),
                !chatExists,
                needsTranslation ? data.text.length : 0,
            );
        } catch (error) {
            // Dakikalık hız sınırını aşmak (spam/bot davranışı) kötüye kullanım sinyalidir.
            // Kaynak anahtarı saattir: aynı saatteki tekrarlar puanı şişirmez.
            if (error instanceof HttpsError && error.message === "rate-limit-exceeded") {
                await recordAbuseSignal(uid, "message_rate_limit", new Date().toISOString().slice(0, 13), { chatId: data.chatId });
            }
            throw error;
        }
        const now = Date.now();
        let translatedText: string | null = null;

        if (quota.translate) {
            const authKey = DEEPL_AUTH_KEY.value();
            const host = authKey.endsWith(":fx") ? "api-free.deepl.com" : "api.deepl.com";

            const body = new URLSearchParams();
            body.append("text", data.text);
            body.append("target_lang", targetLanguage);
            if (ALLOWED_TARGETS.has(sourceLanguage)) {
                body.append("source_lang", toSourceCode(sourceLanguage) || "");
            }

            try {
                const response = await fetch(`https://${host}/v2/translate`, {
                    method: "POST",
                    headers: {
                        Authorization: `DeepL-Auth-Key ${authKey}`,
                        "Content-Type": "application/x-www-form-urlencoded",
                    },
                    body,
                    signal: AbortSignal.timeout(10000),
                });
                if (response.ok) {
                    const payload = await response.json();
                    translatedText = payload?.translations?.[0]?.text || null;
                }
            } catch (error) {
                console.error("DeepL translation failed in sendMessage:", error);
                // Hata fırlatılmaz; mesaj çevrilmeden de teslim edilebilir.
            }
        }

        // 4. Save to Firestore atomically using batch
        const batch = db.batch();

        // Update Chat Room metadata.
        //
        // The unread counter is written as a *nested* map, not the dotted key
        // `unreadCounts.<uid>`: set() does not interpret dots as field paths (only
        // update() does), so a dotted key here would create a sibling field literally
        // named "unreadCounts.<uid>" that the client's unreadCounts map never sees.
        // With merge:true the nested form still leaves the other participant's count
        // untouched.
        batch.set(chatRef, {
            users: [uid, data.peerUid].sort(),
            lastMessage: data.text,
            lastMessageTimestamp: now,
            lastSenderUid: uid,
            unreadCounts: {
                [data.peerUid]: FieldValue.increment(1),
            },
            // The previous last message may have been retracted; this one is not.
            lastMessageDeleted: false,
            // Gereksinim 1.7: yalnızca sohbetin İLK mesajında (yeni sohbet) etiketleniyor
            // — `matchDoc` yukarıda zaten okundu (eşleşme kontrolü için), bu yüzden ek bir
            // okuma gerekmiyor. Bir yanıtta bu alanı tekrar YAZMIYORUZ: kullanıcı o
            // sırada farklı bir odada olsa bile sohbetin doğduğu oda değişmemelidir.
            ...(chatExists ? {} : { roomLanguageCode: matchDoc.data()?.roomLanguageCode ?? null }),
        }, { merge: true });

        // Add the message under the id the client minted. Client-owned ids let the
        // sender render the bubble optimistically and reconcile it by id when the
        // snapshot arrives, and make a retry idempotent: resending after a timeout
        // overwrites the same document instead of posting a duplicate.
        batch.set(messageRef, {
            senderUid: uid,
            text: data.text,
            senderLanguage: data.senderLanguage,
            translatedText: translatedText,
            timestamp: now,
            type: "text",
        });

        await batch.commit();

        // 5. Send FCM Push Notification
        try {
            const tokens: string[] = Array.isArray(peerData?.fcmTokens) ? peerData.fcmTokens : [];
            if (tokens.length > 0) {
                // The profile field is `displayName` (see DiscoverRepositoryImpl);
                // reading `name` here always missed and every notification arrived
                // titled with the fallback.
                const senderDoc = await db.collection("users").doc(uid).get();
                const senderName = senderDoc.data()?.displayName || "Yeni Mesaj";

                const bodyText = translatedText || data.text;

                const message = {
                    notification: {
                        title: senderName,
                        body: bodyText,
                    },
                    data: {
                        chatId: data.chatId,
                        senderUid: uid
                    },
                    tokens
                };

                const fcmResponse = await admin.messaging().sendEachForMulticast(message);

                // Cleanup invalid tokens
                if (fcmResponse.failureCount > 0) {
                    const invalidTokens: string[] = [];
                    fcmResponse.responses.forEach((resp, idx) => {
                        if (!resp.success && resp.error) {
                            const errCode = resp.error.code;
                            if (errCode === 'messaging/invalid-registration-token' ||
                                errCode === 'messaging/registration-token-not-registered') {
                                invalidTokens.push(tokens[idx]);
                            }
                        }
                    });
                    if (invalidTokens.length > 0) {
                        await db.collection("users").doc(data.peerUid).update({
                            fcmTokens: FieldValue.arrayRemove(...invalidTokens)
                        });
                    }
                }
            }
        } catch (error) {
            console.error("Failed to send FCM push notification:", error);
            // Non-fatal error, message is already saved safely.
        }

        return {
            success: true,
            messageId: messageRef.id,
            translatedText: translatedText,
            // Yalnızca Çeviri Kotası: karakter kotası bittiği için mesaj çevrilmeden gönderildi.
            // İstemci bunu engel olarak DEĞİL, bilgilendirme olarak gösterir.
            translationQuotaExhausted: quota.translationQuotaExhausted,
            // Gereksinim 1.6: gönderme asla engellenmez — bu yalnızca istemcinin yerel
            // bir bilgilendirme sistem mesajı göstermesi için bir sinyaldir.
            recipientAtDailyLimit: await isRecipientAtDailyMessageLimit(data.peerUid),
        };
    }
);

/**
 * Retracts one of the caller's own messages.
 *
 * A soft delete: the document stays, with its type flipped and its text cleared, so
 * the other participant sees a placeholder rather than the message quietly vanishing
 * out of the middle of their conversation. It also keeps the ordering stable for the
 * bounded window the clients listen on.
 *
 * Lives server-side because the security rules deny every client write to `messages`
 * — which is what stops someone deleting a message they did not send — and because
 * the chat document's preview has to be updated in the same breath.
 */
export const deleteMessage = onCall(
    { region: "us-central1" },
    async (request) => {
        if (!request.auth || !request.auth.uid) {
            throw new HttpsError("unauthenticated", "You must be logged in to delete a message.");
        }

        const uid = request.auth.uid;
        const data = request.data as DeleteMessageRequest;

        if (!data.chatId) {
            throw new HttpsError("invalid-argument", "Missing chatId.");
        }
        const messageId = assertValidMessageId(data.messageId);

        const chatRef = db.collection("chats").doc(data.chatId);
        const messageRef = chatRef.collection("messages").doc(messageId);

        const [chatDoc, messageDoc] = await Promise.all([chatRef.get(), messageRef.get()]);

        if (!messageDoc.exists) {
            throw new HttpsError("not-found", "Message does not exist.");
        }
        const message = messageDoc.data()!;
        if (message.senderUid !== uid) {
            throw new HttpsError("permission-denied", "You can only delete your own messages.");
        }
        if (message.type === "deleted") {
            // Already retracted — treat as success so a double tap is not an error.
            return { success: true, messageId };
        }

        const batch = db.batch();

        batch.update(messageRef, {
            type: "deleted",
            text: "",
            translatedText: null,
            deletedAt: Date.now(),
        });

        // If this was the newest message, the chat list is still previewing its text.
        // Flag the room instead of re-querying for the previous message: the clients
        // render their own localized placeholder, and one extra read per delete would
        // buy nothing a flag does not.
        if (chatDoc.exists && chatDoc.data()?.lastMessageTimestamp === message.timestamp) {
            batch.update(chatRef, { lastMessage: "", lastMessageDeleted: true });
        }

        await batch.commit();

        return { success: true, messageId };
    }
);
