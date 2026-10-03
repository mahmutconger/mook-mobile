const assert = require("node:assert/strict");
const { generateKeyPairSync, sign } = require("node:crypto");
const { test } = require("node:test");
const { verifyAdMobSsvQuery } = require("../lib/admobRewardedSsv");

const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const keys = new Map([["4242", publicKey]]);

function signedQuery({ rewardItem = "bonus_like", userId = "firebase-user-123" } = {}) {
    const content = new URLSearchParams({
        ad_network: "5450213213286189855",
        ad_unit: "4818972517",
        custom_data: "bonus_like_v1",
        reward_amount: "1",
        reward_item: rewardItem,
        timestamp: "1790270000000",
        transaction_id: "abcdef0123456789",
        user_id: userId,
    }).toString();
    const signature = sign("sha256", Buffer.from(content), { key: privateKey, dsaEncoding: "der" })
        .toString("base64url");
    return `${content}&signature=${signature}&key_id=4242`;
}

test("accepts a valid AdMob-style ECDSA signed query", () => {
    assert.equal(verifyAdMobSsvQuery(signedQuery(), keys), true);
});

test("rejects a modified callback payload", () => {
    assert.equal(verifyAdMobSsvQuery(signedQuery().replace("reward_amount=1", "reward_amount=2"), keys), false);
});

test("rejects unknown keys and malformed query ordering", () => {
    const query = signedQuery();
    assert.equal(verifyAdMobSsvQuery(query.replace("key_id=4242", "key_id=9999"), keys), false);
    assert.equal(verifyAdMobSsvQuery(query.replace("&signature=", "&key_id=4242&signature="), keys), false);
});
