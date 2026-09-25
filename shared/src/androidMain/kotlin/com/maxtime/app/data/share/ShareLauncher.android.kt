package com.maxtime.app.data.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

// The component ACTION_SEND resolves Instagram's feed/story share target to — well-precedented
// (many open-source share-intent libraries target this exact class directly), but still an
// internal Instagram implementation detail Meta could change without notice; if Instagram ever
// stops appearing correctly in the chooser, verify this against a fresh `adb shell dumpsys
// package com.instagram.android` on a real device before assuming something else broke.
private const val INSTAGRAM_PACKAGE = "com.instagram.android"
private const val INSTAGRAM_SHARE_HANDLER_ACTIVITY = "com.instagram.share.handleractivity.ShareHandlerActivity"

actual class ShareLauncher(private val activity: Activity) {
    actual fun shareText(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(sendIntent, null))
    }

    actual fun shareImage(pngBytes: ByteArray, caption: String?, targetPackage: String?, instagramBytes: ByteArray?) {
        val uri = writeShareImage(activity, pngBytes, "referral_share.png")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (caption != null) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            targetPackage?.let { setPackage(it) }
        }
        // Only relevant when this is actually going to show a chooser (targetPackage == null) —
        // a targeted share (WhatsApp/Snapchat's own icon) launches straight into that app with no
        // chooser at all, so Instagram can never be the destination there.
        if (targetPackage == null && instagramBytes != null) {
            try {
                activity.startActivity(instagramAwareChooser(intent, instagramBytes))
                return
            } catch (e: ActivityNotFoundException) {
                // Fall through to the plain chooser below.
            }
        }
        try {
            activity.startActivity(if (targetPackage != null) intent else Intent.createChooser(intent, null))
        } catch (e: ActivityNotFoundException) {
            // The requested app (e.g. WhatsApp/Snapchat) isn't installed — the image should still
            // be shareable via whatever else is, not silently fail.
            intent.setPackage(null)
            activity.startActivity(Intent.createChooser(intent, null))
        }
    }

    /** Pins a separately-imaged, Instagram-targeted intent at the top of the chooser (via
     *  [Intent.EXTRA_INITIAL_INTENTS]) and excludes Instagram's own default resolution of
     *  [genericIntent] (via [Intent.EXTRA_EXCLUDE_COMPONENTS]) so it doesn't also appear a second
     *  time showing the wrong (non-Instagram) image. Every other app in the chooser resolves
     *  [genericIntent] completely normally. */
    private fun instagramAwareChooser(genericIntent: Intent, instagramBytes: ByteArray): Intent {
        val instagramUri = writeShareImage(activity, instagramBytes, "referral_share_instagram.png")
        activity.grantUriPermission(INSTAGRAM_PACKAGE, instagramUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val instagramIntent = Intent(Intent.ACTION_SEND).apply {
            setPackage(INSTAGRAM_PACKAGE)
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, instagramUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(genericIntent, null).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(INSTAGRAM_PACKAGE, INSTAGRAM_SHARE_HANDLER_ACTIVITY)))
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(instagramIntent))
        }
    }

    actual fun shareToInstagramStories(pngBytes: ByteArray) {
        val uri = writeShareImage(activity, pngBytes, "referral_share_sticker.png")
        activity.grantUriPermission(INSTAGRAM_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            type = "image/png"
            // interactive_asset_uri is Instagram/Meta's own sticker-asset key (confirmed via
            // Meta's official Android Stories-sharing docs) — it already preserves this PNG's
            // alpha channel, compositing it as a floating transparent sticker rather than
            // flattening it, unlike a plain full-bleed background image. top_background_color/
            // bottom_background_color fill the canvas around the sticker with this card's own
            // gradient instead of Instagram's default. pngBytes here must come from
            // drawShareSticker (real rounded corners, real transparent corners).
            putExtra("interactive_asset_uri", uri)
            putExtra("top_background_color", "#0E2413")
            putExtra("bottom_background_color", "#040B06")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            shareImage(pngBytes, caption = null, targetPackage = null)
        }
    }
}

/** Writes into a private cache subfolder, not the public MediaStore gallery — unlike a common
 *  reference implementation that inserts every generated share-card into the user's actual
 *  Photos app (permanent gallery clutter for a throwaway image), this is a temp file exposed to
 *  other apps only via the FileProvider's short-lived content:// URI (see AndroidManifest.xml /
 *  file_paths.xml). Reused for every share call — a stable filename, overwritten each time, is
 *  fine since only one share-card is ever "current" at once. */
private fun writeShareImage(context: Context, bytes: ByteArray, fileName: String): Uri {
    val dir = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeBytes(bytes)
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

@Composable
actual fun rememberShareLauncher(): ShareLauncher {
    val activity = LocalContext.current as Activity
    return ShareLauncher(activity)
}
