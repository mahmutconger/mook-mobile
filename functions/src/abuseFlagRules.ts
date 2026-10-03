/**
 * Kötüye Kullanım İşaretleri (abuse_flags) — SAF kurallar (Firestore'a dokunmaz).
 *
 * Belge: `abuse_flags/{uid}` (yalnızca sunucu okur/yazar; istemci erişimi kurallarla kapalı).
 * Her sinyal türü bir ağırlık taşır; puan eşikleri aşınca durum yükselir:
 *   clear (0–4) → watch (5–9) → review (10+)
 * "review" durumundaki hesaplar insan moderasyonuna düşer (otomatik yasak YOKTUR).
 *
 * Aynı kaynaktan (ör. aynı şikâyetçi) gelen tekrar sinyaller puanı ŞİŞİRMEZ: sinyal başına
 * kaynak kümesi tutulur ve puan TEKİL kaynak sayısıyla hesaplanır.
 */

export type AbuseSignal = "user_reported" | "message_rate_limit" | "multi_account_trial";
export type AbuseStatus = "clear" | "watch" | "review";

/** Sinyal ağırlıkları: tekil kaynak başına puan. */
export const ABUSE_SIGNAL_WEIGHTS: Record<AbuseSignal, number> = {
    user_reported: 3,
    message_rate_limit: 1,
    multi_account_trial: 5,
};

export const WATCH_THRESHOLD = 5;
export const REVIEW_THRESHOLD = 10;
/** Sinyal başına saklanan en fazla tekil kaynak (belge boyutunu sınırlar). */
export const MAX_SOURCES_PER_SIGNAL = 50;

export interface AbuseFlagDocument {
    uid: string;
    score: number;
    status: AbuseStatus;
    /** Sinyal türü → toplam olay sayısı (tekrarlar dahil). */
    signalCounts: Partial<Record<AbuseSignal, number>>;
    /** Sinyal türü → tekil kaynaklar (puan bunlarla hesaplanır). */
    signalSources: Partial<Record<AbuseSignal, string[]>>;
    createdAt: number;
    updatedAt: number;
    lastSignal: AbuseSignal;
    /** Durumun en son yükseldiği an; moderasyon kuyruğunu sıralamak için. */
    escalatedAt: number | null;
}

export function isAbuseSignal(value: unknown): value is AbuseSignal {
    return typeof value === "string" && Object.prototype.hasOwnProperty.call(ABUSE_SIGNAL_WEIGHTS, value);
}

export function statusForScore(score: number): AbuseStatus {
    if (score >= REVIEW_THRESHOLD) return "review";
    if (score >= WATCH_THRESHOLD) return "watch";
    return "clear";
}

const STATUS_RANK: Record<AbuseStatus, number> = { clear: 0, watch: 1, review: 2 };

/**
 * Mevcut belgeye ([existing], yoksa `null`) yeni bir sinyal uygular ve güncel belgeyi döner.
 * Durum hiçbir zaman otomatik olarak DÜŞMEZ (yalnızca moderatör temizler).
 */
export function applyAbuseSignal(input: {
    uid: string;
    existing: Partial<AbuseFlagDocument> | null;
    signal: AbuseSignal;
    sourceKey: string;
    now: number;
}): { document: AbuseFlagDocument; escalated: boolean } {
    const existing = input.existing ?? {};
    const signalCounts = { ...(existing.signalCounts ?? {}) };
    const signalSources: Partial<Record<AbuseSignal, string[]>> = {};
    for (const [signal, sources] of Object.entries(existing.signalSources ?? {})) {
        if (isAbuseSignal(signal) && Array.isArray(sources)) signalSources[signal] = [...sources];
    }

    signalCounts[input.signal] = Number(signalCounts[input.signal] ?? 0) + 1;
    const sources = signalSources[input.signal] ?? [];
    if (!sources.includes(input.sourceKey) && sources.length < MAX_SOURCES_PER_SIGNAL) sources.push(input.sourceKey);
    signalSources[input.signal] = sources;

    const score = (Object.keys(signalSources) as AbuseSignal[])
        .reduce((total, signal) => total + (signalSources[signal]?.length ?? 0) * ABUSE_SIGNAL_WEIGHTS[signal], 0);
    const previousStatus: AbuseStatus = existing.status && existing.status in STATUS_RANK ? existing.status : "clear";
    const computed = statusForScore(score);
    const status = STATUS_RANK[computed] > STATUS_RANK[previousStatus] ? computed : previousStatus;
    const escalated = STATUS_RANK[status] > STATUS_RANK[previousStatus];

    return {
        escalated,
        document: {
            uid: input.uid,
            score,
            status,
            signalCounts,
            signalSources,
            createdAt: typeof existing.createdAt === "number" ? existing.createdAt : input.now,
            updatedAt: input.now,
            lastSignal: input.signal,
            escalatedAt: escalated ? input.now : (existing.escalatedAt ?? null),
        },
    };
}
