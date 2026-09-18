package com.maxtime.app.data.share

import androidx.compose.runtime.Composable

/**
 * Cross-platform "share this text" entry point — Android's share-sheet chooser Intent / iOS's
 * UIActivityViewController, whichever target app the person picks (WhatsApp, Messages, etc.)
 * receives the plain text as-is. Deliberately plain text only for V1 — no deep link/App Link is
 * embedded, since none exists yet for this app; see ReferAndEarnScreen's own doc comment.
 */
@Composable
expect fun rememberShareLauncher(): ShareLauncher

expect class ShareLauncher {
    fun shareText(text: String)
}
