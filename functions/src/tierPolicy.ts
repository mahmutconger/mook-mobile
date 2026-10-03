import { planRoomTrim, type RoomTrimPlan } from "./roomSwitch";

/**
 * Kademe değişikliklerinin SAF (Firestore'a dokunmayan) politika kuralları.
 *
 * `revenuecatSync.ts` her RevenueCat senkronizasyonunda bu planı kullanıcının belgesiyle
 * çalıştırır; böylece kural mantığı emülatör gerektirmeden birim testlenebilir
 * (bkz. `test/tierPolicy.test.js`).
 */

export type TierName = "FREE" | "ECONOMY" | "STANDARD" | "PREMIUM";

/** Kademelerin sıralaması — büyük değer daha yüksek plandır. */
export const TIER_RANK: Record<TierName, number> = {
    FREE: 0,
    ECONOMY: 1,
    STANDARD: 2,
    PREMIUM: 3,
};

export function isTierName(value: unknown): value is TierName {
    return typeof value === "string" && Object.prototype.hasOwnProperty.call(TIER_RANK, value);
}

/** Yeni kademe öncekinden düşükse `true` (ör. PREMIUM → STANDARD, ECONOMY → FREE). */
export function isTierDowngrade(previous: TierName, next: TierName): boolean {
    return TIER_RANK[next] < TIER_RANK[previous];
}

export interface PlanEnforcementInput {
    /** Önceki kademeden daha düşük bir kademeye geçildi mi? */
    downgraded: boolean;
    /** Yeni planın sınırları (yalnızca politika için gerekli alanlar). */
    limits: { roomSlots: number | null; boostsPerMonth: number; incognito: boolean };
    user: {
        rooms: string[];
        activeRoom: string | null;
        lastActiveAt: Record<string, number>;
        boostUntil: number | null;
        incognito: boolean;
    };
    now: number;
}

export interface PlanEnforcementPlan {
    /** Oda kırpması gerekiyorsa plan; gerekmiyorsa `null`. */
    roomTrim: RoomTrimPlan | null;
    /** Aktif Boost sonlandırılmalı mı? */
    endBoost: boolean;
    /** Gizli mod kapatılmalı mı? */
    disableIncognito: boolean;
}

/**
 * Yeni kademenin sınırlarını kullanıcının mevcut durumuna uygular (idempotent):
 * - Slot sayısını aşan odalar kapatılır (bkz. `planRoomTrim`).
 * - Aktif bir Boost, HERHANGİ bir kademe düşüşünde veya yeni planda Boost hakkı yoksa sonlandırılır.
 * - Yeni plan gizli modu desteklemiyorsa gizli mod kapatılır.
 *
 * Kademe düşmemiş olsa bile çağrılması güvenlidir: plan zaten sınırlar içindeyse hiçbir
 * değişiklik önermez — bu yüzden kaçırılmış/sırası bozuk webhook olaylarında da durum düzelir.
 */
export function planPlanEnforcement(input: PlanEnforcementInput): PlanEnforcementPlan {
    const trim = planRoomTrim({
        rooms: input.user.rooms,
        activeRoom: input.user.activeRoom,
        lastActiveAt: input.user.lastActiveAt,
        roomSlots: input.limits.roomSlots,
    });
    const boostActive = input.user.boostUntil !== null && input.user.boostUntil > input.now;
    return {
        roomTrim: trim.closedRooms.length > 0 ? trim : null,
        endBoost: boostActive && (input.downgraded || input.limits.boostsPerMonth <= 0),
        disableIncognito: input.user.incognito && !input.limits.incognito,
    };
}

/** Sunucunun tanıdığı RevenueCat yetki (entitlement) kimlikleri. */
export const KNOWN_ENTITLEMENTS: ReadonlySet<string> = new Set(["economy", "standard", "premium"]);

export interface RevenueCatSubscriberPayload {
    subscriber?: {
        entitlements?: Record<string, { expires_date?: string | null } | null | undefined>;
    };
}

/**
 * RevenueCat REST (`GET /v1/subscribers/{uid}`) yanıtından ŞU AN aktif olan, tanınan yetkileri
 * çıkarır. `expires_date` boşsa (ömür boyu) veya gelecekteyse yetki aktiftir. Sonuç küçük
 * harfli, tekilleştirilmiş ve sıralıdır — böylece özel talep (custom claim) karşılaştırması
 * kararlı olur.
 */
export function activeEntitlementIds(payload: RevenueCatSubscriberPayload, now: number): string[] {
    const active = new Set<string>();
    for (const [identifier, entitlement] of Object.entries(payload.subscriber?.entitlements ?? {})) {
        const id = identifier.trim().toLowerCase();
        if (!KNOWN_ENTITLEMENTS.has(id)) continue;
        const raw = entitlement?.expires_date;
        const expiresAt = typeof raw === "string" ? Date.parse(raw) : null;
        if (expiresAt === null || Number.isNaN(expiresAt) || expiresAt > now) active.add(id);
    }
    return [...active].sort();
}
