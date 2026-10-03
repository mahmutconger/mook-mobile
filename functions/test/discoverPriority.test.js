const assert = require("node:assert/strict");
const { test } = require("node:test");
const { discoverPriority, sortByDiscoverPriority, tierPriority } = require("../lib/discoverPriority");

const NOW = 1_000_000;

test("Öncelik: Boost > Premium > Standart > diğerleri", () => {
    assert.equal(discoverPriority({ boostUntil: NOW + 1, subscriptionTier: "FREE" }, NOW), 0);
    assert.equal(discoverPriority({ boostUntil: NOW - 1, subscriptionTier: "PREMIUM" }, NOW), 1);
    assert.equal(discoverPriority({ boostUntil: 0, subscriptionTier: "STANDARD" }, NOW), 2);
    assert.equal(discoverPriority({ boostUntil: null, subscriptionTier: "ECONOMY" }, NOW), 3);
    assert.equal(tierPriority(undefined), 3);
    assert.equal(tierPriority("hacker"), 3);
});

test("Sıralama kararlıdır: grup içi sıra korunur", () => {
    const items = [
        { id: "free1", p: 3 }, { id: "std1", p: 2 }, { id: "boost", p: 0 },
        { id: "prem1", p: 1 }, { id: "free2", p: 3 }, { id: "prem2", p: 1 },
    ];
    const sorted = sortByDiscoverPriority(items, (item) => item.p).map((item) => item.id);
    assert.deepEqual(sorted, ["boost", "prem1", "prem2", "std1", "free1", "free2"]);
});
