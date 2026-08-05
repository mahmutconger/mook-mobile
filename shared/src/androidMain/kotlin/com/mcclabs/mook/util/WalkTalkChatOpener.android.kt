package com.mcclabs.mook.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** WalkTalk's Android application id (Play listing + package). */
private const val WALKTALK_PACKAGE = "com.istaps.walktalk2"

@Composable
actual fun rememberWalkTalkChatOpener(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url -> openWalkTalkOrPlayStore(context, url) }
    }
}

/**
 * Opens [url] in the WalkTalk app when it is installed; otherwise sends the user
 * to WalkTalk's Google Play page.
 *
 * Note: detecting the package on Android 11+ requires a `<queries>` entry for
 * [WALKTALK_PACKAGE] in the app manifest, otherwise it is treated as not installed.
 */
private fun openWalkTalkOrPlayStore(context: Context, url: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(WALKTALK_PACKAGE)

    if (launchIntent == null) {
        // WalkTalk not installed → Google Play.
        openPlayStore(context)
        return
    }

    // Installed → open the deep link inside WalkTalk. If it cannot yet route this
    // path, fall back to simply launching the app rather than the store.
    val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setPackage(WALKTALK_PACKAGE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (!tryStart(context, deepLink)) {
        tryStart(context, launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openPlayStore(context: Context) {
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$WALKTALK_PACKAGE"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    if (!tryStart(context, marketIntent)) {
        // Play Store app missing (e.g. emulator) → open the web listing.
        tryStart(
            context,
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$WALKTALK_PACKAGE"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun tryStart(context: Context, intent: Intent): Boolean =
    runCatching { context.startActivity(intent) }.isSuccess
