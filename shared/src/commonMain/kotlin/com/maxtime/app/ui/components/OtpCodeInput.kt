package com.maxtime.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Digit-only OTP entry, styled to match the app's other text fields but implemented per-platform
 * so the underlying input can actually offer real SMS-code autofill:
 * Android via Compose's `ContentType.SmsOtpCode` semantics hint (Compose's own Autofill
 * framework — no `RECEIVE_SMS` permission, since Firebase controls its own SMS wording and the
 * fully-silent SMS Retriever API needs an app-signature hash we can't inject into it), iOS via a
 * genuinely native `UITextField` with `textContentType = .oneTimeCode` (Compose Multiplatform's
 * `BasicTextField` doesn't expose that hook on iOS at all).
 *
 * [onComplete] fires the instant [value] reaches 6 digits, regardless of whether they arrived by
 * typing or autofill — this is what makes verification automatic, not just the code entry.
 *
 * [refocusTrigger] re-requests focus (and, on iOS, retries `becomeFirstResponder()`) whenever it
 * changes — pass a value that changes once per fresh code sent (e.g. an attempt counter bumped on
 * both the initial send and every resend). Without this, only the very first send would land the
 * keyboard automatically: a resend doesn't remount this composable (it's the same field, same
 * screen), so a one-shot "focus on first appearance" wouldn't fire again for the new code.
 */
@Composable
expect fun OtpCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    refocusTrigger: Any = Unit
)
