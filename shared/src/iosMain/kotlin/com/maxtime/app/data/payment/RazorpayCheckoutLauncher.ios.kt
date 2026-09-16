package com.maxtime.app.data.payment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import cocoapods.RazorpayStandard.RazorpayCheckout
import cocoapods.RazorpayStandard.RazorpayPaymentCompletionProtocolWithDataProtocol

actual class RazorpayCheckoutLauncher {
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun launch(request: RazorpayCheckoutRequest): RazorpayCheckoutResult =
        suspendCancellableCoroutine { cont ->
            val root = topMostViewController()
            if (root == null) {
                cont.resume(RazorpayCheckoutResult.Failure("Could not open the payment screen."))
                return@suspendCancellableCoroutine
            }

            val prefill = buildMap {
                request.prefillEmail?.let { put("email", it) }
                request.prefillContact?.let { put("contact", it) }
            }
            val options = buildMap<Any?, Any?> {
                put("name", request.name)
                put("description", request.description)
                put("order_id", request.order.orderId)
                put("currency", request.order.currency)
                put("amount", (request.order.amount * 100).toString())
                if (prefill.isNotEmpty()) put("prefill", prefill)
            }

            val delegate = CheckoutDelegate(cont)
            retainedDelegate = delegate
            cont.invokeOnCancellation { retainedDelegate = null }

            val checkout = RazorpayCheckout.initWithKey(request.order.keyId, andDelegateWithData = delegate)
            checkout.open(options, displayController = root)
        }
}

@Composable
actual fun rememberRazorpayCheckoutLauncher(): RazorpayCheckoutLauncher = remember { RazorpayCheckoutLauncher() }

/** Keep the delegate alive for the continuation's lifetime — without a strong reference held
 *  somewhere, ARC is free to deallocate it before Razorpay's callback fires (same pattern as
 *  ImagePicker.ios.kt's retainedPickerDelegate). */
@OptIn(ExperimentalForeignApi::class)
private var retainedDelegate: NSObject? = null

@OptIn(ExperimentalForeignApi::class)
private class CheckoutDelegate(
    private val cont: CancellableContinuation<RazorpayCheckoutResult>,
) : NSObject(), RazorpayPaymentCompletionProtocolWithDataProtocol {

    override fun onPaymentSuccess(payment_id: String, andData: Map<Any?, *>?) {
        if (cont.isActive) cont.resume(RazorpayCheckoutResult.Success(payment_id))
        retainedDelegate = null
    }

    override fun onPaymentError(code: Int, description: String, andData: Map<Any?, *>?) {
        if (cont.isActive) cont.resume(RazorpayCheckoutResult.Failure(description))
        retainedDelegate = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun topMostViewController(): UIViewController? {
    val windowScene = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull() ?: return null
    val keyWindow = windowScene.windows
        .filterIsInstance<UIWindow>()
        .firstOrNull { it.isKeyWindow() }
        ?: windowScene.windows.filterIsInstance<UIWindow>().firstOrNull()
    var top = keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
