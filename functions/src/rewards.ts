/**
 * "Ödüllü Reklam Ekonomisi" — ödüllü reklamların ne kazandırdığına ve günlük tavanlarına dair
 * TEK kaynak. Saf modüldür (Firestore'a dokunmaz); AdMob SSV callback'i (`admobRewardedSsv.ts`),
 * uygunluk sorgusu (`canEarnReward`) ve tüketen callable'lar (`swipe`, `unlockLikedMe`,
 * `switchRoom`) aynı tabloyu kullanır. İstemcideki `BillingConfig` bu değerlerle BİLİNÇLİ olarak
 * aynı tutulur (istemci yalnızca iyimser bir tahmin yapar; asıl zorlama buradadır).
 *
 * Kurallar (para kazanma raporu):
 * - Beğeni: günde 1 reklam → +5 beğeni.
 * - "Beni beğenenler": günde en fazla 2 reklam, her biri 1 profil açma hakkı.
 * - Oda değiştirme: günde 1 reklam → +1 oda değiştirme hakkı.
 * - Yalnızca Ücretsiz ve Ekonomik kademeler ödüllü reklamla hak kazanabilir.
 */
import type { Tier } from "./monetization";

export const REWARDED_ELIGIBLE_TIERS: ReadonlySet<Tier> = new Set<Tier>(["FREE", "ECONOMY"]);

/** Tek bir beğeni ödüllü reklamının kazandırdığı ek beğeni. */
export const REWARDED_LIKES_PER_AD = 5;
/** Günlük ödüllü bonus beğeni tavanı (= günde 1 reklam × 5 beğeni). */
export const MAX_FREE_REWARDED_LIKES = 5;
/** Günlük ödüllü "beni beğenenler" açma tavanı (= günde 2 reklam × 1 açma). */
export const MAX_REWARDED_LIKED_ME_UNLOCKS = 2;
/** Günlük ödüllü oda değiştirme tavanı (= günde 1 reklam × 1 değişiklik). */
export const MAX_REWARDED_ROOM_SWITCHES = 1;

export type RewardUsageField = "rewardedLikes" | "rewardedLikedMeUnlocks" | "rewardedRoomSwitches";

export interface RewardDefinition {
    /** Bugün kazanılan bonusun tutulduğu `usage/{uid}` alanı. */
    usageField: RewardUsageField;
    /** Doğrulanmış tek bir reklamın kazandırdığı hak. */
    grantPerAd: number;
    /** Günlük toplam bonus tavanı (hak cinsinden). */
    dailyCap: number;
}

/**
 * Anahtarlar, istemcinin reklamı gösterirken SSV `custom_data` alanına yazdığı değerlerdir
 * (istemcide `RewardType.ssvCustomData`). Üç ödül aynı AdMob reklam birimini paylaşır; hangi
 * ödülün istendiği YALNIZCA bu anahtarla ayırt edilir.
 */
export const REWARD_DEFINITIONS = {
    bonus_like_v1: { usageField: "rewardedLikes", grantPerAd: REWARDED_LIKES_PER_AD, dailyCap: MAX_FREE_REWARDED_LIKES },
    bonus_liked_me_unlock_v1: { usageField: "rewardedLikedMeUnlocks", grantPerAd: 1, dailyCap: MAX_REWARDED_LIKED_ME_UNLOCKS },
    bonus_room_switch_v1: { usageField: "rewardedRoomSwitches", grantPerAd: 1, dailyCap: MAX_REWARDED_ROOM_SWITCHES },
} as const satisfies Record<string, RewardDefinition>;

export type RewardKey = keyof typeof REWARD_DEFINITIONS;

export function isRewardKey(value: unknown): value is RewardKey {
    return typeof value === "string" && Object.prototype.hasOwnProperty.call(REWARD_DEFINITIONS, value);
}

export type RewardGrantPlan =
    | { granted: true; nextCount: number }
    | { granted: false; reason: "daily-limit" };

/**
 * Doğrulanmış bir reklamın ödülü verilebilir mi? Bir reklam ödülü ya TAMAMEN verilir ya hiç
 * verilmez: kalan tavan tam bir reklamlık ödüle yetmiyorsa reddedilir (ör. +5 beğenilik
 * reklamda tavan 5 iken bugün zaten 5 kazanıldıysa).
 */
export function planRewardGrant(currentCount: number, definition: RewardDefinition): RewardGrantPlan {
    const current = Math.max(0, Math.trunc(currentCount));
    if (current + definition.grantPerAd > definition.dailyCap) return { granted: false, reason: "daily-limit" };
    return { granted: true, nextCount: current + definition.grantPerAd };
}

/** Bugün bu ödül için izlenebilecek kalan reklam sayısı. */
export function adsRemainingToday(currentCount: number, definition: RewardDefinition): number {
    const remaining = definition.dailyCap - Math.max(0, Math.trunc(currentCount));
    return Math.max(0, Math.floor(remaining / definition.grantPerAd));
}

export type LikedMeUnlockDecision =
    | { allowed: true }
    | { allowed: false; error: "daily-liked-me-limit" | "upgrade-required" };

/**
 * "Beni beğenenler" profil açma kararı. Ödüllü sayaç (SSV) taban hakka EKLENİR; böylece
 * tabanı 0 olan Ücretsiz/Ekonomik kullanıcı reklam izleyerek profil açabilir. Hak dolduğunda
 * ödüllü yolu olan kademelere `daily-liked-me-limit` döner (istemci limit sayfasında reklam
 * teklif eder); hiçbir yolu olmayan kademeye `upgrade-required` döner.
 */
export function planLikedMeUnlock(input: {
    tier: Tier;
    basePerDay: number | null;
    rewardedUnlocksToday: number;
    usedToday: number;
}): LikedMeUnlockDecision {
    if (input.basePerDay === null) return { allowed: true };
    const eligible = REWARDED_ELIGIBLE_TIERS.has(input.tier);
    const bonus = eligible ? Math.min(Math.max(0, input.rewardedUnlocksToday), MAX_REWARDED_LIKED_ME_UNLOCKS) : 0;
    if (input.usedToday < input.basePerDay + bonus) return { allowed: true };
    if (!eligible && input.basePerDay === 0) return { allowed: false, error: "upgrade-required" };
    return { allowed: false, error: "daily-liked-me-limit" };
}

/**
 * "Beni beğenenler" için bugün kalan profil açma hakkı. `null` = sınırsız (Premium).
 * [planLikedMeUnlock] ile AYNI formül: taban + (uygun kademede) ödüllü reklam bonusu − kullanılan.
 */
export function likedMeUnlocksRemaining(input: {
    tier: Tier;
    basePerDay: number | null;
    rewardedUnlocksToday: number;
    usedToday: number;
}): number | null {
    if (input.basePerDay === null) return null;
    const eligible = REWARDED_ELIGIBLE_TIERS.has(input.tier);
    const bonus = eligible ? Math.min(Math.max(0, input.rewardedUnlocksToday), MAX_REWARDED_LIKED_ME_UNLOCKS) : 0;
    return Math.max(0, input.basePerDay + bonus - Math.max(0, input.usedToday));
}
