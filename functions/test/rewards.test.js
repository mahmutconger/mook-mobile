const assert = require("node:assert/strict");
const { test } = require("node:test");
const {
    REWARD_DEFINITIONS,
    adsRemainingToday,
    isRewardKey,
    planLikedMeUnlock,
    planRewardGrant,
} = require("../lib/rewards");

const like = REWARD_DEFINITIONS.bonus_like_v1;
const likedMe = REWARD_DEFINITIONS.bonus_liked_me_unlock_v1;
const room = REWARD_DEFINITIONS.bonus_room_switch_v1;

test("beğeni: günün ilk reklamı tek seferde +5 beğeni verir", () => {
    assert.deepEqual(planRewardGrant(0, like), { granted: true, nextCount: 5 });
});

test("beğeni: günde yalnızca 1 reklam — ikinci reklam ödül vermez", () => {
    assert.deepEqual(planRewardGrant(5, like), { granted: false, reason: "daily-limit" });
    assert.equal(adsRemainingToday(0, like), 1);
    assert.equal(adsRemainingToday(5, like), 0);
});

test("beğeni: eski +1 sisteminden kalan kısmi sayaçta tam ödül sığmazsa verilmez", () => {
    assert.deepEqual(planRewardGrant(1, like), { granted: false, reason: "daily-limit" });
});

test("beni beğenenler: günde 2 reklam, her biri 1 açma", () => {
    assert.deepEqual(planRewardGrant(0, likedMe), { granted: true, nextCount: 1 });
    assert.deepEqual(planRewardGrant(1, likedMe), { granted: true, nextCount: 2 });
    assert.equal(planRewardGrant(2, likedMe).granted, false);
});

test("oda: günde 1 reklam, +1 değişiklik", () => {
    assert.deepEqual(planRewardGrant(0, room), { granted: true, nextCount: 1 });
    assert.equal(planRewardGrant(1, room).granted, false);
});

test("yalnızca bilinen custom_data anahtarları ödül türüdür", () => {
    assert.equal(isRewardKey("bonus_like_v1"), true);
    assert.equal(isRewardKey("bonus_like_v2"), false);
    assert.equal(isRewardKey("__proto__"), false);
});

test("unlockLikedMe: tabanı 0 olan Ücretsiz kullanıcı ödüllü sayaçla profil açabilir", () => {
    assert.deepEqual(planLikedMeUnlock({ tier: "FREE", basePerDay: 0, rewardedUnlocksToday: 1, usedToday: 0 }), { allowed: true });
});

test("unlockLikedMe: ödülsüz Ücretsiz kullanıcı upgrade-required DEĞİL, reklam teklif edilebilir limit alır", () => {
    assert.deepEqual(
        planLikedMeUnlock({ tier: "ECONOMY", basePerDay: 0, rewardedUnlocksToday: 0, usedToday: 0 }),
        { allowed: false, error: "daily-liked-me-limit" },
    );
});

test("unlockLikedMe: ödüllü bonus günlük 2 ile sınırlıdır", () => {
    assert.deepEqual(
        planLikedMeUnlock({ tier: "FREE", basePerDay: 0, rewardedUnlocksToday: 9, usedToday: 2 }),
        { allowed: false, error: "daily-liked-me-limit" },
    );
});

test("unlockLikedMe: Standart günde 5 açar, Premium sınırsızdır, Standart ödüllü bonus almaz", () => {
    assert.deepEqual(planLikedMeUnlock({ tier: "STANDARD", basePerDay: 5, rewardedUnlocksToday: 2, usedToday: 4 }), { allowed: true });
    assert.equal(planLikedMeUnlock({ tier: "STANDARD", basePerDay: 5, rewardedUnlocksToday: 2, usedToday: 5 }).allowed, false);
    assert.deepEqual(planLikedMeUnlock({ tier: "PREMIUM", basePerDay: null, rewardedUnlocksToday: 0, usedToday: 999 }), { allowed: true });
});

test("unlockLikedMe: hiçbir ödüllü yolu olmayan 0 tabanlı kademe upgrade-required alır", () => {
    assert.deepEqual(
        planLikedMeUnlock({ tier: "STANDARD", basePerDay: 0, rewardedUnlocksToday: 0, usedToday: 0 }),
        { allowed: false, error: "upgrade-required" },
    );
});
