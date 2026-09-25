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

    /**
     * Shares a rendered PNG image (a share-card, see ShareCardTemplate) plus an optional caption.
     * [targetPackage] — Android only — jumps straight into that app (e.g. "com.whatsapp") instead
     * of showing the OS chooser, matching how a dedicated per-app icon is expected to behave; iOS
     * has no equivalent concept for `UIActivityViewController` (the person always picks from its
     * sheet), so [targetPackage] is ignored there and it always shows the generic share sheet.
     * Falls back to the generic share sheet if [targetPackage] is set but that app isn't
     * installed — the image should never end up unshared just because one specific app is
     * missing.
     *
     * [instagramBytes], if provided, is swapped in specifically when Instagram ends up being the
     * destination someone picks from the plain native/generic share sheet — NOT the dedicated
     * Instagram sticker button ([shareToInstagramStories]), which is a separate mechanism
     * entirely. This exists because, unlike [targetPackage], you cannot know in advance which app
     * a person will choose from an OS share sheet — each platform actual detects the choice at
     * hand-off time (an `NSItemProvider`-style per-activity-type callback on iOS, a pinned
     * targeted intent + excluded default resolution on Android) rather than up front.
     */
    fun shareImage(pngBytes: ByteArray, caption: String? = null, targetPackage: String? = null, instagramBytes: ByteArray? = null)

    /**
     * Instagram Stories specifically needs its own mechanism, not a generic share intent — see
     * this file's Android/iOS actuals for exactly how each platform does this (an explicit Intent
     * extra on Android, a UIPasteboard handoff + URL scheme on iOS). Falls back to [shareImage]
     * (the generic sheet) if Instagram isn't installed on that device.
     */
    fun shareToInstagramStories(pngBytes: ByteArray)
}
