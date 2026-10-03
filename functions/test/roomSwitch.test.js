const assert = require("node:assert/strict");
const { test } = require("node:test");
const { planRoomSwitch, RoomSwitchLimitError } = require("../lib/roomSwitch");

// Varsayılan girdi: tek slotlu Ücretsiz kullanıcı, TR odasında, bugün hiç değişiklik yapmamış.
function input(overrides = {}) {
    return {
        rooms: ["TR"],
        activeRoom: "TR",
        lastActiveAt: { TR: 1_000 },
        target: "DE",
        roomSlots: 1,
        switchesUsedToday: 0,
        dailySwitchLimit: 1,
        ...overrides,
    };
}

test("Ücretsiz kullanıcı tek slot doluyken odasını kapasite hatası almadan değiştirebilir", () => {
    const plan = planRoomSwitch(input());
    assert.equal(plan.kind, "switch");
    assert.deepEqual(plan.rooms, ["DE"]);
    assert.equal(plan.activeRoom, "DE");
    assert.deepEqual(plan.evictedRooms, ["TR"]);
    assert.equal(plan.chargesQuota, true);
});

test("Ekonomik kullanıcı 3 slot doluyken en uzun süredir kullanılmayan odayı takas eder", () => {
    const plan = planRoomSwitch(input({
        rooms: ["TR", "EN-US", "FR"],
        activeRoom: "FR",
        lastActiveAt: { TR: 3_000, "EN-US": 1_000, FR: 5_000 },
        roomSlots: 3,
        dailySwitchLimit: 3,
    }));
    assert.deepEqual(plan.evictedRooms, ["EN-US"]);
    assert.deepEqual(plan.rooms, ["TR", "FR", "DE"]);
    assert.equal(plan.rooms.length, 3);
});

test("günlük hak dolduysa kapasiteden önce özel daily-room-switch-limit hatası verilir", () => {
    assert.throws(
        () => planRoomSwitch(input({ switchesUsedToday: 1 })),
        (error) => error instanceof RoomSwitchLimitError && error.message === "daily-room-switch-limit",
    );
});

test("ödüllü bonusla artırılmış günlük limit bir ek değişikliğe izin verir", () => {
    const plan = planRoomSwitch(input({ switchesUsedToday: 1, dailySwitchLimit: 2 }));
    assert.equal(plan.kind, "switch");
});

test("ilk zorunlu oda seçimi ücretsizdir ve limit dolu olsa bile engellenmez", () => {
    const plan = planRoomSwitch(input({ rooms: [], activeRoom: null, lastActiveAt: {}, switchesUsedToday: 99 }));
    assert.equal(plan.chargesQuota, false);
    assert.deepEqual(plan.rooms, ["DE"]);
});

test("zaten açık bir odaya geçiş slot değiştirmez ama günlük haktan düşer", () => {
    const plan = planRoomSwitch(input({ rooms: ["TR", "DE"], roomSlots: 3, dailySwitchLimit: 3 }));
    assert.deepEqual(plan.rooms, ["TR", "DE"]);
    assert.deepEqual(plan.evictedRooms, []);
    assert.equal(plan.chargesQuota, true);
});

test("aktif odayı yeniden seçmek hiçbir şey yapmaz ve hak düşmez", () => {
    const plan = planRoomSwitch(input({ target: "TR", switchesUsedToday: 1 }));
    assert.equal(plan.kind, "noop");
});

test("kademe düşüşü sonrası fazla açık odalar takasla slot sınırına indirilir", () => {
    const plan = planRoomSwitch(input({
        rooms: ["TR", "EN-US", "FR", "IT", "ES"],
        activeRoom: "TR",
        lastActiveAt: { TR: 9_000, "EN-US": 1_000, FR: 2_000, IT: 3_000, ES: 8_000 },
        roomSlots: 3,
        dailySwitchLimit: 3,
    }));
    assert.deepEqual(plan.evictedRooms, ["EN-US", "FR", "IT"]);
    assert.deepEqual(plan.rooms, ["TR", "ES", "DE"]);
});

test("sınırsız slotlu planda hiçbir oda çıkarılmaz", () => {
    const plan = planRoomSwitch(input({ rooms: ["TR", "EN-US", "FR", "IT"], roomSlots: null, dailySwitchLimit: null }));
    assert.deepEqual(plan.evictedRooms, []);
    assert.equal(plan.rooms.length, 5);
});

// --- planRoomTrim: kademe düşüşünde sunucu tarafı oda kırpması ---
const { planRoomTrim } = require("../lib/roomSwitch");

test("planRoomTrim: sınırsız slotta hiçbir oda kapatılmaz", () => {
    const plan = planRoomTrim({ rooms: ["TR", "DE", "FR"], activeRoom: "DE", lastActiveAt: {}, roomSlots: null });
    assert.deepEqual(plan, { rooms: ["TR", "DE", "FR"], activeRoom: "DE", closedRooms: [] });
});

test("planRoomTrim: kapasite aşılmamışsa değişiklik yok", () => {
    const plan = planRoomTrim({ rooms: ["TR", "DE"], activeRoom: "TR", lastActiveAt: {}, roomSlots: 3 });
    assert.deepEqual(plan.closedRooms, []);
    assert.deepEqual(plan.rooms, ["TR", "DE"]);
});

test("planRoomTrim: Premium → Ücretsiz düşüşünde yalnızca aktif oda kalır", () => {
    const plan = planRoomTrim({
        rooms: ["TR", "DE", "FR", "ES"],
        activeRoom: "FR",
        lastActiveAt: { TR: 4_000, DE: 3_000, FR: 1_000, ES: 2_000 },
        roomSlots: 1,
    });
    assert.deepEqual(plan, { rooms: ["FR"], activeRoom: "FR", closedRooms: ["TR", "DE", "ES"] });
});

test("planRoomTrim: aktif oda + en son kullanılanlar korunur, sıra bozulmaz", () => {
    const plan = planRoomTrim({
        rooms: ["TR", "DE", "FR", "ES", "IT"],
        activeRoom: "IT",
        lastActiveAt: { TR: 1_000, DE: 5_000, FR: 2_000, ES: 4_000, IT: 3_000 },
        roomSlots: 3,
    });
    assert.deepEqual(plan.rooms, ["DE", "ES", "IT"]);
    assert.equal(plan.activeRoom, "IT");
    assert.deepEqual(plan.closedRooms, ["TR", "FR"]);
});

test("planRoomTrim: aktif oda yoksa korunanlar içinden en son kullanılan aktif olur", () => {
    const plan = planRoomTrim({
        rooms: ["TR", "DE", "FR"],
        activeRoom: null,
        lastActiveAt: { TR: 1_000, DE: 3_000, FR: 2_000 },
        roomSlots: 2,
    });
    assert.deepEqual(plan.rooms, ["DE", "FR"]);
    assert.equal(plan.activeRoom, "DE");
});

test("planRoomTrim: eşitlikte listede önce gelen korunur; 0 slot en az 1 sayılır", () => {
    const plan = planRoomTrim({ rooms: ["TR", "DE"], activeRoom: "XX", lastActiveAt: {}, roomSlots: 0 });
    assert.deepEqual(plan.rooms, ["TR"]);
    assert.equal(plan.activeRoom, "TR");
    assert.deepEqual(plan.closedRooms, ["DE"]);
});
