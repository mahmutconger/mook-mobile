import { createHmac, timingSafeEqual } from "node:crypto";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { onRequest } from "firebase-functions/v2/https";

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

/**
 * This is the HMAC signing secret generated for the *direct* RevenueCat webhook
 * integration. It is deliberately distinct from the Firebase Extension secret.
 * Configure it with `firebase functions:secrets:set RC_WEBHOOK_SIGNING_SECRET`.
 */
const RC_WEBHOOK_SIGNING_SECRET = defineSecret("RC_WEBHOOK_SIGNING_SECRET");
const ENTITLEMENTS = new Set(["economy", "standard", "premium"]);
// A pause is scheduled for the end of the current period, so access must remain
// active until RevenueCat sends the later EXPIRATION event.
const REVOKING_EVENTS = new Set(["EXPIRATION", "REFUND"]);
const MAX_SIGNATURE_AGE_SECONDS = 5 * 60;

type RevenueCatEvent = {
    id?: unknown;
    type?: unknown;
    app_user_id?: unknown;
    entitlement_id?: unknown;
    entitlement_ids?: unknown;
    event_timestamp_ms?: unknown;
    product_id?: unknown;
    expiration_at_ms?: unknown;
    environment?: unknown;
};

type RevenueCatEnvelope = { event?: RevenueCatEvent };

function asNonEmptyString(value: unknown): string | null {
    return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function activeEntitlements(event: RevenueCatEvent): string[] {
    const raw = Array.isArray(event.entitlement_ids)
        ? event.entitlement_ids
        : [event.entitlement_id];
    return raw
        .map((value) => asNonEmptyString(value)?.toLowerCase())
        .filter((value): value is string => typeof value === "string" && ENTITLEMENTS.has(value));
}

function parseSignature(header: string | undefined): { timestamp: number; signature: string } | null {
    if (!header) return null;
    const parts = new Map(
        header.split(",").map((part) => {
            const [key, ...value] = part.trim().split("=");
            return [key, value.join("=")];
        }),
    );
    const timestamp = Number(parts.get("t"));
    const signature = parts.get("v1");
    if (!Number.isFinite(timestamp) || typeof signature !== "string" || !/^[a-f0-9]{64}$/i.test(signature)) return null;
    return { timestamp, signature };
}

function hasValidSignature(rawBody: Buffer, header: string | undefined, secret: string): boolean {
    const parsed = parseSignature(header);
    if (!parsed || Math.abs(Date.now() / 1000 - parsed.timestamp) > MAX_SIGNATURE_AGE_SECONDS) return false;
    const expected = createHmac("sha256", secret)
        .update(`${parsed.timestamp}.`)
        .update(rawBody)
        .digest("hex");
    const expectedBuffer = Buffer.from(expected, "hex");
    const receivedBuffer = Buffer.from(parsed.signature, "hex");
    return expectedBuffer.length === receivedBuffer.length && timingSafeEqual(expectedBuffer, receivedBuffer);
}

function isFirebaseUid(value: string): boolean {
    // Firebase Auth UIDs are max. 128 chars. RevenueCat anonymous aliases must never
    // receive trusted claims or create a customer document in our backend.
    return value.length <= 128 && !value.startsWith("$RCAnonymousID:");
}

/**
 * Independent, v2 Cloud Functions webhook receiver for RevenueCat lifecycle events.
 * It removes the Firebase Extension from the authorization critical path while keeping
 * the same `customers/{uid}` and `revenueCatEntitlements` contracts used by callables.
 */
export const revenueCatWebhook = onRequest(
    {
        region: "us-central1",
        secrets: [RC_WEBHOOK_SIGNING_SECRET],
        timeoutSeconds: 20,
        cors: false,
    },
    async (request, response) => {
        if (request.method !== "POST") {
            response.status(405).send("Method Not Allowed");
            return;
        }

        const rawBody = request.rawBody;
        const secret = RC_WEBHOOK_SIGNING_SECRET.value();
        if (!rawBody || !secret || !hasValidSignature(rawBody, request.header("X-RevenueCat-Webhook-Signature"), secret)) {
            response.status(401).send("Unauthorized");
            return;
        }

        const envelope = request.body as RevenueCatEnvelope;
        const event = envelope?.event;
        const eventId = asNonEmptyString(event?.id);
        const uid = asNonEmptyString(event?.app_user_id);
        const type = asNonEmptyString(event?.type)?.toUpperCase();
        if (!event || !eventId || !uid || !type || !isFirebaseUid(uid)) {
            // Return 200 for a syntactically valid but irrelevant event: retrying it
            // cannot make it processable and would only create noise in RevenueCat.
            response.status(200).send("Ignored");
            return;
        }

        try {
            await admin.auth().getUser(uid);
        } catch (error) {
            const code = (error as { code?: string }).code;
            if (code === "auth/user-not-found") {
                response.status(200).send("Ignored");
                return;
            }
            console.error("RevenueCat webhook could not resolve Firebase user", { eventId, uid, error });
            response.status(500).send("Retry later");
            return;
        }

        const eventTimestamp = Number(event.event_timestamp_ms);
        const timestamp = Number.isFinite(eventTimestamp) ? eventTimestamp : Date.now();
        const eventEntitlements = activeEntitlements(event);
        const eventRef = db.collection("revenuecat_webhook_events").doc(eventId);
        const customerRef = db.collection("customers").doc(uid);

        let entitlements: string[] = [];
        let alreadyProcessed = false;
        await db.runTransaction(async (transaction) => {
            const [eventSnapshot, customerSnapshot] = await Promise.all([
                transaction.get(eventRef),
                transaction.get(customerRef),
            ]);
            if (eventSnapshot.data()?.status === "processed") {
                alreadyProcessed = true;
                entitlements = Array.isArray(customerSnapshot.data()?.revenueCatEntitlements)
                    ? customerSnapshot.data()!.revenueCatEntitlements as string[]
                    : [];
                return;
            }

            const customer = customerSnapshot.data() ?? {};
            const current = new Set(
                Array.isArray(customer.revenueCatEntitlements)
                    ? customer.revenueCatEntitlements.filter((entry): entry is string => typeof entry === "string")
                    : [],
            );
            const entitlementTimestamps = { ...(customer.entitlementEventTimestamps ?? {}) } as Record<string, number>;
            const revoke = REVOKING_EVENTS.has(type);
            for (const entitlement of eventEntitlements) {
                if (timestamp < Number(entitlementTimestamps[entitlement] ?? 0)) continue;
                entitlementTimestamps[entitlement] = timestamp;
                if (revoke) current.delete(entitlement);
                else current.add(entitlement);
            }
            entitlements = [...current].sort();
            transaction.set(customerRef, {
                appUserId: uid,
                revenueCatEntitlements: entitlements,
                activeEntitlements: entitlements,
                entitlementEventTimestamps: entitlementTimestamps,
                lastRevenueCatEvent: {
                    id: eventId,
                    type,
                    productId: asNonEmptyString(event.product_id),
                    environment: asNonEmptyString(event.environment),
                    expirationAtMillis: Number(event.expiration_at_ms) || null,
                    timestamp,
                },
                updatedAt: FieldValue.serverTimestamp(),
            }, { merge: true });
            transaction.set(eventRef, {
                status: "pending",
                uid,
                type,
                receivedAt: FieldValue.serverTimestamp(),
                eventTimestamp: timestamp,
            }, { merge: true });
        });

        if (!alreadyProcessed) {
            const user = await admin.auth().getUser(uid);
            await admin.auth().setCustomUserClaims(uid, {
                ...(user.customClaims ?? {}),
                revenueCatEntitlements: entitlements,
            });
            await eventRef.set({
                status: "processed",
                processedAt: FieldValue.serverTimestamp(),
            }, { merge: true });
        }

        response.status(200).send("OK");
    },
);
