package com.maxtime.app.data.firebase

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gitlive.firebase.auth.PhoneVerificationProvider
import java.util.concurrent.TimeUnit

actual typealias PhoneAuthContext = Activity

@Composable
actual fun rememberPhoneAuthContext(): PhoneAuthContext = LocalContext.current as Activity

actual fun buildPhoneVerificationProvider(
    context: PhoneAuthContext,
    getVerificationCode: suspend () -> String,
): PhoneVerificationProvider = object : PhoneVerificationProvider {
    override val activity: Activity = context
    override val timeout: Long = 60
    override val unit: TimeUnit = TimeUnit.SECONDS
    override fun codeSent(triggerResend: (Unit) -> Unit) {}
    override suspend fun getVerificationCode(): String = getVerificationCode.invoke()
}
