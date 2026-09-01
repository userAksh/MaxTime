package com.maxtime.app.data.firebase

import androidx.compose.runtime.Composable
import dev.gitlive.firebase.auth.PhoneVerificationProvider

/** Platform handle needed to start phone verification (an Activity on Android, nothing on iOS). */
expect class PhoneAuthContext

@Composable
expect fun rememberPhoneAuthContext(): PhoneAuthContext

/**
 * `getVerificationCode` is called exactly once per verification attempt on both platforms,
 * right before Firebase needs the SMS code — use it as the single hook to reveal the OTP
 * entry UI and suspend until the user submits it.
 */
expect fun buildPhoneVerificationProvider(
    context: PhoneAuthContext,
    getVerificationCode: suspend () -> String,
): PhoneVerificationProvider
