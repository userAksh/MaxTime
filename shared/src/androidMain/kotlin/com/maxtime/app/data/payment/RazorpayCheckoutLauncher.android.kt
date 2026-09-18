package com.maxtime.app.data.payment

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.maxtime.app.util.friendlyErrorMessage
import com.razorpay.Checkout
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject

/**
 * Razorpay's Android SDK is closed-source and does NOT return its result via the standard
 * `onActivityResult` contract — `Checkout.open(activity, options)` requires the hosting Activity
 * to itself implement `PaymentResultWithDataListener`, and the SDK calls back into it directly
 * once its own internal checkout Activity finishes. That's the one part of this bridge that can't
 * be self-contained inside `shared` the way `LocationSupport`/`ImagePicker` are — `MainActivity`
 * implements the listener and forwards both callbacks here.
 */
object RazorpayCheckoutBridge {
    private var pending: CompletableDeferred<RazorpayCheckoutResult>? = null

    internal fun begin(deferred: CompletableDeferred<RazorpayCheckoutResult>) {
        pending = deferred
    }

    fun onSuccess(paymentId: String) {
        pending?.complete(RazorpayCheckoutResult.Success(paymentId))
        pending = null
    }

    fun onError(description: String) {
        pending?.complete(RazorpayCheckoutResult.Failure(description))
        pending = null
    }
}

actual class RazorpayCheckoutLauncher(private val activity: Activity) {
    actual suspend fun launch(request: RazorpayCheckoutRequest): RazorpayCheckoutResult {
        val deferred = CompletableDeferred<RazorpayCheckoutResult>()
        RazorpayCheckoutBridge.begin(deferred)

        val checkout = Checkout()
        checkout.setKeyID(request.order.keyId)
        val options = JSONObject().apply {
            put("name", request.name)
            put("description", request.description)
            put("order_id", request.order.orderId)
            put("currency", request.order.currency)
            put("amount", request.order.amount * 100) // paise, matches the order Razorpay already created
            val prefill = JSONObject()
            request.prefillEmail?.let { prefill.put("email", it) }
            request.prefillContact?.let { prefill.put("contact", it) }
            if (prefill.length() > 0) put("prefill", prefill)
        }

        try {
            checkout.open(activity, options)
        } catch (e: Exception) {
            RazorpayCheckoutBridge.onError(friendlyErrorMessage(e, "Could not open the payment screen."))
        }

        return deferred.await()
    }
}

@Composable
actual fun rememberRazorpayCheckoutLauncher(): RazorpayCheckoutLauncher {
    val activity = LocalContext.current as Activity
    return RazorpayCheckoutLauncher(activity)
}
