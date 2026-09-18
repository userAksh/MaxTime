package com.maxtime.app.data.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

actual class ShareLauncher {
    @OptIn(ExperimentalForeignApi::class)
    actual fun shareText(text: String) {
        val root = topMostViewController() ?: return
        val activityController = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        root.presentViewController(activityController, animated = true, completion = null)
    }
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
