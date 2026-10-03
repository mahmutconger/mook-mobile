/*
 * Firestore Test Standartları — kapsamlı güvenlik kuralı testleri.
 *
 * Kapsam: users, public_profiles, config/plans, abuse_flags (+ events), private_profile_meta.
 * Her koleksiyon için hem İZİN VERİLEN hem REDDEDİLEN senaryolar test edilir. Kural dosyası
 * gerçek `firestore.rules`tır; testler Firestore emülatöründe `@firebase/rules-unit-testing`
 * ile çalışır (çalıştırıcı: node:test — projede zaten kullanılan, ek bağımlılık gerektirmeyen
 * test koşucusu).
 *
 * Çalıştırma: npm --prefix functions run test:rules
 */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { after, before, beforeEach, describe, test } = require("node:test");
const { assertFails, assertSucceeds, initializeTestEnvironment } = require("@firebase/rules-unit-testing");
const { collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc } = require("firebase/firestore");

const ALICE = "alice";
const BOB = "bob";
let env;

const rules = fs.readFileSync(path.join(__dirname, "..", "..", "firestore.rules"), "utf8");
const authed = (uid, claims = {}) => env.authenticatedContext(uid, claims).firestore();
const anon = () => env.unauthenticatedContext().firestore();

async function seed(collectionPath, id, data) {
    await env.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), collectionPath, id), data);
    });
}

const userDoc = (overrides = {}) => ({
    email: "alice@example.com",
    displayName: "Alice",
    lastActiveTimestamp: 1,
    acceptedEula: true,
    acceptedEulaAt: 1,
    ...overrides,
});

const planDoc = {
    free: { dailyLikes: 10, dailyMessages: 50, showsAds: true },
    premium: { dailyLikes: null, showsAds: false },
};

before(async () => {
    env = await initializeTestEnvironment({ projectId: "demo-walkmatch-security", firestore: { rules } });
});
beforeEach(async () => env.clearFirestore());
after(async () => env.cleanup());

describe("users", () => {
    test("kullanıcı kendi belgesini okuyabilir", async () => {
        await seed("users", ALICE, userDoc());
        await assertSucceeds(getDoc(doc(authed(ALICE), "users", ALICE)));
    });

    test("kullanıcı başkasının özel belgesini okuyamaz (e-posta, doğum tarihi)", async () => {
        await seed("users", BOB, userDoc({ email: "bob@example.com", birthDateMillis: 946684800000 }));
        await assertFails(getDoc(doc(authed(ALICE), "users", BOB)));
    });

    test("oturumsuz istek hiçbir kullanıcı belgesini okuyamaz", async () => {
        await seed("users", ALICE, userDoc());
        await assertFails(getDoc(doc(anon(), "users", ALICE)));
    });

    test("kullanıcılar listelenemez (kazıma koruması)", async () => {
        await seed("users", ALICE, userDoc());
        await assertFails(getDocs(collection(authed(ALICE), "users")));
    });

    test("kullanıcı kendi profilini güncelleyebilir", async () => {
        await seed("users", ALICE, userDoc());
        await assertSucceeds(updateDoc(doc(authed(ALICE), "users", ALICE), { displayName: "Alice Yeni", bio: "Merhaba" }));
    });

    test("kullanıcı başkasının profilini güncelleyemez", async () => {
        await seed("users", BOB, userDoc({ displayName: "Bob" }));
        await assertFails(updateDoc(doc(authed(ALICE), "users", BOB), { displayName: "Ele geçirildi" }));
    });

    test("sunucuya ait alanlar istemciden yazılamaz", async () => {
        await seed("users", ALICE, userDoc());
        const db = authed(ALICE);
        for (const field of [
            { subscriptionTier: "PREMIUM" },
            { isMookActive: true },
            { boostUntil: Date.now() + 3_600_000 },
            { incognito: true },
            { verified: true },
            { isBanned: false },
            { timeZone: "UTC" },
        ]) {
            await assertFails(updateDoc(doc(db, "users", ALICE), field), JSON.stringify(field));
        }
    });

    test("kullanıcı belgesi silinemez (hesap silme yalnızca sunucuda)", async () => {
        await seed("users", ALICE, userDoc());
        await assertFails(deleteDoc(doc(authed(ALICE), "users", ALICE)));
    });
});

describe("public_profiles", () => {
    const profile = { displayName: "Bob", age: 26, isMookActive: true, incognito: false, languageCode: "TR" };

    test("istemci herkese açık profil oluşturamaz/güncelleyemez/silemez", async () => {
        await seed("public_profiles", BOB, profile);
        const db = authed(BOB);
        await assertFails(setDoc(doc(db, "public_profiles", ALICE), profile));
        await assertFails(updateDoc(doc(db, "public_profiles", BOB), { age: 18 }));
        await assertFails(deleteDoc(doc(db, "public_profiles", BOB)));
    });

    test("App Check jetonu olmayan istek profili okuyamaz", async () => {
        // Emülatör `request.app` sağlayamaz; App Check'li olumlu yol üretimde/manuel test edilir.
        await seed("public_profiles", BOB, profile);
        await assertFails(getDoc(doc(authed(ALICE), "public_profiles", BOB)));
    });

    test("oturumsuz istek profil okuyamaz", async () => {
        await seed("public_profiles", BOB, profile);
        await assertFails(getDoc(doc(anon(), "public_profiles", BOB)));
    });
});

describe("config/plans", () => {
    test("oturum açmış kullanıcı plan sınırlarını okuyabilir", async () => {
        await seed("config", "plans", planDoc);
        const snapshot = await assertSucceeds(getDoc(doc(authed(ALICE), "config", "plans")));
        assert.equal(snapshot.data().free.dailyLikes, 10);
    });

    test("oturumsuz istek plan sınırlarını okuyamaz", async () => {
        await seed("config", "plans", planDoc);
        await assertFails(getDoc(doc(anon(), "config", "plans")));
    });

    test("yetkisiz kullanıcı plan sınırlarını yazamaz", async () => {
        await seed("config", "plans", planDoc);
        await assertFails(setDoc(doc(authed(ALICE), "config", "plans"), { free: { dailyLikes: 100000 } }));
        await assertFails(updateDoc(doc(authed(ALICE), "config", "plans"), { "free.showsAds": false }));
        await assertFails(deleteDoc(doc(authed(ALICE), "config", "plans")));
    });

    test("admin özel talebi bile istemciden yazamaz (yalnızca sunucu)", async () => {
        await seed("config", "plans", planDoc);
        await assertFails(setDoc(doc(authed(ALICE, { admin: true }), "config", "plans"), planDoc));
    });

    test("config altındaki diğer belgeler okunamaz", async () => {
        await seed("config", "secrets", { value: "x" });
        await assertFails(getDoc(doc(authed(ALICE), "config", "secrets")));
    });
});

describe("abuse_flags", () => {
    const flag = { uid: ALICE, score: 10, status: "review", signalCounts: { user_reported: 4 } };

    test("işaretlenen kullanıcı kendi işaretini okuyamaz", async () => {
        await seed("abuse_flags", ALICE, flag);
        await assertFails(getDoc(doc(authed(ALICE), "abuse_flags", ALICE)));
    });

    test("başka kullanıcılar işaretleri okuyamaz veya listeleyemez", async () => {
        await seed("abuse_flags", ALICE, flag);
        await assertFails(getDoc(doc(authed(BOB), "abuse_flags", ALICE)));
        await assertFails(getDocs(collection(authed(BOB), "abuse_flags")));
    });

    test("istemci işaret oluşturamaz, temizleyemez veya silemez", async () => {
        await seed("abuse_flags", ALICE, flag);
        await assertFails(setDoc(doc(authed(BOB), "abuse_flags", ALICE), { ...flag, score: 99 }));
        await assertFails(updateDoc(doc(authed(ALICE), "abuse_flags", ALICE), { status: "clear", score: 0 }));
        await assertFails(deleteDoc(doc(authed(ALICE), "abuse_flags", ALICE)));
        await assertFails(setDoc(doc(authed(BOB), "abuse_flags", BOB), { uid: BOB, score: 0, status: "clear" }));
    });

    test("olay geçmişi alt koleksiyonu okunamaz ve yazılamaz", async () => {
        await seed("abuse_flags/alice/events", "e1", { signal: "user_reported", sourceKey: BOB });
        await assertFails(getDoc(doc(authed(ALICE), "abuse_flags/alice/events", "e1")));
        await assertFails(setDoc(doc(authed(ALICE), "abuse_flags/alice/events", "e2"), { signal: "x" }));
    });
});

describe("private_profile_meta", () => {
    test("yaş yenileme kaydı (doğum günü türevi) hiçbir istemciye açık değildir", async () => {
        await seed("private_profile_meta", ALICE, { uid: ALICE, ageRefreshAt: 1 });
        await assertFails(getDoc(doc(authed(ALICE), "private_profile_meta", ALICE)));
        await assertFails(getDoc(doc(authed(BOB), "private_profile_meta", ALICE)));
        await assertFails(setDoc(doc(authed(ALICE), "private_profile_meta", ALICE), { ageRefreshAt: 0 }));
    });
});
