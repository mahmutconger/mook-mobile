/**
 * Profil Gizlilik Kuralları — SAF (Firestore'a dokunmayan) yardımcılar.
 *
 * KURAL: `public_profiles` belgesine doğum tarihi (`birthDateMillis`) ASLA yazılmaz; yalnızca
 * sunucuda hesaplanan tam sayı `age` yazılır. Doğum tarihi yalnızca sahibin okuyabildiği
 * `users/{uid}` belgesinde kalır.
 *
 * Yaş hesabı:
 * - Doğum tarihi bir TAKVİM GÜNÜDÜR ve UTC bileşenleriyle okunur (istemci doğum tarihini UTC
 *   gece yarısı olarak kaydeder — bkz. istemci `AgeCalculator`).
 * - "Bugün" kullanıcının KAYITLI saat dilimindeki tarihtir (`users.timeZone`).
 * - Yaş, (ay, gün) karşılaştırmasıyla doğum günü geldiğinde artar. 29 Şubat doğumlular artık
 *   yıllarda 1 Mart'ta bir yaş büyür (28 Şubat'ta henüz büyümemiştir).
 */

export interface CalendarDate {
    year: number;
    month: number; // 1-12
    day: number;   // 1-31
}

/** Doğum tarihini (epoch ms) UTC takvim günü olarak okur; geçersizse `null`. */
export function birthCalendarDate(birthDateMillis: unknown): CalendarDate | null {
    if (typeof birthDateMillis !== "number" || !Number.isFinite(birthDateMillis)) return null;
    const date = new Date(birthDateMillis);
    if (Number.isNaN(date.getTime())) return null;
    return { year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, day: date.getUTCDate() };
}

/** [now] anında, [timeZone] saat dilimindeki takvim günü. Geçersiz dilimde UTC kullanılır. */
export function todayIn(timeZone: string, now: number): CalendarDate {
    let parts: Intl.DateTimeFormatPart[];
    try {
        parts = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" })
            .formatToParts(new Date(now));
    } catch {
        parts = new Intl.DateTimeFormat("en-CA", { timeZone: "UTC", year: "numeric", month: "2-digit", day: "2-digit" })
            .formatToParts(new Date(now));
    }
    const value = (type: string) => Number(parts.find((part) => part.type === type)?.value);
    return { year: value("year"), month: value("month"), day: value("day") };
}

/** Tam yıl olarak yaş; doğum tarihi yoksa, gelecekteyse veya mantıksızsa `null`. */
export function computeAge(birthDateMillis: unknown, now: number, timeZone: string): number | null {
    const birth = birthCalendarDate(birthDateMillis);
    if (!birth) return null;
    const today = todayIn(timeZone, now);
    let age = today.year - birth.year;
    const birthdayNotYetReached = today.month < birth.month || (today.month === birth.month && today.day < birth.day);
    if (birthdayNotYetReached) age -= 1;
    return age >= 0 && age < 130 ? age : null;
}

/**
 * Yaşın bir sonraki değişeceği tarihten biraz ÖNCEKİ yeniden hesaplama anı (epoch ms).
 *
 * Bir sonraki doğum gününün UTC gece yarısından 14 saat önce (dünyadaki en erken saat dilimi
 * UTC+14) döner; böylece her saat diliminde doğum günü kaçırılmaz. Zamanlanmış görev o anda
 * yaşı YENİDEN hesaplar (henüz değişmediyse aynı değeri yazar) ve sonraki anı tekrar belirler.
 * Hesaplanan an geçmişteyse (doğum günü penceresinin içindeyiz) 6 saat sonrası döner.
 */
export function nextAgeRefreshAt(birthDateMillis: unknown, now: number, timeZone: string): number | null {
    const birth = birthCalendarDate(birthDateMillis);
    if (!birth) return null;
    const today = todayIn(timeZone, now);
    const candidateFor = (year: number) => {
        // 29 Şubat'ın artık olmayan yıldaki karşılığı 1 Mart'tır (Date.UTC bunu kendiliğinden kaydırır).
        return Date.UTC(year, birth.month - 1, birth.day) - 14 * 3_600_000;
    };
    const passedThisYear = today.month > birth.month || (today.month === birth.month && today.day >= birth.day);
    const target = candidateFor(passedThisYear ? today.year + 1 : today.year);
    return target > now ? target : now + 6 * 3_600_000;
}

/** Herkese açık profil projeksiyonu: yalnızca Keşfet'in gösterebileceği alanlar. */
export function buildPublicProfile(data: Record<string, unknown>, now: number, timeZone: string): Record<string, unknown> {
    const tiers = ["FREE", "ECONOMY", "STANDARD", "PREMIUM"];
    return {
        displayName: typeof data.displayName === "string" ? data.displayName : "",
        // Doğum tarihi yerine YALNIZCA yaş (bkz. dosya KDoc'u).
        age: computeAge(data.birthDateMillis, now, timeZone),
        countryCode: typeof data.countryCode === "string" ? data.countryCode : null,
        languageCode: typeof data.languageCode === "string" ? data.languageCode : null,
        avatarUrl: typeof data.avatarUrl === "string" ? data.avatarUrl : null,
        discoveryPhotos: Array.isArray(data.discoveryPhotos) ? data.discoveryPhotos : [],
        bio: typeof data.bio === "string" ? data.bio : "",
        interests: Array.isArray(data.interests) ? data.interests : [],
        verified: data.verified === true,
        lastActiveTimestamp: typeof data.lastActiveTimestamp === "number" ? data.lastActiveTimestamp : 0,
        isMookActive: data.isMookActive === true,
        discoverVisible: data.discoverVisible !== false,
        incognito: data.incognito === true,
        visibleTo: Array.isArray(data.visibleTo) ? data.visibleTo : [],
        boostUntil: typeof data.boostUntil === "number" ? data.boostUntil : 0,
        // Premium rozeti ve Keşfet önceliği için; yalnızca RevenueCat senkronizasyonu yazar.
        subscriptionTier: typeof data.subscriptionTier === "string" && tiers.includes(data.subscriptionTier)
            ? data.subscriptionTier
            : "FREE",
    };
}
