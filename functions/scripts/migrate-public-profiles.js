#!/usr/bin/env node
/*
 * Idempotently copies every legacy users/{uid} discovery projection to
 * public_profiles/{uid}. It is intentionally a local Admin SDK command rather
 * than a public callable: no customer token or client-side admin claim is used.
 *
 * Defaults to dry-run. Example:
 *   gcloud auth application-default login
 *   npm --prefix functions run migrate:public-profiles -- --project walktalk-1123f
 *   npm --prefix functions run migrate:public-profiles -- --project walktalk-1123f --execute
 */
const admin = require("firebase-admin");
const { FieldPath } = require("firebase-admin/firestore");

const args = process.argv.slice(2);
const valueAfter = (flag) => {
    const index = args.indexOf(flag);
    return index >= 0 ? args[index + 1] : undefined;
};
const projectId = valueAfter("--project") || process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT;
const afterUid = valueAfter("--after");
const execute = args.includes("--execute");

if (!projectId) {
    throw new Error("Missing project id. Pass --project walktalk-1123f.");
}
if (args.includes("--after") && !afterUid) {
    throw new Error("--after requires the last processed Firebase UID.");
}

admin.initializeApp({ projectId });
const db = admin.firestore();
const pageSize = 400;

function toPublicProfile(data) {
    return {
        displayName: data.displayName ?? "",
        birthDateMillis: data.birthDateMillis ?? null,
        countryCode: data.countryCode ?? null,
        languageCode: data.languageCode ?? null,
        avatarUrl: data.avatarUrl ?? null,
        discoveryPhotos: Array.isArray(data.discoveryPhotos) ? data.discoveryPhotos : [],
        bio: data.bio ?? "",
        interests: Array.isArray(data.interests) ? data.interests : [],
        verified: data.verified === true,
        lastActiveTimestamp: data.lastActiveTimestamp ?? 0,
        isMookActive: data.isMookActive === true,
        discoverVisible: data.discoverVisible !== false,
        incognito: data.incognito === true,
        visibleTo: Array.isArray(data.visibleTo) ? data.visibleTo : [],
        boostUntil: typeof data.boostUntil === "number" ? data.boostUntil : 0,
    };
}

async function migrate() {
    let cursor = afterUid;
    let scanned = 0;
    let written = 0;

    console.log(`${execute ? "Executing" : "Dry run"} for project ${projectId}${cursor ? ` after ${cursor}` : ""}.`);
    for (;;) {
        let query = db.collection("users")
            .orderBy(FieldPath.documentId())
            .limit(pageSize);
        if (cursor) query = query.startAfter(cursor);
        const page = await query.get();
        if (page.empty) break;

        scanned += page.size;
        cursor = page.docs.at(-1).id;
        if (execute) {
            const batch = db.batch();
            for (const user of page.docs) {
                batch.set(db.collection("public_profiles").doc(user.id), toPublicProfile(user.data()), { merge: true });
            }
            await batch.commit();
            written += page.size;
        }
        console.log(`Processed ${scanned} user(s); last UID: ${cursor}`);
        if (page.size < pageSize) break;
    }

    console.log(execute
        ? `Completed: ${written} public_profiles document(s) upserted. Last UID: ${cursor ?? "none"}.`
        : `Dry run complete: ${scanned} user(s) would be upserted. Re-run with --execute to write.`);
}

migrate().catch((error) => {
    console.error("Migration failed:", error.message || error);
    console.error("Ensure Application Default Credentials are configured with Firestore read/write access.");
    process.exitCode = 1;
});
