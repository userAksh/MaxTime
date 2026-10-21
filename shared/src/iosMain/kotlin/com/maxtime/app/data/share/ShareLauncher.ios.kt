package com.maxtime.app.data.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIActivityItemProvider
import platform.UIKit.UIActivityType
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

// Instagram's share-extension activity type — NOT documented by Meta anywhere; this exact string
// is a long-standing community-observed value (widely reused across other apps' share-sheet
// integrations) for the activity iOS reports when a person picks Instagram from a plain
// UIActivityViewController sheet. It could silently stop matching after an Instagram app update —
// if the Instagram-from-native-sheet card ever stops appearing, re-check this by logging
// `itemForActivityType`'s actual value on a real device before assuming something else broke.
private const val INSTAGRAM_SHARE_EXTENSION_ACTIVITY_TYPE = "com.burbn.instagram.shareextension"

/** Lets [ShareLauncher.shareImage] hand Instagram a different, purpose-built image than every
 *  other app in the same share sheet — see [ShareLauncher.shareImage]'s "instagramBytes" doc
 *  comment for why. [UIActivityItemProvider] is the one API that defers the choice of item until
 *  the OS already knows which app the person picked (via `itemForActivityType`), unlike a plain
 *  array of activity items which is fixed before the sheet even opens. */
@OptIn(ExperimentalForeignApi::class)
private class InstagramAwareShareItem(
    placeholder: UIImage,
    private val defaultImage: UIImage,
    private val instagramImage: UIImage
) : UIActivityItemProvider(placeholderItem = placeholder) {
    override fun activityViewController(
        activityViewController: UIActivityViewController,
        itemForActivityType: UIActivityType
    ): Any? = if (itemForActivityType == INSTAGRAM_SHARE_EXTENSION_ACTIVITY_TYPE) instagramImage else defaultImage
}

actual class ShareLauncher {
    @OptIn(ExperimentalForeignApi::class)
    actual fun shareText(text: String) {
        val root = topMostViewController() ?: return
        val activityController = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        root.presentViewController(activityController, animated = true, completion = null)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun shareImage(pngBytes: ByteArray, caption: String?, targetPackage: String?, instagramBytes: ByteArray?) {
        // targetPackage is an Android-only concept (setPackage on an Intent) — UIActivityViewController
        // has no equivalent "jump straight into this one app" mode, the person always picks from
        // its own sheet, so it's intentionally unused here.
        val root = topMostViewController() ?: return
        val image = UIImage(data = pngBytes.toNSData())
        // Every shareImage call goes through this same sheet on iOS (targetPackage is ignored),
        // so Instagram can be picked from any of them — instagramBytes is passed on every call,
        // not gated behind targetPackage == null the way Android's chooser-only branch is.
        val imageItem: Any = if (instagramBytes != null) {
            InstagramAwareShareItem(
                placeholder = image,
                defaultImage = image,
                instagramImage = UIImage(data = instagramBytes.toNSData())
            )
        } else {
            image
        }
        val items = listOfNotNull(imageItem, caption)
        val activityController = UIActivityViewController(activityItems = items, applicationActivities = null)
        root.presentViewController(activityController, animated = true, completion = null)
    }

    // targetPackage is unused here for the same reason as in shareImage — no iOS equivalent of
    // "jump straight into this one app" exists for UIActivityViewController.
    @OptIn(ExperimentalForeignApi::class)
    actual fun shareLink(url: String, targetPackage: String?) {
        val root = topMostViewController() ?: return
        val activityController = UIActivityViewController(activityItems = listOf(NSURL(string = url)), applicationActivities = null)
        root.presentViewController(activityController, animated = true, completion = null)
    }

    // Android-only capability — no iOS mechanism reaches Instagram's own multi-destination
    // picker with a single call, see this property's doc comment on the expect declaration.
    actual val instagramSharePickerIsNative: Boolean = false

    // Never actually called on iOS since instagramSharePickerIsNative is false — the screen only
    // calls shareToInstagramStory here. Falls back to the generic sheet for safety if it's ever
    // reached anyway.
    @OptIn(ExperimentalForeignApi::class)
    actual fun shareToInstagram(opaqueBytes: ByteArray) {
        shareImage(opaqueBytes, caption = null, targetPackage = null)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun shareToInstagramStory(pngBytes: ByteArray) {
        val bundleId = NSBundle.mainBundle.bundleIdentifier ?: ""
        val url = NSURL(string = "instagram-stories://share?source_application=$bundleId")
        // canOpenURL only returns true for schemes this app declared in Info.plist's
        // LSApplicationQueriesSchemes AND that are actually installed — covers both "Instagram
        // isn't installed" and "we forgot to whitelist the scheme" with the same fallback.
        if (url == null || !UIApplication.sharedApplication.canOpenURL(url)) {
            shareImage(pngBytes, caption = null, targetPackage = null)
            return
        }
        // "stickerImage" (not "backgroundImage") is the key that actually preserves this PNG's
        // alpha channel — Instagram composites it as a floating, transparent sticker on top of a
        // generated background, rather than flattening it into an opaque full-bleed background.
        // backgroundTopColor/backgroundBottomColor fill the canvas around the sticker with this
        // card's own gradient instead of Instagram's default. pngBytes here must come from
        // drawShareSticker (real rounded corners, real transparent corners).
        val pasteboardItem = mapOf<Any?, Any?>(
            "com.instagram.sharedSticker.stickerImage" to pngBytes.toNSData(),
            "com.instagram.sharedSticker.backgroundTopColor" to "#0E2413",
            "com.instagram.sharedSticker.backgroundBottomColor" to "#040B06"
        )
        UIPasteboard.generalPasteboard.setItems(
            listOf(pasteboardItem),
            options = mapOf<Any?, Any?>(UIPasteboardOptionExpirationDate to NSDate.dateWithTimeIntervalSinceNow(300.0))
        )
        // The deprecated single-argument openURL(_:) is silently forced to return false on iOS 18+
        // (confirmed via on-device diagnostic: canOpenURL=true, openURL(_:) returned false) —
        // it never actually launched Instagram despite reporting success. The 3-argument overload
        // below is still functional and is what's now required to actually switch apps.
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

@Composable
actual fun rememberShareLauncher(): ShareLauncher = remember { ShareLauncher() }

// Duplicated from ImagePicker.ios.kt/RazorpayCheckoutLauncher.ios.kt — no shared iosMain util
// exists yet for this, matching how both of those already keep their own private copy.
@OptIn(ExperimentalForeignApi::class)
private fun topMostViewController(): UIViewController? {
    val windowScene = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull() ?: return null
    val keyWindow = windowScene.windows
        .filterIsInstance<UIWindow>()
        .firstOrNull { it.isKeyWindow() }
        ?: windowScene.windows.filterIsInstance<UIWindow>().firstOrNull()
    var top = keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
