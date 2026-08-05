const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Generates a Firebase Custom Token for the authenticated user.
 * This token can be used by companion apps (like WalkTalk) to authenticate
 * as the same user seamlessly (Single Sign-On).
 */
exports.generateSsoToken = onCall(async (request) => {
    // 1. Verify that the user is authenticated in the calling app (Mook).
    if (!request.auth || !request.auth.uid) {
        throw new HttpsError(
            "unauthenticated",
            "You must be logged in to generate an SSO token."
        );
    }

    const uid = request.auth.uid;

    try {
        // 2. Generate a custom token using the Firebase Admin SDK.
        // This token is valid for 1 hour by default.
        const customToken = await admin.auth().createCustomToken(uid);
        
        // 3. Return the token to the client.
        return {
            token: customToken
        };
    } catch (error) {
        console.error("Error generating custom token:", error);
        throw new HttpsError(
            "internal",
            "Unable to generate SSO token."
        );
    }
});
