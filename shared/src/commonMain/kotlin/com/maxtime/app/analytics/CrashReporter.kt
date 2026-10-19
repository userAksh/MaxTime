package com.maxtime.app.analytics

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics

/**
 * The one place UI/domain code talks to crash reporting — mirrors [AnalyticsTracker]'s own "don't
 * call the vendor SDK everywhere" rule. Unlike analytics, this uses the real native Firebase
 * Crashlytics SDK on both platforms (via the `dev.gitlive:firebase-crashlytics` KMP wrapper) —
 * Crashlytics' crash-capture mechanism (an uncaught-exception/signal handler installed at process
 * start, plus NDK/dSYM symbolication) is exactly the part that can't be reimplemented over a bare
 * HTTP API the way AnalyticsTracker's Mixpanel calls were; there's no equivalent workaround for
 * the cinterop gap here, since unlike Mixpanel-swift this needs real native crash-handler hooks,
 * not just an SDK method surface.
 *
 * Fatal crashes are captured automatically the moment the dependency is present — nothing to call
 * for that. What this wrapper is for: recording caught/non-fatal exceptions explicitly, and
 * keeping crash reports correlated with the same user identity Mixpanel uses (see
 * [identify]/[reset], called from the exact same places as [AnalyticsTracker.identify]/[reset]).
 *
 * Every call here is wrapped in [runCatching] — a Crashlytics call failing must never itself
 * crash the app or mask the real exception being reported.
 */
object CrashReporter {
    fun identify(userId: String) {
        runCatching { Firebase.crashlytics.setUserId(userId) }
    }

    fun reset() {
        runCatching { Firebase.crashlytics.setUserId("") }
    }

    /** For a caught exception worth knowing about (e.g. a repository call that degraded instead
     *  of crashing) — never for routine/expected failure paths, which would just be noise.
     *  [context], if given, is logged as a preceding breadcrumb — the pinned dev.gitlive version
     *  (2.6.0) doesn't yet have the newer recordException(exception, customKeys) overload. */
    fun recordException(throwable: Throwable, context: Map<String, String> = emptyMap()) {
        if (context.isNotEmpty()) log(context.entries.joinToString(", ") { "${it.key}=${it.value}" })
        runCatching { Firebase.crashlytics.recordException(throwable) }
    }

    fun log(message: String) {
        runCatching { Firebase.crashlytics.log(message) }
    }
}
