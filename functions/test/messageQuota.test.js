const assert = require("node:assert/strict");
const { test } = require("node:test");
const { planMessageQuota, MessageQuotaError } = require("../lib/messageQuota");

const NOW = 1_700_000_000_000;

function input(overrides = {}) {
    return {
        usage: {},
        dailyMessages: null,
        dailyNewChats: null,
        dailyChars: 1_000,
        monthlyChars: 10_000,
        isNewChat: false,
        translationChars: 0,
        now: NOW,
        rateLimit: 30,
        rateWindowMs: 60_000,
        ...overrides,
    };
}

test("Aynı dildeki mesaj karakter kotasına hiç dokunmaz", () => {
    const plan = planMessageQuota(input({ usage: { translationCharsDaily: 1_000, translationCharsMonthly: 10_000 } }));
    assert.equal(plan.translate, false);
    assert.equal(plan.translationQuotaExhausted, false);
    assert.equal(plan.counters.translationCharsDaily, 1_000);
    assert.equal(plan.counters.translationCharsMonthly, 10_000);
    assert.equal(plan.counters.messages, 1);
});

test("Çevrilen mesaj karakterleri günlük ve aylık sayaçtan düşer", () => {
    const plan = planMessageQuota(input({ usage: { translationCharsDaily: 100, translationCharsMonthly: 500 }, translationChars: 40 }));
    assert.equal(plan.translate, true);
    assert.equal(plan.counters.translationCharsDaily, 140);
    assert.equal(plan.counters.translationCharsMonthly, 540);
});

test("Günlük kota yetmezse mesaj engellenmez: çeviri atlanır, karakter düşülmez", () => {
    const plan = planMessageQuota(input({ usage: { translationCharsDaily: 990 }, translationChars: 20 }));
    assert.equal(plan.translate, false);
    assert.equal(plan.translationQuotaExhausted, true);
    assert.equal(plan.counters.translationCharsDaily, 990);
    assert.equal(plan.counters.messages, 1);
});

test("Aylık kota yetmezse de yumuşak geri dönüş uygulanır", () => {
    const plan = planMessageQuota(input({ usage: { translationCharsMonthly: 9_995 }, translationChars: 10 }));
    assert.equal(plan.translationQuotaExhausted, true);
    assert.equal(plan.counters.translationCharsMonthly, 9_995);
});

test("Kotayı tam dolduran mesaj hâlâ çevrilir", () => {
    const plan = planMessageQuota(input({ usage: { translationCharsDaily: 980 }, translationChars: 20 }));
    assert.equal(plan.translate, true);
    assert.equal(plan.counters.translationCharsDaily, 1_000);
});

test("Günlük mesaj sayısı tavanı her mesaj için geçerlidir", () => {
    assert.throws(
        () => planMessageQuota(input({ dailyMessages: 50, usage: { messages: 50 } })),
        (error) => error instanceof MessageQuotaError && error.code === "daily-message-limit",
    );
});

test("Dakikalık hız sınırı aşılırsa reddedilir; pencere dolunca sıfırlanır", () => {
    assert.throws(
        () => planMessageQuota(input({ usage: { messageWindowStart: NOW - 1_000, messageWindowCount: 30 } })),
        (error) => error.code === "rate-limit-exceeded",
    );
    const plan = planMessageQuota(input({ usage: { messageWindowStart: NOW - 60_000, messageWindowCount: 30 } }));
    assert.equal(plan.counters.messageWindowCount, 1);
    assert.equal(plan.counters.messageWindowStart, NOW);
});

test("Yeni sohbet tavanı uygulanır ve sayaç artar", () => {
    assert.throws(
        () => planMessageQuota(input({ isNewChat: true, dailyNewChats: 3, usage: { newChats: 3 } })),
        (error) => error.code === "daily-new-chat-limit",
    );
    const plan = planMessageQuota(input({ isNewChat: true, dailyNewChats: 3, usage: { newChats: 2 } }));
    assert.equal(plan.counters.newChats, 3);
});
