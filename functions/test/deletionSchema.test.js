const assert = require("node:assert/strict");
const { test } = require("node:test");
const { buildDeletionPlan } = require("../lib/deletionSchema");

const UID = "user_123";

function has(plan, predicate) {
    return plan.some(predicate);
}

test("Kullanıcının kendi belgeleri (users, usage, public_profiles, customers) silinir", () => {
    const plan = buildDeletionPlan(UID, null);
    for (const collection of ["users", "usage", "public_profiles", "customers"]) {
        assert.ok(has(plan, (t) => t.kind === "document" && t.collection === collection && t.docId === UID), collection);
    }
});

test("Sohbetler mesaj alt koleksiyonlarıyla birlikte (recursive) silinir", () => {
    const plan = buildDeletionPlan(UID, null);
    assert.ok(has(plan, (t) => t.kind === "query" && t.collection === "chats"
        && t.field === "users" && t.operator === "array-contains" && t.recursive === true));
    assert.ok(has(plan, (t) => t.kind === "collectionGroupQuery" && t.collectionGroup === "messages" && t.field === "senderUid"));
});

test("profileViews ve liked_me_unlocks iki yönde de silinir", () => {
    const plan = buildDeletionPlan(UID, null);
    for (const [collection, field] of [
        ["profileViews", "viewerUid"], ["profileViews", "profileUid"],
        ["liked_me_unlocks", "uid"], ["liked_me_unlocks", "profileUid"],
    ]) {
        assert.ok(has(plan, (t) => t.kind === "query" && t.collection === collection && t.field === field), `${collection}.${field}`);
    }
});

test("device_trial_ledger belgesi silinmez, yalnızca uid referansı çıkarılır", () => {
    const plan = buildDeletionPlan(UID, null);
    const ledger = plan.filter((t) => t.collection === "device_trial_ledger");
    assert.equal(ledger.length, 1);
    assert.equal(ledger[0].kind, "arrayRemove");
    assert.equal(ledger[0].field, "uids");
});

test("Her hedef yalnızca bu kullanıcıyı hedefler ve tekrarlanmaz", () => {
    const plan = buildDeletionPlan(UID, "a@b.com");
    const keys = plan.map((t) => JSON.stringify([t.kind, t.collection ?? t.collectionGroup, t.field ?? "", t.docId ?? ""]));
    assert.equal(new Set(keys).size, keys.length);
    for (const target of plan) {
        const value = target.kind === "document" ? target.docId : target.value;
        assert.ok(value === UID || value === "a@b.com", JSON.stringify(target));
    }
});

test("E-posta varsa mail kuyruğu da silinir; yoksa plana eklenmez", () => {
    assert.ok(has(buildDeletionPlan(UID, "a@b.com"), (t) => t.collection === "mail" && t.value === "a@b.com"));
    assert.ok(!has(buildDeletionPlan(UID, null), (t) => t.collection === "mail"));
});

test("Boş uid ile plan oluşturulamaz", () => {
    assert.throws(() => buildDeletionPlan("  ", null));
});
