package com.mcclabs.mook.util

import androidx.compose.runtime.Composable

/**
 * Remembers a launcher that opens the WalkTalk chat deep link [url].
 *
 * Fallback behaviour when WalkTalk cannot handle the link:
 * - **Android:** if the WalkTalk app is installed, the link is opened inside it;
 *   otherwise the user is sent to WalkTalk's Google Play page.
 * - **iOS:** the Universal Link is opened; if the app is not installed it falls
 *   through to the web page (which redirects to the App Store).
 */
@Composable
expect fun rememberWalkTalkChatOpener(): (String) -> Unit
