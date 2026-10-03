import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

// İndirme bağlantısının imzalı URL geçerlilik süresi (Gereksinim 5, KVKK Madde 11).
const SIGNED_URL_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 gün

/**
 * Bir koleksiyonu belirli bir alana göre sorgular ve dokümanları düz nesnelere çevirir.
 * `deleteAccount.ts`deki `deleteByQuery` ile AYNI sorgulama mantığını izler ama SİLMEK
 * yerine OKUR -- iki fonksiyonun kapsadığı koleksiyon kümesi KASITLI olarak BİREBİR aynıdır
 * (KVKK Madde 11: "işlenen veriyi öğrenme" ile "silinmesini isteme" hakları AYNI veri
 * kümesine uygulanmalıdır).
 */
async function collectByQuery(
    query: admin.firestore.Query,
): Promise<Record<string, unknown>[]> {
    const snapshot = await query.get();
    return snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
}

/**
 * Kullanıcıya ait TÜM Firestore verisini tek bir JSON-serileştirilebilir nesneye derler.
 *
 * `deleteAccount.ts`deki `deleteUserFirestore()` ile AYNI koleksiyon kümesini kapsar (bkz.
 * o fonksiyonun KDoc'u) -- `public_profiles`, `admob_rewarded_events` dahil.
 */
async function collectUserData(uid: string): Promise<Record<string, unknown>> {
    const [
        userDoc,
        usageDoc,
        customerDoc,
        publicProfileDoc,
        sentInteractions,
        receivedInteractions,
        matches,
        rewardedEvents,
    ] = await Promise.all([
        db.collection("users").doc(uid).get(),
        db.collection("usage").doc(uid).get(),
        db.collection("customers").doc(uid).get(),
        db.collection("public_profiles").doc(uid).get(),
        collectByQuery(db.collection("interactions").where("fromUserId", "==", uid)),
        collectByQuery(db.collection("interactions").where("toUserId", "==", uid)),
        collectByQuery(db.collection("matches").where("users", "array-contains", uid)),
        collectByQuery(db.collection("admob_rewarded_events").where("uid", "==", uid)),
    ]);

    return {
        exportedAt: new Date().toISOString(),
        uid,
        user: userDoc.exists ? userDoc.data() : null,
        usage: usageDoc.exists ? usageDoc.data() : null,
        customer: customerDoc.exists ? customerDoc.data() : null,
        publicProfile: publicProfileDoc.exists ? publicProfileDoc.data() : null,
        interactionsSent: sentInteractions,
        interactionsReceived: receivedInteractions,
        matches,
        rewardedAdEvents: rewardedEvents,
    };
}

/**
 * Derlenen JSON'ı Cloud Storage'a yükler ve 7 gün geçerli, imzalı (signed) bir indirme
 * URL'si döndürür -- Storage Security Rules'u KASITLI olarak BAYPAS EDER (imzalı URL,
 * dosyanın KENDİSİNE gömülü bir yetkilendirmedir; bu yüzden `storage.rules`e bu yol için
 * AYRI bir kural EKLEMEYE gerek YOKTUR, bkz. `deployment_checklist.md`).
 */
async function uploadExportAndGetSignedUrl(uid: string, payload: Record<string, unknown>): Promise<string> {
    const bucket = admin.storage().bucket();
    const fileName = `exports/${uid}/${Date.now()}.json`;
    const file = bucket.file(fileName);
    await file.save(Buffer.from(JSON.stringify(payload, null, 2), "utf8"), {
        contentType: "application/json",
        // Gereksinim 5: dosya YALNIZCA imzalı URL üzerinden erişilebilir olmalı, herkese
        // açık (public) OLMAMALIDIR -- bu yüzden `predefinedAcl`/`public: true` KULLANILMAZ.
    });
    const [url] = await file.getSignedUrl({
        action: "read",
        expires: Date.now() + SIGNED_URL_TTL_MS,
    });
    return url;
}

/**
 * `mail` koleksiyonuna bir doküman yazarak indirme bağlantısını e-postayla gönderir.
 *
 * Bu, Firebase'in resmi "Trigger Email" uzantısının (`firestore-send-email`) BEKLEDİĞİ
 * şemadır -- bu uzantı kurulu VE yapılandırılmışsa (bkz. `deployment_checklist.md`), bu
 * koleksiyona yazılan her doküman otomatik olarak gönderilir. Uzantı kurulu DEĞİLSE bu
 * yazma işlemi SESSİZCE hiçbir şey YAPMAZ (doküman sırada bekler) -- işlem HATA VERMEZ,
 * yalnızca e-posta gönderilmez; bu KASITLI bir zarifçe bozulma (graceful degradation)
 * seçimidir, `exportUserData` callable'ının kendisi bu duruma rağmen BAŞARILI döner
 * (dosya zaten Storage'a yazıldı).
 */
async function queueDownloadLinkEmail(email: string, downloadUrl: string): Promise<void> {
    await db.collection("mail").add({
        to: email,
        message: {
            subject: "WalkMatch — Verileriniz Hazır",
            text:
                "Merhaba,\n\n" +
                "WalkMatch hesabınıza ait verilerin dışa aktarma talebiniz tamamlandı. " +
                `Aşağıdaki bağlantıdan verilerinizi indirebilirsiniz (bağlantı 7 gün geçerlidir):\n\n${downloadUrl}\n\n` +
                "Bu talebi siz yapmadıysanız lütfen bizimle iletişime geçin.\n\n" +
                "WalkMatch Ekibi",
        },
    });
}

/**
 * Gereksinim 5 (Faz 6, KVKK Madde 11 — Veri Taşınabilirliği): kullanıcının TÜM verisini
 * bir JSON dosyasına derler, Cloud Storage'a yükler ve imzalı bir indirme bağlantısını
 * e-posta ile gönderir.
 *
 * İstemciye (Android `ExportUserDataUseCase`) YALNIZCA isteğin KABUL EDİLDİĞİ bildirilir --
 * dosyanın kendisi asla istemciye dönmez (KVKK'nın öngördüğü gibi, hassas bir veri dökümü
 * yalnızca kimliği doğrulanmış kullanıcının KENDİ e-posta adresine, sunucu tarafında
 * üretilen bir bağlantıyla ulaşır).
 */
export const exportUserData = onCall(
    // Gereksinim 2 (Faz 6): Kapsamlı App Check Uygulaması.
    { region: "us-central1", enforceAppCheck: true, timeoutSeconds: 120 },
    async (request) => {
        if (!request.auth || !request.auth.uid) {
            throw new HttpsError(
                "unauthenticated",
                "Verilerinizi dışa aktarmak için giriş yapmış olmanız gerekir.",
            );
        }
        const uid = request.auth.uid;

        let email = request.auth.token.email as string | undefined;
        if (!email) {
            // Özel talepte e-posta yoksa (ör. telefonla kayıt) Admin SDK üzerinden okunur.
            try {
                const userRecord = await admin.auth().getUser(uid);
                email = userRecord.email ?? undefined;
            } catch (error) {
                console.error(`[exportUserData] Kullanıcı kaydı okunamadı (uid=${uid}):`, error);
            }
        }
        if (!email) {
            throw new HttpsError(
                "failed-precondition",
                "Hesabınıza kayıtlı bir e-posta adresi bulunamadığı için dışa aktarma bağlantısı gönderilemiyor.",
            );
        }

        try {
            const payload = await collectUserData(uid);
            const downloadUrl = await uploadExportAndGetSignedUrl(uid, payload);
            await queueDownloadLinkEmail(email, downloadUrl);
            return { success: true };
        } catch (error) {
            console.error(`[exportUserData] Veri dışa aktarma başarısız (uid=${uid}):`, error);
            throw new HttpsError(
                "internal",
                "Veri dışa aktarma isteği işlenirken bir sorun oluştu. Lütfen tekrar deneyin.",
            );
        }
    },
);
