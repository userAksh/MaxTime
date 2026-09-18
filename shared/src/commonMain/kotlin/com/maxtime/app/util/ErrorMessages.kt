package com.maxtime.app.util

/**
 * Turns a caught exception into text that's actually safe to show a user. Firebase (and other
 * SDKs) routinely surface raw, implementation-detail strings as `Throwable.message` — e.g.
 * `"Error Domain=FIRAuthErrorDomain Code=17044 ..."` on iOS — that leak internal error codes and
 * domain names instead of explaining what went wrong. [fallback] is the DEFAULT outcome, not a
 * last resort: `e.message` itself is never returned directly. Only a small set of recognized,
 * genuinely-actionable cases (matched by keyword/code substring, since GitLive's shared
 * `Exception` type doesn't expose a structured error code the same way on both platforms) get a
 * more specific message than [fallback]; everything else — including anything unrecognized —
 * falls back to whatever context-appropriate default the caller already had.
 */
fun friendlyErrorMessage(e: Throwable, fallback: String): String {
    val raw = e.message ?: return fallback
    return when {
        raw.contains("17044") || (raw.contains("invalid", ignoreCase = true) && raw.contains("verification code", ignoreCase = true)) ->
            "That code doesn't look right. Double-check it and try again."
        raw.contains("17051") || raw.contains("expired", ignoreCase = true) ->
            "This code has expired. Tap \"Resend code\" to get a new one."
        raw.contains("too-many-requests", ignoreCase = true) || raw.contains("too many requests", ignoreCase = true) ->
            "Too many attempts. Please wait a few minutes and try again."
        raw.contains("network", ignoreCase = true) ->
            "Check your internet connection and try again."
        raw.contains("invalid", ignoreCase = true) && raw.contains("phone", ignoreCase = true) ->
            "That doesn't look like a valid phone number."
        raw.contains("quota", ignoreCase = true) ->
            "We're temporarily unable to do this right now. Please try again shortly."
        else -> fallback
    }
}
