package com.maxtime.app.data.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

actual class ShareLauncher(private val activity: Activity) {
    actual fun shareText(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(sendIntent, null))
    }

    actual fun shareImage(pngBytes: ByteArray, caption: String?, targetPackage: String?) {
        val uri = writeShareImage(activity, pngBytes)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (caption != null) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            targetPackage?.let { setPackage(it) }
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

    actual fun shareToInstagramStories(pngBytes: ByteArray) {
        val uri = writeShareImage(activity, pngBytes)
        activity.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
private fun writeShareImage(context: Context, bytes: ByteArray): Uri {
    val dir = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(dir, "referral_share.png")
    file.writeBytes(bytes)
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

@Composable
actual fun rememberShareLauncher(): ShareLauncher {
    val activity = LocalContext.current as Activity
    return ShareLauncher(activity)
}
