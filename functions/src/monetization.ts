import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

// Firebase loads imported modules before executing index.ts. Initializing here keeps
// deploy-time function discovery and every exported entry point safe regardless of
// import order.
if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

export type Tier = "FREE" | "ECONOMY" | "STANDARD" | "PREMIUM";

interface PlanLimits {
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

const FAIR_DAILY_LIKES = 1000;
const FAIR_DAILY_MESSAGES = 2000;
const MESSAGE_RATE_LIMIT = 30;
const MESSAGE_RATE_WINDOW_MS = 60_000;
const MAX_FREE_REWARDED_LIKES = 5;
const PLAN_DEFAULTS: Record<Tier, PlanLimits> = {
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

function serializablePlans(): Record<string, PlanLimits> {
    return Object.fromEntries(Object.entries(PLAN_DEFAULTS).map(([tier, limits]) => [tier.toLowerCase(), limits]));
}

function requireUid(request: { auth?: { uid?: string } | null }): string {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in before using this feature.");
    return uid;
}

function tierFromEntitlements(value: unknown): Tier {
    const names = new Set<string>();
    if (Array.isArray(value)) value.forEach((entry) => names.add(String(entry).toLowerCase()));
    else if (typeof value === "string") names.add(value.toLowerCase());
    else if (value && typeof value === "object") {
        Object.entries(value as Record<string, unknown>).forEach(([key, enabled]) => {
            if (enabled) names.add(key.toLowerCase());
        });
    }
    if (names.has("premium")) return "PREMIUM";
    if (names.has("standard")) return "STANDARD";
    if (names.has("economy")) return "ECONOMY";
    return "FREE";
}

export function resolveTier(request: { auth?: { token?: Record<string, unknown> } | null }): Tier {
    return tierFromEntitlements(request.auth?.token?.revenueCatEntitlements);
}

/**
 * A freshly completed purchase can reach a callable before Firebase refreshes its custom token.
 * In that narrow case, consult the RevenueCat Firebase extension document once so the customer
 * is never incorrectly denied at a paid limit.
 */
export async function resolveTierFor(uid: string, request: { auth?: { token?: Record<string, unknown> } | null }): Promise<Tier> {
    const claimed = resolveTier(request);
    if (claimed !== "FREE") return claimed;
    const customer = await db.collection("customers").doc(uid).get();
    const data = customer.data();
    return tierFromEntitlements(data?.revenueCatEntitlements ?? data?.entitlements ?? data?.activeEntitlements);
}

async function limitsFor(tier: Tier): Promise<PlanLimits> {
    const document = await db.collection("config").doc("plans").get();
    const raw = document.data()?.[tier.toLowerCase()] as Partial<PlanLimits> | undefined;
    return { ...PLAN_DEFAULTS[tier], ...raw };
}

function dayKey(timeZone: string, now = new Date()): string {
    try {
        return new Intl.DateTimeFormat("en-CA", {
            timeZone, year: "numeric", month: "2-digit", day: "2-digit",
        }).format(now);
    } catch {
        return new Intl.DateTimeFormat("en-CA", {
            timeZone: "Europe/Istanbul", year: "numeric", month: "2-digit", day: "2-digit",
        }).format(now);
    }
}

function monthKey(timeZone: string, now = new Date()): string {
    try {
        return new Intl.DateTimeFormat("en-CA", {
            timeZone, year: "numeric", month: "2-digit",
        }).format(now);
    } catch {
        return new Intl.DateTimeFormat("en-CA", {
            timeZone: "Europe/Istanbul", year: "numeric", month: "2-digit",
        }).format(now);
    }
}

interface UsageData {
    day?: string;
    month?: string;
    likes?: number;
    messages?: number;
    newChats?: number;
    roomSwitches?: number;
    rewardedLikes?: number;
    likedMeUnlocks?: number;
    rewinds?: number;
    boosts?: number;
    messageWindowStart?: number;
    messageWindowCount?: number;
    lastPass?: string;
    lastPassDay?: string;
}

function resetUsage(data: UsageData, day: string, month: string): UsageData {
    const daily = data.day === day ? data : {
        day, likes: 0, messages: 0, newChats: 0, roomSwitches: 0,
        rewardedLikes: 0, likedMeUnlocks: 0, rewinds: 0,
        messageWindowStart: 0, messageWindowCount: 0,
    };
    return daily.month === month ? daily : { ...daily, month, boosts: 0 };
}

async function userTimeZone(uid: string): Promise<string> {
    const user = await db.collection("users").doc(uid).get();
    const zone = user.data()?.timeZone;
    return typeof zone === "string" && zone.length <= 64 ? zone : "Europe/Istanbul";
}

async function consumeDaily(
    uid: string,
    key: keyof UsageData,
    limit: number | null,
    error: string,
    options: { fairLimit?: number; bonusLikes?: boolean; rateLimit?: boolean } = {},
): Promise<void> {
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);
    const reference = db.collection("usage").doc(uid);
    await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(reference);
        const data = resetUsage((snapshot.data() ?? {}) as UsageData, day, month);
        const baseLimit = limit ?? options.fairLimit ?? Number.MAX_SAFE_INTEGER;
        const effectiveLimit = options.bonusLikes
            ? baseLimit + Math.min(data.rewardedLikes ?? 0, MAX_FREE_REWARDED_LIKES)
            : baseLimit;
        const value = Number(data[key] ?? 0);
        if (value >= effectiveLimit) throw new HttpsError("resource-exhausted", error);
        const now = Date.now();
        if (options.rateLimit) {
            const windowStart = Number(data.messageWindowStart ?? 0);
            const windowCount = now - windowStart >= MESSAGE_RATE_WINDOW_MS
                ? 0 : Number(data.messageWindowCount ?? 0);
            if (windowCount >= MESSAGE_RATE_LIMIT) {
                throw new HttpsError("resource-exhausted", "rate-limit-exceeded");
            }
            data.messageWindowStart = windowCount === 0 ? now : windowStart;
            data.messageWindowCount = windowCount + 1;
        }
        const next = { ...data, [key]: value + 1 } as UsageData;
        transaction.set(reference, next, { merge: true });
    });
}

/** Applies message, new-chat and per-minute ceilings atomically before a new message is written. */
export async function enforceMessageQuota(uid: string, tier: Tier, isNewChat: boolean): Promise<void> {
    const limits = await limitsFor(tier);
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);
    const reference = db.collection("usage").doc(uid);
    await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(reference);
        const usage = resetUsage((snapshot.data() ?? {}) as UsageData, day, month);
        const dailyLimit = limits.dailyMessages ?? FAIR_DAILY_MESSAGES;
        if ((usage.messages ?? 0) >= dailyLimit) {
            throw new HttpsError("resource-exhausted", "daily-message-limit");
        }
        const now = Date.now();
        const windowStart = Number(usage.messageWindowStart ?? 0);
        const windowCount = now - windowStart >= MESSAGE_RATE_WINDOW_MS
            ? 0 : Number(usage.messageWindowCount ?? 0);
        if (windowCount >= MESSAGE_RATE_LIMIT) {
            throw new HttpsError("resource-exhausted", "rate-limit-exceeded");
        }
        if (isNewChat) {
            const newChatLimit = limits.dailyNewChats ?? Number.MAX_SAFE_INTEGER;
            if ((usage.newChats ?? 0) >= newChatLimit) {
                throw new HttpsError("resource-exhausted", "daily-new-chat-limit");
            }
            usage.newChats = (usage.newChats ?? 0) + 1;
        }
        usage.messages = (usage.messages ?? 0) + 1;
        usage.messageWindowStart = windowCount === 0 ? now : windowStart;
        usage.messageWindowCount = windowCount + 1;
        transaction.set(reference, usage, { merge: true });
    });
}

function orderedPair(a: string, b: string): string {
    return a < b ? `${a}_${b}` : `${b}_${a}`;
}

export const swipe = onCall({ region: "us-central1", enforceAppCheck: false }, async (request) => {
    const uid = requireUid(request);
    const data = request.data as { toUserId?: unknown; isLike?: unknown };
    if (typeof data.toUserId !== "string" || !data.toUserId || data.toUserId === uid || typeof data.isLike !== "boolean") {
        throw new HttpsError("invalid-argument", "Invalid swipe request.");
    }
    const targetUid = data.toUserId;
    const isLike = data.isLike;
    const tier = await resolveTierFor(uid, request);
    const limits = await limitsFor(tier);
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);
    const interactionRef = db.collection("interactions").doc(`${uid}_${targetUid}`);
    const reverseRef = db.collection("interactions").doc(`${targetUid}_${uid}`);
    const matchRef = db.collection("matches").doc(orderedPair(uid, targetUid));
    const usageRef = db.collection("usage").doc(uid);
    const ownUserRef = db.collection("users").doc(uid);
    const targetUserRef = db.collection("users").doc(targetUid);

    return db.runTransaction(async (transaction) => {
        const [existing, reverse, usageSnapshot, ownUser, targetUser] = await Promise.all([
            transaction.get(interactionRef), transaction.get(reverseRef), transaction.get(usageRef),
            transaction.get(ownUserRef), transaction.get(targetUserRef),
        ]);
        if (!targetUser.exists) throw new HttpsError("not-found", "Profile not found.");
        const ownBlocked = (ownUser.data()?.blockedUsers ?? []) as string[];
        const targetBlocked = (targetUser.data()?.blockedUsers ?? []) as string[];
        if (ownBlocked.includes(targetUid) || targetBlocked.includes(uid)) {
            throw new HttpsError("permission-denied", "blocked-user");
        }
        if (existing.exists) {
            const wasLike = existing.data()?.type === "like";
            const mutual = wasLike && reverse.data()?.type === "like";
            return { result: mutual ? "mutual_match" : wasLike ? "single_like" : "pass", idempotent: true };
        }

        const usage = resetUsage((usageSnapshot.data() ?? {}) as UsageData, day, month);
        if (isLike) {
            const limit = limits.dailyLikes ?? FAIR_DAILY_LIKES;
            const effectiveLimit = limit + Math.min(usage.rewardedLikes ?? 0, MAX_FREE_REWARDED_LIKES);
            if ((usage.likes ?? 0) >= effectiveLimit) {
                throw new HttpsError("resource-exhausted", "daily-like-limit");
            }
            usage.likes = (usage.likes ?? 0) + 1;
            transaction.set(usageRef, usage, { merge: true });
        }

        transaction.set(interactionRef, {
            fromUserId: uid, toUserId: targetUid, type: isLike ? "like" : "pass",
            timestamp: Date.now(), day,
        });
        if (!isLike) {
            transaction.set(usageRef, { ...usage, lastPass: targetUid, lastPassDay: day }, { merge: true });
            return { result: "pass", idempotent: false };
        }
        if (reverse.data()?.type === "like") {
            transaction.set(matchRef, { users: [uid, targetUid].sort(), timestamp: Date.now() }, { merge: true });
            if (targetUser.data()?.incognito === true) {
                transaction.set(targetUserRef, { visibleTo: FieldValue.arrayUnion(uid) }, { merge: true });
            }
            return { result: "mutual_match", idempotent: false };
        }
        if (targetUser.data()?.incognito === true) {
            transaction.set(targetUserRef, { visibleTo: FieldValue.arrayUnion(uid) }, { merge: true });
        }
        return { result: "single_like", idempotent: false };
    });
});

export const switchRoom = onCall({ region: "us-central1", enforceAppCheck: false }, async (request) => {
    const uid = requireUid(request);
    const code = (request.data as { languageCode?: unknown }).languageCode;
    if (typeof code !== "string" || !/^[A-Za-z-]{2,16}$/.test(code)) {
        throw new HttpsError("invalid-argument", "Invalid language code.");
    }
    const tier = await resolveTierFor(uid, request);
    const limits = await limitsFor(tier);
    const userRef = db.collection("users").doc(uid);
    const usageRef = db.collection("usage").doc(uid);
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);

    // Slot selection, daily room-switch quota and the active room must change as one
    // unit. Keeping these as separate read/write operations allowed simultaneous calls
    // to observe the same free slot and exceed a plan's room allowance.
    return db.runTransaction(async (transaction) => {
        const [current, usageSnapshot] = await Promise.all([
            transaction.get(userRef), transaction.get(usageRef),
        ]);
        const user = current.data() ?? {};
        const rooms = Array.isArray(user.roomLanguageCodes)
            ? user.roomLanguageCodes.filter((room): room is string => typeof room === "string")
            : [user.roomLanguageCode].filter((room): room is string => typeof room === "string");
        if (user.roomLanguageCode === code) return { roomLanguageCodes: rooms, activeRoom: code };
        if (!rooms.includes(code) && limits.roomSlots !== null && rooms.length >= limits.roomSlots) {
            throw new HttpsError("resource-exhausted", "room-slot-limit");
        }

        const usage = resetUsage((usageSnapshot.data() ?? {}) as UsageData, day, month);
        // The mandatory first-room picker is onboarding, not a billable room switch.
        if (rooms.length > 0) {
            const limit = limits.roomSwitchesPerDay ?? Number.MAX_SAFE_INTEGER;
            if ((usage.roomSwitches ?? 0) >= limit) {
                throw new HttpsError("resource-exhausted", "daily-room-switch-limit");
            }
            usage.roomSwitches = (usage.roomSwitches ?? 0) + 1;
            transaction.set(usageRef, usage, { merge: true });
        }

        const nextRooms = rooms.includes(code) ? rooms : [...rooms, code];
        transaction.set(userRef, { roomLanguageCodes: nextRooms, roomLanguageCode: code }, { merge: true });
        return { roomLanguageCodes: nextRooms, activeRoom: code };
    });
});

export const unlockLikedMe = onCall({ region: "us-central1", enforceAppCheck: false }, async (request) => {
    const uid = requireUid(request);
    const profileUid = (request.data as { profileUid?: unknown }).profileUid;
    if (typeof profileUid !== "string" || !profileUid) throw new HttpsError("invalid-argument", "Invalid profile.");
    const limits = await limitsFor(await resolveTierFor(uid, request));
    if (limits.likedMeUnlocksPerDay === 0) throw new HttpsError("permission-denied", "upgrade-required");
    await consumeDaily(uid, "likedMeUnlocks", limits.likedMeUnlocksPerDay, "daily-liked-me-limit");
    await db.collection("liked_me_unlocks").doc(`${uid}_${profileUid}`).set({ uid, profileUid, unlockedAt: Date.now() });
    return { unlocked: true };
});

/** Records a profile visit unless a Premium user has explicitly enabled incognito mode. */
export const recordProfileVisit = onCall({ region: "us-central1", enforceAppCheck: false }, async (request) => {
    const uid = requireUid(request);
    const profileUid = (request.data as { profileUid?: unknown }).profileUid;
    if (typeof profileUid !== "string" || !profileUid || profileUid === uid) {
        throw new HttpsError("invalid-argument", "Invalid profile.");
    }
    const [viewer, target] = await Promise.all([
        db.collection("users").doc(uid).get(), db.collection("public_profiles").doc(profileUid).get(),
    ]);
    if (!target.exists) throw new HttpsError("not-found", "Profile not found.");
    const isIncognito = await resolveTierFor(uid, request) === "PREMIUM" && viewer.data()?.incognito === true;
    if (isIncognito) return { recorded: false };
    await db.collection("profileViews").doc(`${profileUid}_${uid}`).set({
        profileUid, viewerUid: uid, visitedAt: Date.now(),
    }, { merge: true });
    return { recorded: true };
});

export const setIncognito = onCall({ region: "us-central1", enforceAppCheck: false }, async (request) => {
    const uid = requireUid(request);
    const enabled = (request.data as { enabled?: unknown }).enabled;
    if (typeof enabled !== "boolean") throw new HttpsError("invalid-argument", "Invalid incognito preference.");
    if (enabled && await resolveTierFor(uid, request) !== "PREMIUM") {
        throw new HttpsError("permission-denied", "upgrade-required");
    }
    await db.collection("users").doc(uid).set({ incognito: enabled }, { merge: true });
    return { enabled };
});

export const rewind = onCall({ region: "us-central1", enforceAppCheck: false }, async (request) => {
    const uid = requireUid(request);
    const limits = await limitsFor(await resolveTierFor(uid, request));
    if (limits.rewindsPerDay === 0) throw new HttpsError("permission-denied", "upgrade-required");
    const usageRef = db.collection("usage").doc(uid);
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);

    // Consume the rewind and remove its pass in the same transaction. Otherwise two
    // concurrent taps could both spend quota against the same last pass.
    return db.runTransaction(async (transaction) => {
        const usageSnapshot = await transaction.get(usageRef);
        const usage = resetUsage((usageSnapshot.data() ?? {}) as UsageData, day, month);
        const target = usage.lastPass;
        if (typeof target !== "string") throw new HttpsError("failed-precondition", "no-pass-to-rewind");
        const limit = limits.rewindsPerDay ?? Number.MAX_SAFE_INTEGER;
        if ((usage.rewinds ?? 0) >= limit) {
            throw new HttpsError("resource-exhausted", "daily-rewind-limit");
        }
        usage.rewinds = (usage.rewinds ?? 0) + 1;
        transaction.delete(db.collection("interactions").doc(`${uid}_${target}`));
        transaction.set(usageRef, {
            ...usage,
            lastPass: FieldValue.delete(),
            lastPassDay: FieldValue.delete(),
        }, { merge: true });
        return { profileUid: target };
    });
});

export const activateBoost = onCall({ region: "us-central1", enforceAppCheck: false }, async (request) => {
    const uid = requireUid(request);
    const limits = await limitsFor(await resolveTierFor(uid, request));
    if (limits.boostsPerMonth <= 0) throw new HttpsError("permission-denied", "upgrade-required");
    const zone = await userTimeZone(uid);
    const day = dayKey(zone);
    const month = monthKey(zone);
    const usageRef = db.collection("usage").doc(uid);
    await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(usageRef);
        const usage = resetUsage((snapshot.data() ?? {}) as UsageData, day, month);
        if ((usage.boosts ?? 0) >= limits.boostsPerMonth) throw new HttpsError("resource-exhausted", "monthly-boost-limit");
        transaction.set(usageRef, { ...usage, boosts: (usage.boosts ?? 0) + 1 }, { merge: true });
    });
    const boostUntil = Date.now() + 30 * 60 * 1000;
    await db.collection("users").doc(uid).set({ boostUntil }, { merge: true });
    return { boostUntil };
});

export const updateTimeZone = onCall({ region: "us-central1", enforceAppCheck: false }, async (request) => {
    const uid = requireUid(request);
    const timeZone = (request.data as { timeZone?: unknown }).timeZone;
    if (typeof timeZone !== "string" || timeZone.length > 64) throw new HttpsError("invalid-argument", "Invalid time zone.");
    try { Intl.DateTimeFormat(undefined, { timeZone }); } catch { throw new HttpsError("invalid-argument", "Invalid time zone."); }
    const userRef = db.collection("users").doc(uid);
    const current = await userRef.get();
    const lastChangedAt = Number(current.data()?.timeZoneChangedAt ?? 0);
    if (Date.now() - lastChangedAt < 7 * 24 * 60 * 60 * 1000) {
        throw new HttpsError("failed-precondition", "timezone-change-cooldown");
    }
    await userRef.set({ timeZone, timeZoneChangedAt: Date.now() }, { merge: true });
    return { timeZone };
});

/** Read-only, server-clock-based usage view for optimistic client gates. */
export const getUsage = onCall({ region: "us-central1", enforceAppCheck: false }, async (request) => {
    const uid = requireUid(request);
    const zone = await userTimeZone(uid);
    const today = dayKey(zone);
    const snapshot = await db.collection("usage").doc(uid).get();
    const usage = (snapshot.data() ?? {}) as UsageData;
    if (usage.day !== today) return { day: today, likes: 0, messages: 0, newChats: 0, roomSwitches: 0 };
    return {
        day: today,
        likes: Number(usage.likes ?? 0),
        messages: Number(usage.messages ?? 0),
        newChats: Number(usage.newChats ?? 0),
        roomSwitches: Number(usage.roomSwitches ?? 0),
    };
});

/**
 * One-time, resumable administrator operation. It seeds the canonical plan document and
 * creates public profiles for existing accounts in bounded batches. The caller must carry
 * the Firebase custom claim `admin: true`; no client-side role flag is trusted.
 */
export const bootstrapMonetization = onCall({ region: "us-central1", enforceAppCheck: false }, async (request) => {
    requireUid(request);
    if (request.auth?.token?.admin !== true) throw new HttpsError("permission-denied", "administrator-required");
    const afterUid = (request.data as { afterUid?: unknown }).afterUid;
    if (afterUid !== undefined && typeof afterUid !== "string") throw new HttpsError("invalid-argument", "Invalid cursor.");
    await db.collection("config").doc("plans").set(serializablePlans(), { merge: true });
    let query = db.collection("users").orderBy(admin.firestore.FieldPath.documentId()).limit(250);
    if (typeof afterUid === "string" && afterUid) query = query.startAfter(afterUid);
    const users = await query.get();
    const batch = db.batch();
    users.docs.forEach((document) => {
        const data = document.data();
        batch.set(db.collection("public_profiles").doc(document.id), {
            displayName: data.displayName ?? "",
            birthDateMillis: data.birthDateMillis ?? null,
            countryCode: data.countryCode ?? null,
            languageCode: data.languageCode ?? null,
            avatarUrl: data.avatarUrl ?? null,
            discoveryPhotos: Array.isArray(data.discoveryPhotos) ? data.discoveryPhotos : [],
            bio: data.bio ?? "",
            interests: Array.isArray(data.interests) ? data.interests : [],
            verified: data.verified === true,
            lastActiveTimestamp: data.lastActiveTimestamp ?? 0,
            isMookActive: data.isMookActive === true,
            discoverVisible: data.discoverVisible !== false,
            incognito: data.incognito === true,
            visibleTo: Array.isArray(data.visibleTo) ? data.visibleTo : [],
            boostUntil: typeof data.boostUntil === "number" ? data.boostUntil : 0,
        }, { merge: true });
    });
    await batch.commit();
    const last = users.docs.at(-1)?.id ?? null;
    return { migrated: users.size, nextAfterUid: users.size === 250 ? last : null };
});

/** Keeps only the fields Discover is allowed to expose outside the profile owner. */
export const syncPublicProfile = onDocumentWritten("users/{uid}", async (event) => {
    const after = event.data?.after;
    if (!after?.exists) {
        await db.collection("public_profiles").doc(event.params.uid).delete();
        return;
    }
    const data = after.data() ?? {};
    const publicProfile = {
        displayName: data.displayName ?? "",
        birthDateMillis: data.birthDateMillis ?? null,
        countryCode: data.countryCode ?? null,
        languageCode: data.languageCode ?? null,
        avatarUrl: data.avatarUrl ?? null,
        discoveryPhotos: Array.isArray(data.discoveryPhotos) ? data.discoveryPhotos : [],
        bio: data.bio ?? "",
        interests: Array.isArray(data.interests) ? data.interests : [],
        verified: data.verified === true,
        lastActiveTimestamp: data.lastActiveTimestamp ?? 0,
        isMookActive: data.isMookActive === true,
        discoverVisible: data.discoverVisible !== false,
        incognito: data.incognito === true,
        visibleTo: Array.isArray(data.visibleTo) ? data.visibleTo : [],
        boostUntil: typeof data.boostUntil === "number" ? data.boostUntil : 0,
    };
    await db.collection("public_profiles").doc(event.params.uid).set(publicProfile, { merge: true });
});
