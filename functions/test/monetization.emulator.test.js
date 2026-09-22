/*
 * End-to-end callable tests. Run with `npm --prefix functions test`.
 *
 * The script deliberately talks to the Functions emulator over HTTP, not to
 * exported implementation helpers. This exercises callable auth parsing,
 * Firestore transactions and the same handlers that production deploys.
 */
const assert = require("node:assert/strict");
const { after, test } = require("node:test");
const admin = require("firebase-admin");

const projectId = process.env.GCLOUD_PROJECT || "walktalk-1123f";
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
const functionsHost = process.env.FUNCTIONS_EMULATOR_HOST || "127.0.0.1:5001";

process.env.FIREBASE_AUTH_EMULATOR_HOST = authHost;
process.env.FIRESTORE_EMULATOR_HOST = firestoreHost;

if (admin.apps.length === 0) admin.initializeApp({ projectId });
const db = admin.firestore();
const runId = `monetization_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
let sequence = 0;

after(async () => {
    await admin.app().delete();
});

function dateKeys(zone = "Europe/Istanbul") {
    const now = new Date();
    return {
        day: new Intl.DateTimeFormat("en-CA", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" }).format(now),
        month: new Intl.DateTimeFormat("en-CA", { timeZone: zone, year: "numeric", month: "2-digit" }).format(now),
    };
}

async function signIn() {
    const response = await fetch(
        `http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator-test-key`,
        {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ returnSecureToken: true }),
        },
    );
    const body = await response.json();
    assert.equal(response.ok, true, JSON.stringify(body));
    return { uid: body.localId, token: body.idToken };
}

async function seedUser(uid, { tier = "FREE", roomLanguageCodes, roomLanguageCode } = {}) {
    const data = {
        displayName: `Test ${uid.slice(0, 6)}`,
        languageCode: "EN-US",
        timeZone: "Europe/Istanbul",
        blockedUsers: [],
    };
    if (roomLanguageCodes) data.roomLanguageCodes = roomLanguageCodes;
    if (roomLanguageCode) data.roomLanguageCode = roomLanguageCode;
    await db.collection("users").doc(uid).set(data);
    if (tier !== "FREE") {
        await db.collection("customers").doc(uid).set({
            revenueCatEntitlements: { [tier.toLowerCase()]: true },
        });
    }
}

async function actor(tier = "FREE") {
    const account = await signIn();
    await seedUser(account.uid, { tier });
    return account;
}

async function targets(count) {
    const writes = [];
    const ids = [];
    for (let index = 0; index < count; index += 1) {
        const uid = `${runId}_target_${sequence++}_${index}`;
        ids.push(uid);
        writes.push(db.collection("users").doc(uid).set({
            displayName: `Target ${index}`,
            languageCode: "EN-US",
            timeZone: "Europe/Istanbul",
            blockedUsers: [],
        }));
    }
    await Promise.all(writes);
    return ids;
}

async function callable(name, token, data) {
    const response = await fetch(`http://${functionsHost}/${projectId}/us-central1/${name}`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ data }),
    });
    const body = await response.json();
    return { httpStatus: response.status, body };
}

function successful(result) {
    return result.httpStatus === 200 && !result.body.error;
}

function assertError(result, code) {
    assert.equal(result.body.error?.status, code, JSON.stringify(result.body));
}

test("parallel free swipes never exceed the 10-like daily quota", { timeout: 30000, concurrency: true }, async () => {
    const user = await actor();
    const profiles = await targets(20);
    const results = await Promise.all(profiles.map((toUserId) => callable("swipe", user.token, { toUserId, isLike: true })));

    assert.equal(results.filter(successful).length, 10, JSON.stringify(results));
    results.filter((result) => !successful(result)).forEach((result) => assertError(result, "RESOURCE_EXHAUSTED"));
    const usage = (await db.collection("usage").doc(user.uid).get()).data();
    assert.equal(usage.likes, 10);
});

test("tier like limits enforce Economy 30, Standard 100 and Premium fair-use 1000", { timeout: 30000, concurrency: true }, async () => {
    const cases = [
        ["ECONOMY", 30],
        ["STANDARD", 100],
        ["PREMIUM", 1000],
    ];
    const { day, month } = dateKeys();

    for (const [tier, limit] of cases) {
        const user = await actor(tier);
        const [atLimit, overLimit] = await targets(2);
        await db.collection("usage").doc(user.uid).set({ day, month, likes: limit - 1 });

        assert.equal(successful(await callable("swipe", user.token, { toUserId: atLimit, isLike: true })), true, tier);
        const rejected = await callable("swipe", user.token, { toUserId: overLimit, isLike: true });
        assertError(rejected, "RESOURCE_EXHAUSTED");
        assert.equal((await db.collection("usage").doc(user.uid).get()).data().likes, limit);
    }
});

test("parallel Economy room changes cap room slots and charge only committed switches", { timeout: 30000, concurrency: true }, async () => {
    const user = await actor("ECONOMY");
    const results = await Promise.all(["TR", "EN-US", "DE", "FR"].map((languageCode) => (
        callable("switchRoom", user.token, { languageCode })
    )));

    assert.equal(results.filter(successful).length, 3, JSON.stringify(results));
    results.filter((result) => !successful(result)).forEach((result) => assertError(result, "RESOURCE_EXHAUSTED"));
    const profile = (await db.collection("users").doc(user.uid).get()).data();
    const usage = (await db.collection("usage").doc(user.uid).get()).data();
    assert.equal(profile.roomLanguageCodes.length, 3);
    assert.equal(usage.roomSwitches, 2); // Initial mandatory room selection is free.
});

test("rewind is single-use under concurrent requests", { timeout: 30000, concurrency: true }, async () => {
    const user = await actor("STANDARD");
    const [profileUid] = await targets(1);
    const { day, month } = dateKeys();
    await db.collection("interactions").doc(`${user.uid}_${profileUid}`).set({
        fromUserId: user.uid, toUserId: profileUid, type: "pass",
    });
    await db.collection("usage").doc(user.uid).set({ day, month, lastPass: profileUid, lastPassDay: day });

    const results = await Promise.all([callable("rewind", user.token, {}), callable("rewind", user.token, {})]);
    assert.equal(results.filter(successful).length, 1, JSON.stringify(results));
    assertError(results.find((result) => !successful(result)), "FAILED_PRECONDITION");
    assert.equal((await db.collection("interactions").doc(`${user.uid}_${profileUid}`).get()).exists, false);
    assert.equal((await db.collection("usage").doc(user.uid).get()).data().rewinds, 1);
});

test("Standard allows one monthly Boost", { timeout: 30000, concurrency: true }, async () => {
    const user = await actor("STANDARD");
    assert.equal(successful(await callable("activateBoost", user.token, {})), true);
    const rejected = await callable("activateBoost", user.token, {});
    assertError(rejected, "RESOURCE_EXHAUSTED");
    assert.equal((await db.collection("usage").doc(user.uid).get()).data().boosts, 1);
});

test("new-chat and message ceilings prevent the 51st free message", { timeout: 30000, concurrency: true }, async () => {
    const sender = await actor();
    const recipient = await actor();
    const chatId = [sender.uid, recipient.uid].sort().join("_");
    const { day, month } = dateKeys();
    await db.collection("matches").doc(chatId).set({ users: [sender.uid, recipient.uid].sort() });
    await db.collection("usage").doc(sender.uid).set({ day, month, messages: 49, newChats: 2 });

    const first = await callable("sendMessage", sender.token, {
        chatId, peerUid: recipient.uid, text: "quota test", senderLanguage: "EN-US", messageId: "quota-message-1",
    });
    assert.equal(successful(first), true, JSON.stringify(first));
    const second = await callable("sendMessage", sender.token, {
        chatId, peerUid: recipient.uid, text: "one too many", senderLanguage: "EN-US", messageId: "quota-message-2",
    });
    assertError(second, "RESOURCE_EXHAUSTED");
    const usage = (await db.collection("usage").doc(sender.uid).get()).data();
    assert.equal(usage.messages, 50);
    assert.equal(usage.newChats, 3);
});
