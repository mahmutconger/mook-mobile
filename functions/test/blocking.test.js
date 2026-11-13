const assert = require("node:assert/strict");
const { test } = require("node:test");
const { BLOCKED_USER_ERROR, blockedUsersOf, isBlockedBetween } = require("../lib/blocking");

test("Engel alanı yoksa kimse engelli değildir", () => {
    assert.equal(blockedUsersOf(undefined).size, 0);
    assert.equal(blockedUsersOf(null).size, 0);
    assert.equal(blockedUsersOf({}).size, 0);
});

test("Dizi olmayan veya bozuk girdiler sessizce yok sayılır", () => {
    assert.equal(blockedUsersOf({ blockedUsers: "uidB" }).size, 0);
    const blocked = blockedUsersOf({ blockedUsers: ["uidB", 42, null, "", { uid: "x" }, "uidC"] });
    assert.deepEqual([...blocked].sort(), ["uidB", "uidC"]);
});

test("Gönderen karşı tarafı engellediyse engel vardır", () => {
    assert.equal(isBlockedBetween("uidA", { blockedUsers: ["uidB"] }, "uidB", {}), true);
});

test("Karşı taraf göndereni engellediyse de engel vardır (iki yönlü)", () => {
    assert.equal(isBlockedBetween("uidA", {}, "uidB", { blockedUsers: ["uidA"] }), true);
});

test("Karşılıklı engelde (A B'yi, B de A'yı engelledi) engel sürer ve hata fırlatılmaz", () => {
    const a = { blockedUsers: ["uidB"] };
    const b = { blockedUsers: ["uidA"] };
    assert.equal(isBlockedBetween("uidA", a, "uidB", b), true);
    assert.equal(isBlockedBetween("uidB", b, "uidA", a), true);
});

test("Engel kaldırılınca (liste boşalınca) ikili yeniden mesajlaşabilir", () => {
    assert.equal(isBlockedBetween("uidA", { blockedUsers: [] }, "uidB", { blockedUsers: [] }), false);
});

test("Üçüncü kişilerin engellenmesi bu ikiliyi etkilemez", () => {
    assert.equal(isBlockedBetween("uidA", { blockedUsers: ["uidC"] }, "uidB", { blockedUsers: ["uidD"] }), false);
});

test("Belgeler eksik olsa bile engel yok sayılır, hata fırlatılmaz", () => {
    assert.equal(isBlockedBetween("uidA", undefined, "uidB", undefined), false);
});

test("İstemcinin eşlediği hata metni sabittir", () => {
    // İstemcideki chatSendErrorFor bu metni ChatSendError.BLOCKED'a çevirir.
    assert.equal(BLOCKED_USER_ERROR, "blocked-user");
});
