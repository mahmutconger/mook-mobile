import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { applyAbuseSignal, type AbuseFlagDocument, type AbuseSignal } from "./abuseFlagRules";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

export const ABUSE_FLAGS_COLLECTION = "abuse_flags";

/** Bekleyen bir sinyal yazımı: önce OKUNUR, sonra (aynı transaction'da) YAZILIR. */
export interface PendingAbuseSignal {
    flagRef: admin.firestore.DocumentReference;
    snapshot: admin.firestore.DocumentSnapshot;
    uid: string;
    signal: AbuseSignal;
    sourceKey: string;
    details: Record<string, unknown>;
}

/**
 * Transaction'ın OKUMA aşaması: işaret belgesini okur. Firestore transaction'larında tüm
 * okumalar yazmalardan önce yapılmalıdır; bu yüzden okuma ve yazma iki ayrı adımdır ve başka
 * bir kullanıcı durumu değişikliğiyle (ör. cihaz deneme defteri) AYNI transaction'da birleşebilir.
 */
export async function readAbuseFlag(
    transaction: admin.firestore.Transaction,
    uid: string,
    signal: AbuseSignal,
    sourceKey: string,
    details: Record<string, unknown> = {},
): Promise<PendingAbuseSignal> {
    const flagRef = db.collection(ABUSE_FLAGS_COLLECTION).doc(uid);
    return { flagRef, snapshot: await transaction.get(flagRef), uid, signal, sourceKey, details };
}

/**
 * Transaction'ın YAZMA aşaması: işaret belgesini günceller ve denetim izi olarak
 * `abuse_flags/{uid}/events` alt koleksiyonuna bir olay ekler.
 */
export function writeAbuseFlag(transaction: admin.firestore.Transaction, pending: PendingAbuseSignal, now = Date.now()): boolean {
    const { document, escalated } = applyAbuseSignal({
        uid: pending.uid,
        existing: (pending.snapshot.data() ?? null) as Partial<AbuseFlagDocument> | null,
        signal: pending.signal,
        sourceKey: pending.sourceKey,
        now,
    });
    transaction.set(pending.flagRef, document);
    transaction.create(pending.flagRef.collection("events").doc(), {
        signal: pending.signal,
        sourceKey: pending.sourceKey,
        details: pending.details,
        scoreAfter: document.score,
        statusAfter: document.status,
        createdAt: now,
    });
    if (escalated) {
        console.warn("Kötüye kullanım işareti yükseldi", { uid: pending.uid, status: document.status, score: document.score });
    }
    return escalated;
}

/**
 * Bağımsız bir sinyal kaydı (kendi transaction'ında). Hata FIRLATMAZ: işaretleme asla asıl
 * kullanıcı akışını (ör. mesaj gönderme hatası yanıtı) bozmamalıdır.
 */
export async function recordAbuseSignal(
    uid: string,
    signal: AbuseSignal,
    sourceKey: string,
    details: Record<string, unknown> = {},
): Promise<void> {
    try {
        await db.runTransaction(async (transaction) => {
            const pending = await readAbuseFlag(transaction, uid, signal, sourceKey, details);
            writeAbuseFlag(transaction, pending);
        });
    } catch (error) {
        console.error("Kötüye kullanım sinyali kaydedilemedi", { uid, signal, error });
    }
}

/**
 * Bir kullanıcı şikâyet edildiğinde (istemci `reports` belgesi oluşturur) şikâyet edilen
 * hesaba `user_reported` sinyali eklenir. Kaynak anahtarı şikâyetçinin uid'sidir: aynı kişinin
 * tekrar tekrar şikâyeti puanı artırmaz.
 */
export const onReportCreated = onDocumentCreated(
    { document: "reports/{reportId}", region: "us-central1" },
    async (event) => {
        const data = event.data?.data();
        const reportedUid = typeof data?.reportedUid === "string" ? data.reportedUid : null;
        const reporterUid = typeof data?.reporterUid === "string" ? data.reporterUid : null;
        if (!reportedUid || !reporterUid || reportedUid === reporterUid) return;
        await recordAbuseSignal(reportedUid, "user_reported", reporterUid, {
            reportId: event.params.reportId,
            reason: typeof data?.reason === "string" ? data.reason.slice(0, 100) : null,
            kind: typeof data?.kind === "string" ? data.kind : null,
        });
    },
);
