/**
 * Keşfet Öncelik Sıralama Kuralları — SAF (Firestore'a dokunmayan) yardımcılar.
 *
 * Bir aday sayfası İÇİNDE sıralama hiyerarşisi:
 *   1. Aktif Boost'lu profiller (`boostUntil > şimdi`)
 *   2. Premium aboneler
 *   3. Standart aboneler
 *   4. Diğerleri (Ekonomik ve Ücretsiz)
 * Aynı gruptaki profillerin göreli sırası KORUNUR (kararlı sıralama) — sorgunun kendi sırası
 * (son aktiflik) grup içinde geçerli kalır.
 */

export type PriorityTier = "FREE" | "ECONOMY" | "STANDARD" | "PREMIUM";

export interface DiscoverPriorityInput {
    boostUntil: unknown;
    subscriptionTier: unknown;
}

const TIER_PRIORITY: Record<PriorityTier, number> = { PREMIUM: 1, STANDARD: 2, ECONOMY: 3, FREE: 3 };

/** Bilinmeyen/eksik kademe en düşük önceliği alır. */
export function tierPriority(value: unknown): number {
    return typeof value === "string" && value in TIER_PRIORITY ? TIER_PRIORITY[value as PriorityTier] : 3;
}

/** Profilin öncelik grubu: 0 = Boost, 1 = Premium, 2 = Standart, 3 = diğerleri. */
export function discoverPriority(input: DiscoverPriorityInput, now: number): number {
    if (typeof input.boostUntil === "number" && input.boostUntil > now) return 0;
    return tierPriority(input.subscriptionTier);
}

/** Listeyi öncelik grubuna göre KARARLI biçimde sıralar (yeni dizi döner). */
export function sortByDiscoverPriority<T>(items: T[], priorityOf: (item: T) => number): T[] {
    return items
        .map((item, index) => ({ item, index, priority: priorityOf(item) }))
        .sort((a, b) => a.priority - b.priority || a.index - b.index)
        .map(({ item }) => item);
}
