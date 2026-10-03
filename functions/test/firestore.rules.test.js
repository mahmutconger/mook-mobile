/*
 * Firestore Güvenlik Temeli testleri (users create/update, public_profiles list).
 * Çalıştırma: npm --prefix functions run test:rules  (yalnızca Firestore emülatörü gerekir)
 */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { after, before, beforeEach, test } = require("node:test");
const {
    assertFails,
    assertSucceeds,
    initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {
    arrayUnion,
    collection,
    doc,
    getDoc,
    getDocs,
    limit,
    query,
    setDoc,
    updateDoc,
    where,
} = require("firebase/firestore");

let env;
const ALICE = "alice";

// Kayıt ekranının gerçekte gönderdiği yük (AuthRepositoryImpl.register).
const registrationPayload = () => ({
    email: "alice@example.com",
    displayName: "Alice",
    lastActiveTimestamp: Date.now(),
    acceptedEula: true,
    acceptedEulaAt: Date.now(),
});

const aliceDb = () => env.authenticatedContext(ALICE, { email: "alice@example.com" }).firestore();

async function seed(collectionName, id, data) {
    await env.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), collectionName, id), data);
    });
}

before(async () => {
    env = await initializeTestEnvironment({
        projectId: "demo-walkmatch-rules",
        firestore: { rules: fs.readFileSync(path.join(__dirname, "..", "..", "firestore.rules"), "utf8") },
    });
});
beforeEach(async () => env.clearFirestore());
after(async () => env.cleanup());

// ---------------------------------------------------------------- users: create

test("meşru kayıt yükü belge oluşturabilir", async () => {
    await assertSucceeds(setDoc(doc(aliceDb(), "users", ALICE), registrationPayload()));
});

test("belge yokken merge ile yazma (EULA kabulü) create olarak geçer", async () => {
    await assertSucceeds(setDoc(doc(aliceDb(), "users", ALICE),
        { acceptedEula: true, acceptedEulaAt: Date.now() }, { merge: true }));
});

for (const [field, value] of [
    ["verified", true],
    ["boostUntil", Date.now() + 10 * 365 * 24 * 3600 * 1000],
    ["incognito", true],
    ["roomLanguageCodes", ["TR", "DE", "FR", "IT", "ES", "EN-US"]],
    ["roomLanguageCode", "DE"],
    ["isMookActive", true],
    ["isBanned", false],
    ["timeZone", "Pacific/Kiritimati"],
]) {
    test(`kayıt yüküne enjekte edilen sunucu alanı reddedilir: ${field}`, async () => {
        await assertFails(setDoc(doc(aliceDb(), "users", ALICE), { ...registrationPayload(), [field]: value }));
    });
}

test("başka bir kullanıcının belgesi oluşturulamaz", async () => {
    await assertFails(setDoc(doc(aliceDb(), "users", "bob"), registrationPayload()));
});

test("bilinen bir alanın tipi bozuksa create reddedilir", async () => {
    await assertFails(setDoc(doc(aliceDb(), "users", ALICE), { ...registrationPayload(), displayName: 42 }));
    await assertFails(setDoc(doc(aliceDb(), "users", ALICE), { ...registrationPayload(), birthDateMillis: "1990" }));
});

// ---------------------------------------------------------------- users: update

test("profil alanları güncellenebilir", async () => {
    await seed("users", ALICE, { ...registrationPayload(), isMookActive: true });
    await assertSucceeds(updateDoc(doc(aliceDb(), "users", ALICE), { bio: "Merhaba", languageCode: "TR" }));
});

for (const [field, value] of [
    ["isMookActive", false],
    ["mookActivatedAt", 1],
    ["verified", true],
    ["boostUntil", Date.now() + 3600_000],
    ["incognito", true],
    ["roomLanguageCodes", ["TR", "DE"]],
]) {
    test(`sunucuya ait alan güncellenemez: ${field}`, async () => {
        await seed("users", ALICE, { ...registrationPayload(), isMookActive: true });
        await assertFails(updateDoc(doc(aliceDb(), "users", ALICE), { [field]: value }));
    });
}

test("WalkTalk'tan kalma şemaya uymayan bir alan ilgisiz güncellemeyi engellemez", async () => {
    // Mook şemasına göre geçersiz (string doğum tarihi, 50 ilgi alanı) eski bir belge.
    await seed("users", ALICE, {
        ...registrationPayload(),
        birthDateMillis: "1990-01-01",
        interests: Array.from({ length: 50 }, (_, i) => `ilgi-${i}`),
    });
    await assertSucceeds(updateDoc(doc(aliceDb(), "users", ALICE), { fcmTokens: arrayUnion("token-1") }));
});

test("istemci bir güncellemede bilinen alanı yanlış tiple yazamaz", async () => {
    await seed("users", ALICE, registrationPayload());
    await assertFails(updateDoc(doc(aliceDb(), "users", ALICE), { discoverVisible: "evet" }));
});

// ---------------------------------------------------------------- public_profiles

test("public_profiles: kısıtsız ham sorgu (kazıma) reddedilir", async () => {
    await seed("users", ALICE, { roomLanguageCode: "DE" });
    await seed("public_profiles", "bob", { languageCode: "DE", isMookActive: true, incognito: false });
    await assertFails(getDocs(collection(aliceDb(), "public_profiles")));
});

test("public_profiles: başka odanın sorgusu reddedilir", async () => {
    await seed("users", ALICE, { roomLanguageCode: "DE" });
    await assertFails(getDocs(query(collection(aliceDb(), "public_profiles"),
        where("isMookActive", "==", true), where("incognito", "==", false),
        where("languageCode", "==", "FR"), limit(20))));
});

test("public_profiles: istemci yazamaz", async () => {
    await assertFails(setDoc(doc(aliceDb(), "public_profiles", ALICE), { verified: true }));
});

test("kural dosyası kısıtlı list koşullarını içerir (App Check'li olumlu yol emülatörde test edilemez)", () => {
    const rules = fs.readFileSync(path.join(__dirname, "..", "..", "firestore.rules"), "utf8");
    assert.match(rules, /request\.query\.limit <= 20/);
    assert.match(rules, /resource\.data\.languageCode == callerActiveRoom\(\) \|\| callerHasFreeRoam\(\)/);
});


// ── Okundu bilgisi (Premium) ve abonelik kademesi ─────────────────────────────

const CHAT_ID = "alice_bob";
const dbFor = (uid, claims = {}) => env.authenticatedContext(uid, claims).firestore();

test("kullanıcı kendi okundu bilgisini katılımcısı olduğu sohbete yazabilir", async () => {
    await seed("chats", CHAT_ID, { users: ["alice", "bob"] });
    await assertSucceeds(setDoc(doc(dbFor("alice"), "read_receipts", `${CHAT_ID}_alice`), {
        chatId: CHAT_ID, readerUid: "alice", lastReadAt: Date.now(),
    }));
});

test("başkası adına veya katılımcısı olunmayan sohbete okundu bilgisi yazılamaz", async () => {
    await seed("chats", CHAT_ID, { users: ["alice", "bob"] });
    await assertFails(setDoc(doc(dbFor("alice"), "read_receipts", `${CHAT_ID}_bob`), {
        chatId: CHAT_ID, readerUid: "bob", lastReadAt: Date.now(),
    }));
    await assertFails(setDoc(doc(dbFor("mallory"), "read_receipts", `${CHAT_ID}_mallory`), {
        chatId: CHAT_ID, readerUid: "mallory", lastReadAt: Date.now(),
    }));
    // Gelecek tarihli sahte okundu bilgisi reddedilir.
    await assertFails(setDoc(doc(dbFor("alice"), "read_receipts", `${CHAT_ID}_alice`), {
        chatId: CHAT_ID, readerUid: "alice", lastReadAt: Date.now() + 3_600_000,
    }));
});

test("karşı tarafın okundu bilgisini yalnızca Premium katılımcı görebilir", async () => {
    await seed("chats", CHAT_ID, { users: ["alice", "bob"] });
    await seed("read_receipts", `${CHAT_ID}_bob`, { chatId: CHAT_ID, readerUid: "bob", lastReadAt: 1 });
    await assertSucceeds(getDoc(doc(dbFor("alice", { revenueCatEntitlements: ["premium"] }), "read_receipts", `${CHAT_ID}_bob`)));
    await assertFails(getDoc(doc(dbFor("alice", { revenueCatEntitlements: ["standard"] }), "read_receipts", `${CHAT_ID}_bob`)));
    await assertFails(getDoc(doc(dbFor("mallory", { revenueCatEntitlements: ["premium"] }), "read_receipts", `${CHAT_ID}_bob`)));
});

test("istemci kendi abonelik kademesini (subscriptionTier) yazamaz", async () => {
    await seed("users", ALICE, registrationPayload());
    await assertFails(updateDoc(doc(aliceDb(), "users", ALICE), { subscriptionTier: "PREMIUM" }));
});
