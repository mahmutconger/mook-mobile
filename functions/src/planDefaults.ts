/**
 * Plan sınırlarının sunucu tarafı varsayılanları — `config/plans` belgesinin TEMELİ.
 *
 * Tek Doğruluk Kaynağı: sunucu `limitsFor()` ile `config/plans` belgesini bu varsayılanların
 * ÜZERİNE birleştirir. İstemci aynı belgeyi okur (bkz. istemci `PlanCatalogRepository`); belge
 * hiç okunamazsa kullandığı gömülü yedek katalog bu tabloyla BİREBİR aynı olmalıdır — bu,
 * `test/planCatalog.test.js` ile CI'da doğrulanır.
 */
import type { Tier } from "./monetization";

export interface PlanLimits {
    dailyLikes: number | null;
    dailyMessages: number | null;
    dailyNewChats: number | null;
    roomSlots: number | null;
    roomSwitchesPerDay: number | null;
    likedMeUnlocksPerDay: number | null;
    rewindsPerDay: number | null;
    boostsPerMonth: number;
    showsAds: boolean;
    freeRoam: boolean;
    incognito: boolean;
}

export const PLAN_DEFAULTS: Record<Tier, PlanLimits> = {
    FREE: {
        dailyLikes: 10, dailyMessages: 50, dailyNewChats: 3, roomSlots: 1,
        roomSwitchesPerDay: 1, likedMeUnlocksPerDay: 0, rewindsPerDay: 0,
        boostsPerMonth: 0, showsAds: true, freeRoam: false, incognito: false,
    },
    ECONOMY: {
        dailyLikes: 30, dailyMessages: 200, dailyNewChats: 10, roomSlots: 3,
        roomSwitchesPerDay: 3, likedMeUnlocksPerDay: 0, rewindsPerDay: 0,
        boostsPerMonth: 0, showsAds: false, freeRoam: false, incognito: false,
    },
    STANDARD: {
        dailyLikes: 100, dailyMessages: null, dailyNewChats: null, roomSlots: 5,
        roomSwitchesPerDay: null, likedMeUnlocksPerDay: 5, rewindsPerDay: 3,
        boostsPerMonth: 1, showsAds: false, freeRoam: false, incognito: false,
    },
    PREMIUM: {
        dailyLikes: null, dailyMessages: null, dailyNewChats: null, roomSlots: null,
        roomSwitchesPerDay: null, likedMeUnlocksPerDay: null, rewindsPerDay: null,
        boostsPerMonth: 4, showsAds: false, freeRoam: true, incognito: true,
    },
};

/** `config/plans` belgesinin şeması: küçük harfli kademe adı → tam sınır tablosu. */
export function serializablePlans(): Record<string, PlanLimits> {
    return Object.fromEntries(Object.entries(PLAN_DEFAULTS).map(([tier, limits]) => [tier.toLowerCase(), limits]));
}
