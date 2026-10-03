const assert = require("node:assert/strict");
const { test } = require("node:test");
const { deriveTokenKey, encodeEntryToken, decodeEntryToken, planLikedMeEntries } = require("../lib/likedMePlan");
const { likedMeUnlocksRemaining } = require("../lib/rewards");

const KEY = deriveTokenKey("test-secret-0123456789");
const NOW = Date.UTC(2026, 9, 1);

function profile(overrides = {}) {
    return {
        displayName: "Ayşe", age: 26, countryCode: "TR", languageCode: "TR",
        photoUrl: "https://x/p.jpg", bio: "Merhaba", verified: false, isMookActive: true, incognito: false, visibleTo: [],
        ...overrides,
    };
}

function candidate(likerUid, overrides = {}) {
    return { likerUid, likedAt: 1, viewerAlreadyActed: false, unlocked: false, profile: profile(), ...overrides };
}

test("Jeton yalnızca kendi izleyicisi için çözülür", () => {
    const token = encodeEntryToken(KEY, "viewer", "liker");
    assert.equal(decodeEntryToken(KEY, token, "viewer"), "liker");
    assert.equal(decodeEntryToken(KEY, token, "someone_else"), null);
    assert.equal(decodeEntryToken(deriveTokenKey("another-secret-0123456"), token, "viewer"), null);
});

test("Bozulmuş veya rastgele jeton reddedilir; jeton uid içermez", () => {
    const token = encodeEntryToken(KEY, "viewer", "liker_uid_123");
    const tampered = token.slice(0, -2) + (token.endsWith("A") ? "BB" : "AA");
    assert.equal(decodeEntryToken(KEY, tampered, "viewer"), null);
    assert.equal(decodeEntryToken(KEY, "abc", "viewer"), null);
    assert.ok(!Buffer.from(token, "base64url").toString("utf8").includes("liker_uid_123"));
});

test("Kilitli girişte profil bilgisi ve uid dönmez; açık girişte döner", () => {
    const entries = planLikedMeEntries({
        viewerUid: "me",
        candidates: [candidate("a"), candidate("b", { unlocked: true, likedAt: 5 })],
        revealAll: false,
        now: NOW,
        makeToken: (uid) => `t_${uid}`,
    });
    assert.equal(entries[0].entryToken, "t_b");
    assert.equal(entries[0].unlocked, true);
    assert.equal(entries[0].profile.uid, "b");
    assert.equal(entries[0].profile.age, 26);
    assert.equal(entries[1].unlocked, false);
    assert.equal(entries[1].profile, null);
    assert.equal(entries[1].photoUrl, "https://x/p.jpg");
});

test("Premium tüm profilleri açık görür", () => {
    const entries = planLikedMeEntries({ viewerUid: "me", candidates: [candidate("a")], revealAll: true, now: NOW, makeToken: (u) => u });
    assert.equal(entries[0].unlocked, true);
});

test("Zaten karşılık verilen, pasif, silinmiş veya gizli profiller listelenmez", () => {
    const entries = planLikedMeEntries({
        viewerUid: "me",
        candidates: [
            candidate("acted", { viewerAlreadyActed: true }),
            candidate("inactive", { profile: profile({ isMookActive: false }) }),
            candidate("deleted", { profile: null }),
            candidate("hidden", { profile: profile({ incognito: true }) }),
            candidate("visible_incognito", { profile: profile({ incognito: true, visibleTo: ["me"] }) }),
            candidate("me"),
        ],
        revealAll: false,
        now: NOW,
        makeToken: (u) => u,
    });
    assert.deepEqual(entries.map((e) => e.entryToken), ["visible_incognito"]);
});

test("Kalan açma hakkı: Premium sınırsız, Ücretsiz ödüllü reklamla, Standart tabanla", () => {
    assert.equal(likedMeUnlocksRemaining({ tier: "PREMIUM", basePerDay: null, rewardedUnlocksToday: 0, usedToday: 9 }), null);
    assert.equal(likedMeUnlocksRemaining({ tier: "FREE", basePerDay: 0, rewardedUnlocksToday: 1, usedToday: 0 }), 1);
    assert.equal(likedMeUnlocksRemaining({ tier: "FREE", basePerDay: 0, rewardedUnlocksToday: 1, usedToday: 1 }), 0);
    assert.equal(likedMeUnlocksRemaining({ tier: "STANDARD", basePerDay: 5, rewardedUnlocksToday: 3, usedToday: 2 }), 3);
});
