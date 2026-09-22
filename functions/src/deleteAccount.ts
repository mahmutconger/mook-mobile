import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";

// RevenueCat gizli (secret) API anahtarı. Koda gömülmez; Firebase Secret Manager
// üzerinden okunur: `firebase functions:secrets:set REVENUECAT_API_KEY`.
const REVENUECAT_API_KEY = defineSecret("REVENUECAT_API_KEY");

// Tek bir Firestore silme grubunun (batch) taşıyabileceği en fazla işlem sayısı 500'dür.
// Güvenli bir tampon bırakmak için 400 kullanıyoruz.
const FIRESTORE_BATCH_LIMIT = 400;

/**
 * Bir Firestore sorgusuna uyan tüm dokümanları, batch limitini aşmayacak şekilde
 * sayfalayarak siler. Büyük veri kümelerinde bellek taşmasını ve batch limiti
 * hatasını önler.
 *
 * @return Silinen toplam doküman sayısı.
 */
async function deleteByQuery(
    query: admin.firestore.Query,
): Promise<number> {
    const db = admin.firestore();
    let deleted = 0;

    // Her turda en fazla FIRESTORE_BATCH_LIMIT doküman siler; kalan yoksa döngü biter.
    // eslint-disable-next-line no-constant-condition
    while (true) {
        const snapshot = await query.limit(FIRESTORE_BATCH_LIMIT).get();
        if (snapshot.empty) break;

        const batch = db.batch();
        snapshot.docs.forEach((doc) => batch.delete(doc.ref));
        await batch.commit();

        deleted += snapshot.size;
        // Son sayfa doluysa devam et; kısmi sayfa geldiyse tamamlanmıştır.
        if (snapshot.size < FIRESTORE_BATCH_LIMIT) break;
    }

    return deleted;
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
 * Kullanıcıya ait tüm Firestore verisini siler:
 * - `users/{uid}` ve alt koleksiyonları (recursiveDelete),
 * - `usage/{uid}` (kullanım/limit sayaçları),
 * - `interactions` içinde kullanıcının gönderdiği VE aldığı tüm kaydırmalar,
 * - `matches` içinde kullanıcının yer aldığı tüm eşleşmeler.
 *
 * interactions dokümanları "{fromUserId}_{toUserId}" kimliğiyle tutulduğundan,
 * kimliğe göre değil alanlara göre sorgulanır ki her iki yön de yakalansın.
 */
async function deleteUserFirestore(uid: string): Promise<void> {
    const db = admin.firestore();

    // users/{uid} ve olası alt koleksiyonları tek çağrıda özyinelemeli olarak siler.
    await db.recursiveDelete(db.collection("users").doc(uid));

    // usage/{uid} — günlük kaydırma limiti gibi kullanım sayaçları.
    await db.recursiveDelete(db.collection("usage").doc(uid));

    // Kullanıcının başlattığı ve kullanıcıya yapılan tüm etkileşimler.
    await deleteByQuery(db.collection("interactions").where("fromUserId", "==", uid));
    await deleteByQuery(db.collection("interactions").where("toUserId", "==", uid));

    // Kullanıcının taraf olduğu tüm eşleşmeler.
    await deleteByQuery(db.collection("matches").where("users", "array-contains", uid));
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
 *   3. RevenueCat    — ölümcül değil (kayıt olmayabilir; loglanıp devam edilir).
 *   4. Firebase Auth — en son ve ölümcül; kimlik bu adımda yok edilir.
 *
 * Hata mesajları istemciye asla ham haliyle sızdırılmaz; sunucuda loglanır,
 * istemciye yalnızca genel bir HttpsError döner.
 */
export const deleteAccount = onCall(
    { secrets: [REVENUECAT_API_KEY], region: "us-central1", timeoutSeconds: 120 },
    async (request) => {
        // Güvenlik önce gelir: kimliği doğrulanmamış hiçbir istek işlenmez.
        if (!request.auth || !request.auth.uid) {
            throw new HttpsError(
                "unauthenticated",
                "Hesabı silmek için giriş yapmış olmanız gerekir.",
            );
        }

        const uid = request.auth.uid;

        // 1. Storage — ölümcül değil. Silinemezse loglayıp devam ederiz.
        try {
            await deleteUserStorage(uid);
        } catch (error) {
            console.error(`[deleteAccount] Storage temizliği başarısız (uid=${uid}):`, error);
        }

        // 2. Firestore — ölümcül. Başarısız olursa Auth kaydını SİLMEDEN hata fırlatırız
        //    ki kullanıcı verisi Auth'suz yetim kalmasın ve işlem güvenle tekrar denenebilsin.
        try {
            await deleteUserFirestore(uid);
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
