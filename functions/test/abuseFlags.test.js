const assert = require("node:assert/strict");
const { test } = require("node:test");
const { applyAbuseSignal, statusForScore, MAX_SOURCES_PER_SIGNAL } = require("../lib/abuseFlagRules");

function apply(existing, signal, sourceKey, now = 1) {
    return applyAbuseSignal({ uid: "u1", existing, signal, sourceKey, now });
}

test("İlk sinyal belgeyi oluşturur", () => {
    const { document, escalated } = apply(null, "user_reported", "reporter1", 100);
    assert.equal(document.uid, "u1");
    assert.equal(document.score, 3);
    assert.equal(document.status, "clear");
    assert.equal(document.createdAt, 100);
    assert.equal(escalated, false);
});

test("Aynı şikâyetçinin tekrarları puanı şişirmez, sayacı artırır", () => {
    let doc = apply(null, "user_reported", "r1").document;
    doc = apply(doc, "user_reported", "r1").document;
    assert.equal(doc.score, 3);
    assert.equal(doc.signalCounts.user_reported, 2);
});

test("Tekil şikâyetçiler eşikleri aşınca durum yükselir ve zamanı kaydedilir", () => {
    let result = apply(null, "user_reported", "r1");
    result = apply(result.document, "user_reported", "r2", 50);
    assert.equal(result.document.status, "watch");
    assert.equal(result.escalated, true);
    assert.equal(result.document.escalatedAt, 50);
    result = apply(result.document, "user_reported", "r3");
    result = apply(result.document, "user_reported", "r4", 90);
    assert.equal(result.document.score, 12);
    assert.equal(result.document.status, "review");
    assert.equal(result.document.escalatedAt, 90);
});

test("Çoklu hesap deneme sinyali tek başına izlemeye alır", () => {
    assert.equal(apply(null, "multi_account_trial", "device1").document.status, "watch");
});

test("Durum moderatör temizlemeden kendiliğinden düşmez", () => {
    const existing = { status: "review", score: 0, signalSources: {} };
    assert.equal(apply(existing, "message_rate_limit", "h1").document.status, "review");
});

test("Kaynak listesi sınırlıdır", () => {
    let doc = null;
    for (let i = 0; i < MAX_SOURCES_PER_SIGNAL + 10; i++) doc = apply(doc, "message_rate_limit", `h${i}`).document;
    assert.equal(doc.signalSources.message_rate_limit.length, MAX_SOURCES_PER_SIGNAL);
    assert.equal(doc.signalCounts.message_rate_limit, MAX_SOURCES_PER_SIGNAL + 10);
});

test("Puan eşikleri", () => {
    assert.equal(statusForScore(4), "clear");
    assert.equal(statusForScore(5), "watch");
    assert.equal(statusForScore(10), "review");
});
