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
     * Instagram button ([shareToInstagram]), which is a separate mechanism entirely. This exists
     * because, unlike [targetPackage], you cannot know in advance which app a person will choose
     * from an OS share sheet — each platform actual detects the choice at hand-off time (an
     * `NSItemProvider`-style per-activity-type callback on iOS, a pinned targeted intent +
     * excluded default resolution on Android) rather than up front.
     */
    fun shareImage(pngBytes: ByteArray, caption: String? = null, targetPackage: String? = null, instagramBytes: ByteArray? = null)

    /**
     * Shares ONLY [url] — no image, no caption. On-device testing showed WhatsApp's share
     * extension never renders its own Open-Graph link-preview card when an image is also attached
     * to the same share, even though the destination page's OG tags are correct — apparently it
     * treats the attached image as "the" visual and skips unfurling the link. Sharing the URL as
     * the sheet's only item is what lets WhatsApp recognize it as a link worth fetching and
     * rendering its own card for, the same way manually pasting a URL into its compose box would.
     */
    fun shareLink(url: String, targetPackage: String? = null)

    /**
     * True if [shareToInstagram] alone already lands the person inside Instagram's own real
     * destination picker (Story / Post / Reel / Message) with a single call — no extra UI needed
     * from this app. False if this platform has no such single-call mechanism, meaning the caller
     * must instead call [shareToInstagramStory] directly (the only destination offered on that
     * platform — see its own doc comment for why Direct isn't).
     *
     * True on Android (a direct `ACTION_SEND` Intent targeted at Instagram's package lands on its
     * own share-receiver activity, which shows the real picker itself). False on iOS — there is no
     * iOS API to skip the system share sheet and land directly inside one specific app's own
     * picker; Apple deliberately disallows programmatically pre-selecting an activity inside
     * `UIActivityViewController`, and Meta has never published a single deep link that opens its
     * own multi-destination chooser the way its share extension does when reached via the system
     * sheet — every scheme Meta exposes deep-links into exactly one destination.
     */
    val instagramSharePickerIsNative: Boolean

    /**
     * Android only ([instagramSharePickerIsNative] == true there): a direct `ACTION_SEND` Intent
     * targeted at Instagram's package, landing on its real Story/Post/Reel/Message picker — full
     * parity with picking Instagram from a generic share sheet. No permission needed, nothing
     * touches the person's Photos library. [opaqueBytes] must be the opaque, corner-radius-baked-in
     * card (see `drawShareCardForInstagramSheet`) — this receiver has no sticker/pasteboard concept
     * and flattens whatever it receives regardless. Falls back to [shareImage] if Instagram isn't
     * installed. Unused on iOS — never called there since [instagramSharePickerIsNative] is false.
     */
    fun shareToInstagram(opaqueBytes: ByteArray)

    /**
     * iOS only: the one proven, documented mechanism Meta publishes for any third-party app —
     * `instagram-stories://share` + the `stickerImage` UIPasteboard key. One tap, no permission,
     * no Photos save, lands directly in the Stories editor. [pngBytes] must be a real transparent
     * PNG (see `drawShareSticker`) — Instagram composites it over a background it generates itself.
     * Falls back to [shareImage] if Instagram isn't installed. On Android, delegates to
     * [shareToInstagram] (that single call already reaches Story via Instagram's own picker).
     *
     * Direct-message sharing (an equivalent "send to a specific person" call) was tried and ruled
     * out: on-device diagnostics confirmed the only known scheme, `instagram://direct-inbox`, does
     * successfully launch Instagram (canOpenURL/openURL both succeed) but opens a blank inbox with
     * no image or contact attached — Meta has never published a Direct equivalent of the Stories
     * sticker API, unlike Stories, which Meta actively wants third-party apps to drive traffic to.
     */
    fun shareToInstagramStory(pngBytes: ByteArray)
}
