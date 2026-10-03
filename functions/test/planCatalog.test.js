/*
 * İstemcinin gömülü yedek plan kataloğu (PlanCatalog.BUNDLED) sunucu varsayılanlarıyla
 * (planDefaults.ts) BİREBİR aynı olmalıdır. Kotlin dosyasındaki işaretli blok ayrıştırılır.
 */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { test } = require("node:test");
const { serializablePlans } = require("../lib/planDefaults");

const KOTLIN_FILE = path.join(__dirname, "..", "..", "shared", "src", "commonMain", "kotlin",
    "com", "mcclabs", "mook", "domain", "billing", "PlanCatalog.kt");

function parseBundledCatalog() {
    const source = fs.readFileSync(KOTLIN_FILE, "utf8");
    const block = source.split("// BEGIN-PLAN-CATALOG")[1].split("// END-PLAN-CATALOG")[0];
    const catalog = {};
    for (const match of block.matchAll(/Tier\.(\w+) to PlanLimits\(([^)]*)\)/g)) {
        const limits = {};
        for (const pair of match[2].split(",")) {
            const [key, raw] = pair.split("=").map((part) => part.trim());
            limits[key] = raw === "null" ? null : raw === "true" ? true : raw === "false" ? false : Number(raw);
        }
        catalog[match[1].toLowerCase()] = limits;
    }
    return catalog;
}

test("İstemci gömülü kataloğu sunucu plan varsayılanlarıyla aynıdır", () => {
    assert.deepEqual(parseBundledCatalog(), serializablePlans());
});
