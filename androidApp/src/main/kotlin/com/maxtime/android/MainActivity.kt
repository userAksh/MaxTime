package com.maxtime.android

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maxtime.app.analytics.AnalyticsTracker
import com.maxtime.app.data.ReferralDeepLink
import com.maxtime.app.data.location.AppContextHolder
import com.maxtime.app.data.payment.RazorpayCheckoutBridge
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.maxtime.app.MaxTimeApp as MaxTimeAppRoot

class MaxTimeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
        AnalyticsTracker.initialize(
            platform = "android",
            appVersion = BuildConfig.VERSION_NAME,
            environment = BuildConfig.ANALYTICS_ENVIRONMENT,
            mixpanelToken = BuildConfig.MIXPANEL_TOKEN,
        )
    }
}

// PaymentResultWithDataListener is required here, not inside shared's RazorpayCheckoutLauncher —
// Razorpay's closed-source SDK calls back into whichever Activity hosted Checkout.open() directly
// (not via the standard onActivityResult contract), so the callback has to land on a real Activity
// class. Both overrides just forward to RazorpayCheckoutBridge, which completes whatever suspend
// call is currently awaiting a result.
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This app's background is always dark (MaxTimeColors), regardless of the device's
        // system light/dark mode setting — SystemBarStyle.dark() pins status/nav bar icons to
        // light/white unconditionally, instead of enableEdgeToEdge()'s default of following
        // the system theme (which would render invisible dark icons in system light mode).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        handleReferralIntent(intent)
        setContent {
            MaxTimeAppRoot()
        }
    }

    // App Links (singleTask launchMode in the manifest) redeliver here instead of creating a new
    // Activity instance when the link is tapped while the app's already running in the
    // background — see AndroidManifest.xml's autoVerify intent-filter.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReferralIntent(intent)
    }

    private fun handleReferralIntent(intent: Intent?) {
        intent?.data?.toString()?.let { ReferralDeepLink.handle(it) }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        RazorpayCheckoutBridge.onSuccess(razorpayPaymentId.orEmpty())
    }

    override fun onPaymentError(code: Int, description: String?, paymentData: PaymentData?) {
        RazorpayCheckoutBridge.onError(description ?: "Payment failed.")
    }
}
