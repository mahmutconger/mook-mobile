const assert = require("node:assert/strict");
const { test } = require("node:test");
const { activeEntitlementIds, isTierDowngrade, planPlanEnforcement } = require("../lib/tierPolicy");

const NOW = 1_700_000_000_000;
const FREE_LIMITS = { roomSlots: 1, boostsPerMonth: 0, incognito: false };
const STANDARD_LIMITS = { roomSlots: 5, boostsPerMonth: 1, incognito: false };
const PREMIUM_LIMITS = { roomSlots: null, boostsPerMonth: 4, incognito: true };

function user(overrides = {}) {
    return {
        rooms: ["TR"], activeRoom: "TR", lastActiveAt: { TR: 1 },
        boostUntil: null, incognito: false, ...overrides,
    };
}

test("isTierDowngrade: yalnızca daha düşük kademe düşüş sayılır", () => {
    assert.equal(isTierDowngrade("PREMIUM", "FREE"), true);
    assert.equal(isTierDowngrade("PREMIUM", "STANDARD"), true);
    assert.equal(isTierDowngrade("FREE", "ECONOMY"), false);
    assert.equal(isTierDowngrade("STANDARD", "STANDARD"), false);
});

test("Ücretsiz'e düşüş: fazla odalar kapanır, Boost biter, gizli mod kapanır", () => {
    const plan = planPlanEnforcement({
        downgraded: true,
        limits: FREE_LIMITS,
        user: user({
            rooms: ["TR", "DE", "FR"], activeRoom: "DE", lastActiveAt: { TR: 3, DE: 1, FR: 2 },
            boostUntil: NOW + 60_000, incognito: true,
        }),
        now: NOW,
    });
    assert.deepEqual(plan.roomTrim.rooms, ["DE"]);
    assert.deepEqual(plan.roomTrim.closedRooms, ["TR", "FR"]);
    assert.equal(plan.endBoost, true);
    assert.equal(plan.disableIncognito, true);
});

test("Premium → Standart: Boost hakkı olsa da aktif Boost sonlandırılır", () => {
    const plan = planPlanEnforcement({
        downgraded: true, limits: STANDARD_LIMITS, user: user({ boostUntil: NOW + 1 }), now: NOW,
    });
    assert.equal(plan.endBoost, true);
    assert.equal(plan.roomTrim, null);
});

test("Düşüş yoksa ve sınırlar içindeyse hiçbir değişiklik önerilmez (idempotent)", () => {
    const plan = planPlanEnforcement({
        downgraded: false, limits: PREMIUM_LIMITS,
        user: user({ rooms: ["TR", "DE"], boostUntil: NOW + 1, incognito: true }), now: NOW,
    });
    assert.deepEqual(plan, { roomTrim: null, endBoost: false, disableIncognito: false });
});

test("Süresi geçmiş Boost için sonlandırma önerilmez", () => {
    const plan = planPlanEnforcement({
        downgraded: true, limits: FREE_LIMITS, user: user({ boostUntil: NOW - 1 }), now: NOW,
    });
    assert.equal(plan.endBoost, false);
});

test("activeEntitlementIds: süresi dolanlar ve tanınmayanlar elenir, sonuç sıralı", () => {
    const ids = activeEntitlementIds({
        subscriber: {
            entitlements: {
                Premium: { expires_date: new Date(NOW + 1_000).toISOString() },
                standard: { expires_date: new Date(NOW - 1_000).toISOString() },
                economy: { expires_date: null },
                legacy_vip: { expires_date: null },
            },
        },
    }, NOW);
    assert.deepEqual(ids, ["economy", "premium"]);
});

test("activeEntitlementIds: boş yanıt aktif yetki içermez", () => {
    assert.deepEqual(activeEntitlementIds({}, NOW), []);
});
