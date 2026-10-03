#!/usr/bin/env node
/**
 * `config/plans` belgesini sunucu varsayılanlarıyla (functions/src/planDefaults.ts →
 * `serializablePlans()`) doldurur. `bootstrapMonetization` callable'ının ilk adımıyla aynı
 * işi yapar; ancak App Check ve `admin` özel talebi gerektirmez, çünkü proje sahibinin
 * `gcloud` oturumuyla doğrudan Firestore REST API'sine yazar (güvenlik kuralları devreye girmez).
 *
 * Yalnızca kademe alanlarını (free, standard, premium …) günceller; belgedeki diğer alanlara
 * dokunmaz. Tekrar çalıştırmak güvenlidir.
 *
 * Kullanım (proje kökünden):
 *   npm --prefix functions run build && node scripts/seed_plan_catalog.js
 *   node scripts/seed_plan_catalog.js --dry-run   # yazmadan yalnızca göster
 */
const { execSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

/** PATH'te yoksa Homebrew/yerel kurulum konumlarında `gcloud` arar. */
function resolveGcloudBinary() {
    if (process.env.GCLOUD_BIN) return process.env.GCLOUD_BIN;
    const candidates = [
        "/opt/homebrew/share/google-cloud-sdk/bin/gcloud",
        "/usr/local/share/google-cloud-sdk/bin/gcloud",
        path.join(process.env.HOME ?? "", "google-cloud-sdk", "bin", "gcloud"),
    ];
    try {
        execSync("command -v gcloud", { stdio: "ignore" });
        return "gcloud";
    } catch {
        const found = candidates.find((candidate) => fs.existsSync(candidate));
        if (found) return found;
        throw new Error("gcloud bulunamadı. Kurulu değilse `brew install --cask google-cloud-sdk`, kuruluysa GCLOUD_BIN=<yol> ile çalıştır.");
    }
}

const PROJECT_ID = "walktalk-1123f";
const DOC_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/config/plans`;

function toFirestoreValue(value) {
    if (value === null || value === undefined) return { nullValue: null };
    if (typeof value === "boolean") return { booleanValue: value };
    if (typeof value === "number") return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
    if (typeof value === "string") return { stringValue: value };
    if (Array.isArray(value)) return { arrayValue: { values: value.map(toFirestoreValue) } };
    const fields = {};
    for (const [key, nested] of Object.entries(value)) fields[key] = toFirestoreValue(nested);
    return { mapValue: { fields } };
}

async function main() {
    const dryRun = process.argv.includes("--dry-run");
    const { serializablePlans } = require(path.join(__dirname, "..", "functions", "lib", "planDefaults.js"));
    const plans = serializablePlans();
    const tiers = Object.keys(plans);
    console.log(`Kademeler: ${tiers.join(", ")}`);
    if (dryRun) {
        console.log(JSON.stringify(plans, null, 2));
        return;
    }

    const token = execSync(`"${resolveGcloudBinary()}" auth print-access-token`, { encoding: "utf8" }).trim();
    const fields = {};
    for (const tier of tiers) fields[tier] = toFirestoreValue(plans[tier]);
    const mask = tiers.map((tier) => `updateMask.fieldPaths=${encodeURIComponent(tier)}`).join("&");

    const response = await fetch(`${DOC_URL}?${mask}`, {
        method: "PATCH",
        headers: {
            Authorization: `Bearer ${token}`,
            "x-goog-user-project": PROJECT_ID,
            "Content-Type": "application/json",
        },
        body: JSON.stringify({ fields }),
    });
    const body = await response.json();
    if (!response.ok) {
        console.error(`✖ Yazma başarısız (${response.status}): ${body.error?.message ?? JSON.stringify(body)}`);
        process.exit(1);
    }
    console.log(`✔ config/plans yazıldı (updateTime=${body.updateTime}).`);
}

main().catch((error) => {
    console.error("✖ Beklenmeyen hata:", error.message);
    process.exit(1);
});
