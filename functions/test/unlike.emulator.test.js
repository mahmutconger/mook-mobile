const assert = require("node:assert/strict");
const { after, test } = require("node:test");
const admin = require("firebase-admin");

// Bu test yalnızca yerel Firestore emülatörüne yazabilir.
if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error("Firestore emülatörü başlatılmalıdır.");
if (admin.apps.length === 0) admin.initializeApp({ projectId: process.env.GCLOUD_PROJECT || "demo-walkmatch-optimistic" });
const { unlike } = require("../lib/monetization");
const db = admin.firestore();
const prefix = `optimistic_${Date.now()}`;
const invoke = (uid, toUserId) => unlike.run({ auth: { uid, token: {} }, data: { toUserId } });
const pair = (first, second) => [first, second].sort().join("_");
after(() => admin.app().delete());

test("Beğeni ve eşleşme atomik silinir; karşı beğeni, sohbet ve kota korunur", async () => {
    const uid = `${prefix}_owner`;
    const peer = `${prefix}_peer`;
    const own = db.collection("interactions").doc(`${uid}_${peer}`);
    const reverse = db.collection("interactions").doc(`${peer}_${uid}`);
    const match = db.collection("matches").doc(pair(uid, peer));
    const chat = db.collection("chats").doc(pair(uid, peer));
    const usage = db.collection("usage").doc(uid);
    await Promise.all([
        own.set({ fromUserId: uid, toUserId: peer, type: "like" }),
        reverse.set({ fromUserId: peer, toUserId: uid, type: "like" }),
        match.set({ users: [uid, peer] }), chat.set({ users: [uid, peer] }),
        usage.set({ likes: 5, likesEver: 10 }),
    ]);
    assert.deepEqual(await invoke(uid, peer), { unliked: true });
    assert.equal((await own.get()).exists, false);
    assert.equal((await match.get()).exists, false);
    assert.equal((await reverse.get()).data().type, "like");
    assert.equal((await chat.get()).exists, true);
    assert.deepEqual((await usage.get()).data(), { likes: 5, likesEver: 10 });
    assert.deepEqual(await invoke(uid, peer), { unliked: true });
});

test("Geçme kayıtları ve karşı tarafın beğenisi korunur", async () => {
    const uid = `${prefix}_pass`;
    const peer = `${prefix}_pass_peer`;
    const own = db.collection("interactions").doc(`${uid}_${peer}`);
    const reverse = db.collection("interactions").doc(`${peer}_${uid}`);
    await own.set({ type: "pass" });
    await reverse.set({ type: "like" });
    assert.deepEqual(await invoke(uid, peer), { unliked: true });
    assert.equal((await own.get()).data().type, "pass");
    assert.equal((await reverse.get()).data().type, "like");
});

test("Kimlik doğrulaması, hedef geçerliliği ve yasaklı kullanıcı denetlenir", async () => {
    await assert.rejects(unlike.run({ data: { toUserId: "peer" } }), { code: "unauthenticated" });
    const uid = `${prefix}_validation`;
    for (const invalid of ["", uid, "invalid/path", 123, undefined]) {
        await assert.rejects(invoke(uid, invalid), { code: "invalid-argument" });
    }
    await db.collection("users").doc(uid).set({ isBanned: true });
    await assert.rejects(invoke(uid, `${prefix}_peer`), { code: "permission-denied" });
});
