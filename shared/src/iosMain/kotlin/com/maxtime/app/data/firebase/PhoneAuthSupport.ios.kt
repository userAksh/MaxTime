package com.maxtime.app.data.firebase

import androidx.compose.runtime.Composable
import cocoapods.FirebaseAuth.FIRAuthUIDelegateProtocol
import dev.gitlive.firebase.auth.PhoneVerificationProvider
import kotlinx.cinterop.ExperimentalForeignApi

actual class PhoneAuthContext

@Composable
actual fun rememberPhoneAuthContext(): PhoneAuthContext = PhoneAuthContext()

@OptIn(ExperimentalForeignApi::class)
actual fun buildPhoneVerificationProvider(
    context: PhoneAuthContext,
    getVerificationCode: suspend () -> String,
): PhoneVerificationProvider = object : PhoneVerificationProvider {
    override val delegate: FIRAuthUIDelegateProtocol? = null
    override suspend fun getVerificationCode(): String = getVerificationCode.invoke()
}
