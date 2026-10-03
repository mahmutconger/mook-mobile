/**
 * KVKK/GDPR Uyumlu Silme Şeması — hesap silindiğinde kullanıcıya ait HER Firestore verisinin
 * nerede durduğunu tanımlayan SAF (Firestore'a dokunmayan) plan.
 *
 * `deleteAccount` bu planı BulkWriter ile yürütür. Plan ayrı tutulur, böylece "hiçbir yol
 * unutulmadı" iddiası emülatör gerektirmeden birim testiyle doğrulanır
 * (bkz. `test/deletionSchema.test.js`).
 *
 * YENİ BİR KOLEKSİYON kullanıcı kimliği (uid) taşıyorsa BURAYA eklenmelidir; aksi halde
 * hesap silindiğinde yetim kişisel veri kalır.
 */

export type DeletionTarget =
    | {
        /** Tek bir belge; `recursive` ise alt koleksiyonlarıyla birlikte silinir. */
        kind: "document";
        collection: string;
        docId: string;
        recursive: boolean;
        reason: string;
    }
    | {
        /** Sorguya uyan tüm belgeler; `recursive` ise alt koleksiyonlarıyla birlikte silinir. */
        kind: "query";
        collection: string;
        field: string;
        operator: "==" | "array-contains";
        value: string;
        recursive: boolean;
        reason: string;
    }
    | {
        /**
         * Koleksiyon grubu sorgusu (ör. tüm `messages` alt koleksiyonları). Firestore'da tek
         * alanlı koleksiyon-grubu dizini gerektirir (`firestore.indexes.json` → fieldOverrides).
         */
        kind: "collectionGroupQuery";
        collectionGroup: string;
        field: string;
        value: string;
        reason: string;
    }
    | {
        /**
         * Belge SİLİNMEZ, yalnızca dizi alanındaki uid referansı çıkarılır. Belgenin kendisi
         * kişisel olmayan bir kötüye kullanım kaydıdır (ör. cihaz başına deneme hakkı).
         */
        kind: "arrayRemove";
        collection: string;
        field: string;
        value: string;
        reason: string;
    };

/**
 * Bir kullanıcının silinmesi gereken tüm verilerini sıralı bir plan olarak döner.
 *
 * @param uid Silinecek kullanıcının Firebase Auth kimliği.
 * @param email Kullanıcının e-posta adresi (varsa) — `mail` kuyruğundaki kayıtları bulmak için.
 */
export function buildDeletionPlan(uid: string, email: string | null): DeletionTarget[] {
    if (uid.trim().length === 0) throw new Error("uid boş olamaz");

    const plan: DeletionTarget[] = [
        // Kullanıcının kendi belgeleri (alt koleksiyonlarıyla birlikte).
        { kind: "document", collection: "users", docId: uid, recursive: true, reason: "Profil, FCM jetonları, ayarlar" },
        { kind: "document", collection: "usage", docId: uid, recursive: true, reason: "Günlük/aylık kota sayaçları" },
        { kind: "document", collection: "public_profiles", docId: uid, recursive: true, reason: "Keşfet'te görünen herkese açık profil" },
        { kind: "document", collection: "customers", docId: uid, recursive: true, reason: "RevenueCat abonelik durumu ve yetkiler" },
        { kind: "document", collection: "private_profile_meta", docId: uid, recursive: false, reason: "Yaş yenileme zamanı (doğum günü türevi)" },

        // Sohbetler: 1:1 sohbet belgesi VE tüm `messages` alt koleksiyonu. Eşleşme silindiği için
        // sohbet karşı taraf için de erişilemez hale gelir; mesajlar silinen kullanıcının
        // kişisel verisini içerdiğinden tamamen kaldırılır.
        { kind: "query", collection: "chats", field: "users", operator: "array-contains", value: uid, recursive: true, reason: "Sohbetler ve mesajları" },
        { kind: "query", collection: "matches", field: "users", operator: "array-contains", value: uid, recursive: true, reason: "Eşleşmeler" },
        { kind: "query", collection: "interactions", field: "fromUserId", operator: "==", value: uid, recursive: false, reason: "Kullanıcının verdiği beğeni/geçmeler" },
        { kind: "query", collection: "interactions", field: "toUserId", operator: "==", value: uid, recursive: false, reason: "Kullanıcıya verilen beğeni/geçmeler" },

        // Profil ziyaretleri ve "beni beğenenler" açmaları — iki yönde de.
        { kind: "query", collection: "profileViews", field: "viewerUid", operator: "==", value: uid, recursive: false, reason: "Kullanıcının ziyaret ettiği profiller" },
        { kind: "query", collection: "profileViews", field: "profileUid", operator: "==", value: uid, recursive: false, reason: "Kullanıcının profilini ziyaret edenler" },
        { kind: "query", collection: "liked_me_unlocks", field: "uid", operator: "==", value: uid, recursive: false, reason: "Kullanıcının açtığı profiller" },
        { kind: "query", collection: "liked_me_unlocks", field: "profileUid", operator: "==", value: uid, recursive: false, reason: "Kullanıcının profilini açanlar" },

        // Şikâyetler — kullanıcının gönderdiği ve hakkında yapılanlar.
        { kind: "query", collection: "reports", field: "reporterUid", operator: "==", value: uid, recursive: false, reason: "Kullanıcının gönderdiği şikâyetler" },
        { kind: "query", collection: "reports", field: "reportedUid", operator: "==", value: uid, recursive: false, reason: "Kullanıcı hakkındaki şikâyetler" },

        // Reklam ödülleri, ödeme olayları ve destek kayıtları.
        { kind: "query", collection: "admob_rewarded_events", field: "uid", operator: "==", value: uid, recursive: false, reason: "Ödüllü reklam (SSV) kayıtları" },
        { kind: "query", collection: "ssv_transactions", field: "uid", operator: "==", value: uid, recursive: false, reason: "Eski SSV işlem kayıtları" },
        { kind: "document", collection: "abuse_flags", docId: uid, recursive: true, reason: "Kötüye kullanım işareti ve olay geçmişi" },
        { kind: "query", collection: "processed_revenuecat_events", field: "uid", operator: "==", value: uid, recursive: false, reason: "RevenueCat olay kayıtları (eski şema)" },
        { kind: "query", collection: "processed_revenuecat_events", field: "uids", operator: "array-contains", value: uid, recursive: false, reason: "RevenueCat olay kayıtları" },
        { kind: "query", collection: "support_grants", field: "targetUid", operator: "==", value: uid, recursive: false, reason: "Destek ekibinin verdiği promosyon kayıtları" },

        // Paylaşılan Firebase projesindeki eski (WalkTalk) içerikler.
        { kind: "query", collection: "posts", field: "uid", operator: "==", value: uid, recursive: true, reason: "Gönderiler (eski şema)" },
        { kind: "query", collection: "posts", field: "authorUid", operator: "==", value: uid, recursive: true, reason: "Gönderiler" },
        { kind: "query", collection: "stories", field: "uid", operator: "==", value: uid, recursive: true, reason: "Hikâyeler (eski şema)" },
        { kind: "query", collection: "stories", field: "authorUid", operator: "==", value: uid, recursive: true, reason: "Hikâyeler" },
        { kind: "query", collection: "calls", field: "callerUid", operator: "==", value: uid, recursive: true, reason: "Arama kayıtları (arayan)" },
        { kind: "query", collection: "calls", field: "receiverUid", operator: "==", value: uid, recursive: true, reason: "Arama kayıtları (aranan)" },
        { kind: "collectionGroupQuery", collectionGroup: "messages", field: "senderUid", value: uid, reason: "Herkese açık sohbet odalarında gönderilen mesajlar" },

        // Cihaz başına deneme hakkı defteri: kayıt kalır (kötüye kullanım önleme), uid çıkarılır.
        { kind: "arrayRemove", collection: "device_trial_ledger", field: "uids", value: uid, reason: "Cihaz deneme defterindeki uid referansı" },
    ];

    const normalizedEmail = email?.trim();
    if (normalizedEmail) {
        // Veri dışa aktarma e-postaları (Trigger Email kuyruğu) e-posta adresini içerir.
        plan.push({ kind: "query", collection: "mail", field: "to", operator: "==", value: normalizedEmail, recursive: true, reason: "Kullanıcıya gönderilen e-posta kuyruğu kayıtları" });
    }
    return plan;
}
