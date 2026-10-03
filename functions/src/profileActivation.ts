import * as admin from "firebase-admin";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { assertNotBanned } from "./moderation";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

/**
 * "Giriş Backend Akışı": kullanıcının WalkMatch (Mook) profilini Discover için etkinleştirir.
 *
 * `users/{uid}.isMookActive` artık YALNIZCA bu fonksiyon (Admin SDK) tarafından yazılır;
 * Firestore kuralları istemcinin bu alanı oluşturmasını ve değiştirmesini reddeder. Böylece
 * değiştirilmiş bir istemci, örneğin yasaklıyken ya da hiç giriş yapmadan kendini Discover'a
 * sokamaz. `users` koleksiyonu WalkTalk ile ortak olduğundan, yalnızca WalkTalk'ta hesabı olan
 * biri Mook'a ilk kez giriş yaptığında bu bayrak burada açılır.
 *
 * İstemci bunu her oturum açılışında (giriş, kayıt, SSO, uygulama yeniden açılışı) çağırır;
 * işlem idempotenttir — bayrak zaten açıksa hiçbir şey yazılmaz. Kayıt sırasında istemcinin
 * profil yazımından ÖNCE çalışırsa belgeyi `merge` ile oluşturur; istemcinin sonraki yazımı
 * bir güncelleme olur ve sunucuya ait alan içermediği için kurallardan geçer.
 */
export const activateMookProfile = onCall({ region: "us-central1", enforceAppCheck: true }, async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Bu işlem için giriş yapmalısın.");
    // Yasaklı bir hesap Discover'a yeniden giremez (bkz. `moderateUser`).
    await assertNotBanned(uid);

    const userRef = db.collection("users").doc(uid);
    return db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(userRef);
        const data = snapshot.data() ?? {};
        if (data.isMookActive === true) {
            return { active: true, activatedNow: false };
        }
        transaction.set(userRef, {
            isMookActive: true,
            // İlk etkinleştirme anı korunur; analitik ve destek için iz bırakır.
            mookActivatedAt: typeof data.mookActivatedAt === "number" ? data.mookActivatedAt : Date.now(),
        }, { merge: true });
        return { active: true, activatedNow: true };
    });
});
