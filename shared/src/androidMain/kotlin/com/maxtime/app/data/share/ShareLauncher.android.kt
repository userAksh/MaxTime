package com.maxtime.app.data.share

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

actual class ShareLauncher(private val activity: Activity) {
    actual fun shareText(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(sendIntent, null))
    }
}

@Composable
actual fun rememberShareLauncher(): ShareLauncher {
    val activity = LocalContext.current as Activity
    return ShareLauncher(activity)
}
