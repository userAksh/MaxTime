package com.maxtime.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Holds a referral code extracted from an incoming Universal Link (iOS) / App Link (Android) —
 * `https://maxtime.in/r/<code>` — until the onboarding referral-entry screen is ready to consume
 * it. Each platform's native entry point calls [handle] with the raw incoming URL the moment the
 * OS delivers it:
 * - iOS: `AppDelegate.application(_:continue:restorationHandler:)` for an `NSUserActivity` of
 *   type `.browsingWeb`.
 * - Android: `MainActivity.onCreate`/`onNewIntent`, reading the launching `Intent`'s `data` URI.
 *
 * A plain object (not tied to any screen's lifecycle) because the link can arrive before the UI
 * that needs it has composed yet — cold-start launches on both platforms hand the Activity/
 * AppDelegate the URL before any Compose content exists.
 */
object ReferralDeepLink {
    var pendingCode by mutableStateOf<String?>(null)
        private set

    private val pathRegex = Regex("/r/([A-Za-z0-9]+)")

    fun handle(url: String) {
        pathRegex.find(url)?.groupValues?.get(1)?.let { pendingCode = it.uppercase() }
    }

    /** One-shot read — clears [pendingCode] so the same link can't silently re-trigger a second
     *  auto-redeem attempt if ReferralScreen recomposes again later in the same session. */
    fun consume(): String? {
        val code = pendingCode
        pendingCode = null
        return code
    }
}
