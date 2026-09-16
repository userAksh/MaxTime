package com.maxtime.app.data.payment

import androidx.compose.runtime.Composable
import com.maxtime.app.data.firebase.RazorpayOrderInfo

data class RazorpayCheckoutRequest(
    val order: RazorpayOrderInfo,
    val name: String,
    val description: String,
    val prefillEmail: String? = null,
    val prefillContact: String? = null,
)

sealed interface RazorpayCheckoutResult {
    data class Success(val paymentId: String) : RazorpayCheckoutResult
    data class Failure(val description: String) : RazorpayCheckoutResult
}

/** Platform-native checkout — Razorpay's SDK is not KMP-aware (Android needs a hosting Activity,
 *  iOS needs a presenting UIViewController), so this is resolved per-platform exactly like
 *  [com.maxtime.app.data.location.rememberLocationContext] and
 *  [com.maxtime.app.data.media.rememberImagePickerContext] already are. */
@Composable
expect fun rememberRazorpayCheckoutLauncher(): RazorpayCheckoutLauncher

expect class RazorpayCheckoutLauncher {
    suspend fun launch(request: RazorpayCheckoutRequest): RazorpayCheckoutResult
}
