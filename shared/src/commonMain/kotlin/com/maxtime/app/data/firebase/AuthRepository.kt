package com.maxtime.app.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.AuthResult
import dev.gitlive.firebase.auth.PhoneAuthProvider
import dev.gitlive.firebase.auth.PhoneVerificationProvider
import dev.gitlive.firebase.auth.auth

class AuthRepository {
    private val auth get() = Firebase.auth

    suspend fun verifyPhoneNumber(phoneNumber: String, verificationProvider: PhoneVerificationProvider): AuthResult {
        val credential = PhoneAuthProvider(auth).verifyPhoneNumber(phoneNumber, verificationProvider)
        return auth.signInWithCredential(credential)
    }

    suspend fun signInAnonymously(): AuthResult = auth.signInAnonymously()

    val currentUserId: String? get() = auth.currentUser?.uid
}
