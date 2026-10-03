import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret, defineString } from "firebase-functions/params";
import * as admin from "firebase-admin";
import { GoogleAuth } from "google-auth-library";
import { FieldValue } from "firebase-admin/firestore";
import { buildDeletionPlan } from "./deletionSchema";

// RevenueCat gizli (secret) API anahtarı. Koda gömülmez; Firebase Secret Manager
// üzerinden okunur: `firebase functions:secrets:set REVENUECAT_API_KEY`.
const REVENUECAT_API_KEY = defineSecret("REVENUECAT_API_KEY");

// Gereksinim 5 (Faz 6, KVKK Madde 11): GA4 mülk kimliği (ör. "properties/123456789")
// -- SECRET DEĞİLDİR (hassas değildir), bu yüzden `defineSecret` yerine `defineString`
// kullanılır. Boş bırakılırsa (henüz yapılandırılmadıysa) Analytics silme isteği
// SESSİZCE atlanır (bkz. `requestAnalyticsDataDeletion` KDoc'u) -- diğer TÜM silme
// adımları (Storage/Firestore/RevenueCat/Auth) buna bağımlı DEĞİLDİR.
//   firebase functions:config:... YERİNE (v2'de kaldırıldı) dağıtım zamanında ortam
// değişkeni olarak sağlanır: `firebase deploy --only functions` öncesi
// `.env.<project-id>` dosyasına `GA4_PROPERTY_ID=properties/123456789` eklenmelidir.
const GA4_PROPERTY_ID = defineString("GA4_PROPERTY_ID", { default: "" });

// Sorgu sonuçları sayfa sayfa okunur; her sayfa BulkWriter kuyruğuna eklenir.
const QUERY_PAGE_SIZE = 300;
// Geçici hatalarda (ör. ABORTED, UNAVAILABLE) bir yazmanın en fazla deneme sayısı.
const MAX_WRITE_ATTEMPTS = 5;

/** Silme planının yürütülmesi sırasında oluşan, işlemi ölümcül kılan hata. */
class DeletionIncompleteError extends Error {
    constructor(message: string) {
        super(message);
        this.name = "DeletionIncompleteError";
    }
}

/**
 * Bir sorgunun tüm sonuçlarını `__name__` imleciyle sayfalayarak gezer. Silmeler BulkWriter'da
 * henüz işlenmemiş olsa bile imleç sayesinde aynı belge iki kez okunmaz.
 */
async function forEachDocument(
    query: admin.firestore.Query,
    visit: (doc: admin.firestore.QueryDocumentSnapshot) => Promise<void>,
): Promise<number> {
    let visited = 0;
    let cursor: admin.firestore.QueryDocumentSnapshot | null = null;
    // eslint-disable-next-line no-constant-condition
    while (true) {
        let page = query.orderBy(admin.firestore.FieldPath.documentId()).limit(QUERY_PAGE_SIZE);
        if (cursor) page = page.startAfter(cursor);
        const snapshot = await page.get();
        if (snapshot.empty) break;
        for (const doc of snapshot.docs) await visit(doc);
        visited += snapshot.size;
        cursor = snapshot.docs[snapshot.docs.length - 1];
        if (snapshot.size < QUERY_PAGE_SIZE) break;
    }
    return visited;
}

/**
 * KVKK/GDPR Uyumlu Silme Şeması'nı (`deletionSchema.ts`) tek bir BulkWriter ile yürütür.
 *
 * - Alt koleksiyonu olabilen belgeler (`users`, `chats` + `messages`, `matches` …)
 *   `recursiveDelete` ile, alt koleksiyonlarıyla birlikte silinir.
 * - `device_trial_ledger` gibi kötüye kullanım kayıtlarında belge korunur, yalnızca uid çıkarılır.
 * - Geçici hatalar BulkWriter tarafından yeniden denenir; kalıcı TEK bir hata bile işlemi
 *   başarısız sayar (Auth kaydı silinmez, istemci güvenle yeniden deneyebilir — tüm adımlar
 *   idempotenttir).
 * - Koleksiyon grubu sorguları dizin gerektirir; dizin henüz hazır değilse (FAILED_PRECONDITION)
 *   bu adım loglanır ama hesabın geri kalanının silinmesini engellemez.
 */
async function deleteUserFirestore(uid: string, email: string | null): Promise<void> {
    const db = admin.firestore();
    const writer = db.bulkWriter();
    const failedPaths: string[] = [];
    writer.onWriteError((error) => {
        if (error.failedAttempts < MAX_WRITE_ATTEMPTS) return true;
        failedPaths.push(error.documentRef.path);
        console.error(`[deleteAccount] Kalıcı yazma hatası: ${error.documentRef.path}`, error.message);
        return false;
    });
    // Tekil işlem sözleri hataları `onWriteError` üzerinden raporlar; işlenmemiş ret oluşmasın.
    const ignore = () => undefined;

    for (const target of buildDeletionPlan(uid, email)) {
        try {
            switch (target.kind) {
                case "document": {
                    const ref = db.collection(target.collection).doc(target.docId);
                    if (target.recursive) await db.recursiveDelete(ref, writer);
                    else writer.delete(ref).catch(ignore);
                    break;
                }
                case "query": {
                    const query = db.collection(target.collection).where(target.field, target.operator, target.value);
                    await forEachDocument(query, async (doc) => {
                        if (target.recursive) await db.recursiveDelete(doc.ref, writer);
                        else writer.delete(doc.ref).catch(ignore);
                    });
                    break;
                }
                case "collectionGroupQuery": {
                    const query = db.collectionGroup(target.collectionGroup).where(target.field, "==", target.value);
                    try {
                        await forEachDocument(query, async (doc) => { writer.delete(doc.ref).catch(ignore); });
                    } catch (error) {
                        if ((error as { code?: number }).code !== 9) throw error; // 9 = FAILED_PRECONDITION
                        console.warn(`[deleteAccount] ${target.collectionGroup}.${target.field} koleksiyon grubu dizini hazır değil; adım atlandı (uid=${uid}).`);
                    }
                    break;
                }
                case "arrayRemove": {
                    const query = db.collection(target.collection).where(target.field, "array-contains", target.value);
                    await forEachDocument(query, async (doc) => {
                        writer.update(doc.ref, { [target.field]: FieldValue.arrayRemove(target.value) }).catch(ignore);
                    });
                    break;
                }
            }
        } catch (error) {
            await writer.close().catch(ignore);
            throw new DeletionIncompleteError(`${target.reason} silinemedi: ${String(error)}`);
        }
    }

    await writer.close();
    if (failedPaths.length > 0) {
        throw new DeletionIncompleteError(`${failedPaths.length} belge silinemedi`);
    }
}

/**
 * Kullanıcının Firebase Storage'daki tüm dosyalarını siler.
 *
 * İki önek (prefix) taranır çünkü fotoğraflar `users/{uid}/`, hikayeler ise
 * `stories/{uid}/` altında tutulur (bkz. storage.rules).
 */
async function deleteUserStorage(uid: string): Promise<void> {
    const bucket = admin.storage().bucket();
    await Promise.all([
        bucket.deleteFiles({ prefix: `users/${uid}/` }),
        bucket.deleteFiles({ prefix: `stories/${uid}/` }),
    ]);
}

/**
 * RevenueCat aboneci (subscriber) kaydını REST API üzerinden siler.
 *
 * `app_user_id`, uygulamada `Purchases.logIn(firebaseUid)` ile ayarlandığı için
 * Firebase uid'sinin aynısıdır. 200 (silindi) ve 404 (zaten yok) başarı sayılır.
 *
 * @throws Error RevenueCat beklenmeyen bir hata döndürürse (çağıran tarafında
 *         yakalanıp ölümcül olmayan hata olarak loglanır).
 */
async function deleteRevenueCatSubscriber(uid: string, apiKey: string): Promise<void> {
    const response = await fetch(
        `https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(uid)}`,
        {
            method: "DELETE",
            headers: { Authorization: `Bearer ${apiKey}` },
            signal: AbortSignal.timeout(10000),
        },
    );

    if (!response.ok && response.status !== 404) {
        throw new Error(`RevenueCat delete returned ${response.status}`);
    }
}

// Gereksinim 2 (Faz 6): normal (App Check korumalı) istekler için GEREKLİ olan geniş OAuth
// kapsamı burada İSTENMEZ -- bu, Cloud Functions'ın kendi çalışma zamanı hizmet hesabının
// (Application Default Credentials) `analytics.edit` kapsamıyla Google'a DOĞRUDAN kimlik
// doğrulaması yaptığı, İSTEMCİDEN TAMAMEN bağımsız bir sunucu-sunucu çağrısıdır.
const analyticsAuth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/analytics.edit"] });

/**
 * Gereksinim 5 (Faz 6, KVKK Madde 11): GA4 Kullanıcı Silme (User Deletion) API'sine
 * (`analyticsadmin.googleapis.com/v1alpha/{property}:submitUserDeletion`) bir silme isteği
 * gönderir. `propertyId` hem `"123456789"` hem `"properties/123456789"` biçiminde kabul edilir.
 *
 * ÖNEMLİ MİMARİ NOT: GA4'ün Kullanıcı Silme API'si bir `userId` (Measurement Protocol
 * Kullanıcı-Kimliği), `appInstanceId` veya `clientId` tanımlayıcısı BEKLER -- Firebase Auth
 * `uid`'si bu ÜÇÜNDEN BİRİ DEĞİLDİR. Bu yüzden Android istemcisi artık HER oturum açma/soğuk
 * başlangıçta `Firebase.analytics.setUserId(uid)` çağırır (bkz.
 * `AnalyticsRepository.identifyUser` KDoc'u) -- bu, `uid`'yi GA4'ün KENDİ Kullanıcı-Kimliği
 * alanına EŞLER ve bu isteğin `userId.userId = uid` ile DOĞRU kullanıcıyı hedeflemesini
 * sağlar.
 *
 * KASITLI olarak ÖLÜMCÜL DEĞİLDİR (bkz. `deleteAccount`'taki adım sıralaması KDoc'u):
 * `GA4_PROPERTY_ID` henüz yapılandırılmamışsa ya da GA4 API'si geçici olarak erişilemezse
 * hesap silme işleminin TAMAMI engellenmemelidir -- yalnızca loglanır.
 */
async function requestAnalyticsDataDeletion(uid: string, propertyId: string): Promise<void> {
    if (!propertyId) {
        console.warn(`[deleteAccount] GA4_PROPERTY_ID yapılandırılmadı; Analytics silme isteği atlandı (uid=${uid}).`);
        return;
    }
    const resourceName = normalizeGa4PropertyName(propertyId);
    if (!resourceName) {
        console.warn(`[deleteAccount] GA4_PROPERTY_ID geçersiz biçimde ("${propertyId}"); Analytics silme isteği atlandı (uid=${uid}).`);
        return;
    }
    const client = await analyticsAuth.getClient();
    await client.request({
        url: `https://analyticsadmin.googleapis.com/v1alpha/${resourceName}:submitUserDeletion`,
        method: "POST",
        data: { userId: uid },
    });
}

/**
 * GA4 mülk kimliğini Admin API kaynak adına (`properties/<sayı>`) dönüştürür.
 * Yalnızca rakamlardan oluşan bir kimlik ya da `properties/<sayı>` kabul edilir; aksi halde `null`.
 */
export function normalizeGa4PropertyName(raw: string): string | null {
    const trimmed = raw.trim();
    const match = /^(?:properties\/)?(\d+)$/.exec(trimmed);
    return match ? `properties/${match[1]}` : null;
}

/**
 * Kullanıcının hesabını ve tüm ilişkili verisini kalıcı olarak siler.
 *
 * Google Play'in hesap silme politikasına uyum için gereklidir. Firestore güvenlik
 * kuralları toplu istemci silmesini (`allow delete: if false`) engellediğinden, bu
 * işlem güvenli bir sunucu ortamında Admin SDK ile yapılır.
 *
 * Silme sırası (ölümcüllük durumu):
 *   1. Storage       — ölümcül değil (dosya olmayabilir; loglanıp devam edilir).
 *   2. Firestore     — ölümcül (PII; başarısızsa Auth SİLİNMEZ ki tekrar denenebilsin).
 *        Gereksinim 5 (Faz 6): artık `public_profiles`, `admob_rewarded_events` (ve
 *        ileriye dönük olarak `ssv_transactions`/`abuse_flags`) de bu adımın KAPSAMINDA.
 *   3. RevenueCat    — ölümcül değil (kayıt olmayabilir; loglanıp devam edilir).
 *   3b. Analytics veri silme isteği (Gereksinim 5, KVKK Madde 11) — ölümcül değil
 *        (bkz. `requestAnalyticsDataDeletion` KDoc'u).
 *   4. Firebase Auth — en son ve ölümcül; kimlik bu adımda yok edilir.
 *
 * Hata mesajları istemciye asla ham haliyle sızdırılmaz; sunucuda loglanır,
 * istemciye yalnızca genel bir HttpsError döner.
 */
export const deleteAccount = onCall(
    // Gereksinim 2 (Faz 6): Kapsamlı App Check Uygulaması -- geri alınamaz bir işlem,
    // yalnızca geçerli bir cihaz bütünlüğü kanıtı taşıyan istekler kabul edilir.
    { secrets: [REVENUECAT_API_KEY], region: "us-central1", timeoutSeconds: 540, memory: "512MiB", enforceAppCheck: true },
    async (request) => {
        // Güvenlik önce gelir: kimliği doğrulanmamış hiçbir istek işlenmez.
        if (!request.auth || !request.auth.uid) {
            throw new HttpsError(
                "unauthenticated",
                "Hesabı silmek için giriş yapmış olmanız gerekir.",
            );
        }

        const uid = request.auth.uid;
        // `mail` kuyruğundaki kayıtları bulmak için e-posta, Auth kaydı silinmeden ÖNCE okunur.
        const email = request.auth.token.email ?? (await admin.auth().getUser(uid).catch(() => null))?.email ?? null;

        // 1. Storage — ölümcül değil. Silinemezse loglayıp devam ederiz.
        try {
            await deleteUserStorage(uid);
        } catch (error) {
            console.error(`[deleteAccount] Storage temizliği başarısız (uid=${uid}):`, error);
        }

        // 2. Firestore — ölümcül. Başarısız olursa Auth kaydını SİLMEDEN hata fırlatırız
        //    ki kullanıcı verisi Auth'suz yetim kalmasın ve işlem güvenle tekrar denenebilsin.
        try {
            await deleteUserFirestore(uid, email);
        } catch (error) {
            console.error(`[deleteAccount] Firestore temizliği başarısız (uid=${uid}):`, error);
            throw new HttpsError(
                "internal",
                "Hesap verileri silinirken bir sorun oluştu. Lütfen tekrar deneyin.",
            );
        }

        // 3. RevenueCat — ölümcül değil. Abonelik kaydı olmayabilir; başarısızlık
        //    hesap silmeyi engellemez, yalnızca loglanır.
        try {
            await deleteRevenueCatSubscriber(uid, REVENUECAT_API_KEY.value());
        } catch (error) {
            console.error(`[deleteAccount] RevenueCat aboneci silme başarısız (uid=${uid}):`, error);
        }

        // 3b. Analytics veri silme isteği (Gereksinim 5, KVKK Madde 11) — ölümcül DEĞİL
        //     (bkz. `requestAnalyticsDataDeletion` KDoc'u): GA4 geçici olarak erişilemez
        //     olsa dahi hesap silme işleminin GERİ KALANI engellenmemelidir.
        try {
            await requestAnalyticsDataDeletion(uid, GA4_PROPERTY_ID.value());
        } catch (error) {
            console.error(`[deleteAccount] Analytics veri silme isteği başarısız (uid=${uid}):`, error);
        }

        // 4. Firebase Auth — en son adım. Buradan sonra istemci artık yetkili değildir.
        try {
            await admin.auth().deleteUser(uid);
        } catch (error) {
            console.error(`[deleteAccount] Auth kullanıcı silme başarısız (uid=${uid}):`, error);
            throw new HttpsError(
                "internal",
                "Hesap kaldırılırken bir sorun oluştu. Lütfen tekrar deneyin.",
            );
        }

        return { success: true };
    },
);
