const assert = require("node:assert/strict");
const { test } = require("node:test");
const { buildPublicProfile, computeAge, nextAgeRefreshAt, todayIn } = require("../lib/profilePrivacy");

const IST = "Europe/Istanbul";
const at = (y, m, d, h = 12) => Date.UTC(y, m - 1, d, h); // UTC öğlen
const birth = (y, m, d) => Date.UTC(y, m - 1, d);

test("Yaş doğum gününde artar, bir gün önce artmaz", () => {
    assert.equal(computeAge(birth(2000, 6, 15), at(2026, 6, 14), IST), 25);
    assert.equal(computeAge(birth(2000, 6, 15), at(2026, 6, 15), IST), 26);
});

test("29 Şubat doğumlu: artık olmayan yılda 28 Şubat'ta büyümez, 1 Mart'ta büyür", () => {
    assert.equal(computeAge(birth(2004, 2, 29), at(2025, 2, 28), IST), 20);
    assert.equal(computeAge(birth(2004, 2, 29), at(2025, 3, 1), IST), 21);
    assert.equal(computeAge(birth(2004, 2, 29), at(2028, 2, 29), IST), 24);
});

test("'Bugün' kullanıcının saat dilimine göre hesaplanır", () => {
    // 2026-06-14 22:30 UTC = İstanbul'da 15 Haziran 01:30 → doğum günü başlamıştır.
    const lateUtc = Date.UTC(2026, 5, 14, 22, 30);
    assert.equal(computeAge(birth(2000, 6, 15), lateUtc, IST), 26);
    assert.equal(computeAge(birth(2000, 6, 15), lateUtc, "UTC"), 25);
    assert.deepEqual(todayIn("Geçersiz/Dilim", lateUtc), { year: 2026, month: 6, day: 14 });
});

test("Geçersiz veya gelecekteki doğum tarihi yaş üretmez", () => {
    assert.equal(computeAge(null, at(2026, 1, 1), IST), null);
    assert.equal(computeAge("2000-01-01", at(2026, 1, 1), IST), null);
    assert.equal(computeAge(birth(2030, 1, 1), at(2026, 1, 1), IST), null);
});

test("Herkese açık profil doğum tarihini İÇERMEZ, yalnızca yaş içerir", () => {
    const publicProfile = buildPublicProfile({ displayName: "Ayşe", birthDateMillis: birth(2000, 1, 1), email: "a@b.com" }, at(2026, 6, 1), IST);
    assert.equal(publicProfile.age, 26);
    assert.ok(!("birthDateMillis" in publicProfile));
    assert.ok(!("email" in publicProfile));
});

test("Bir sonraki yaş yenileme anı doğum gününden önceki en erken saat dilimine göre", () => {
    const now = at(2026, 6, 1);
    assert.equal(nextAgeRefreshAt(birth(2000, 6, 15), now, IST), Date.UTC(2026, 5, 15) - 14 * 3_600_000);
    // Bu yıl geçtiyse gelecek yıl.
    assert.equal(nextAgeRefreshAt(birth(2000, 3, 1), now, IST), Date.UTC(2027, 2, 1) - 14 * 3_600_000);
    // Doğum günü penceresinin içindeysek 6 saat sonra yeniden dene.
    const insideWindow = Date.UTC(2026, 5, 14, 12);
    assert.equal(nextAgeRefreshAt(birth(2000, 6, 15), insideWindow, IST), insideWindow + 6 * 3_600_000);
    assert.equal(nextAgeRefreshAt(undefined, now, IST), null);
});
