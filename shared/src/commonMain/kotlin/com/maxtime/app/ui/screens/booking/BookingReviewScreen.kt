package com.maxtime.app.ui.screens.booking

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.payment.RazorpayCheckoutRequest
import com.maxtime.app.data.payment.RazorpayCheckoutResult
import com.maxtime.app.data.payment.rememberRazorpayCheckoutLauncher
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SectionLabel
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class ReviewInfo(val activity: String, val venue: String, val whenLabel: String, val where: String, val price: Int, val participants: String)

private fun reviewInfo(state: AppState): ReviewInfo {
    val g = state.selectedGroup
    val s = state.selectedSession
    return when {
        g != null -> ReviewInfo(g.activity, g.venue ?: "Session to be confirmed", g.whenLabel, g.where, g.priceNormal, "You + ${(g.members - 1).coerceAtLeast(0)}")
        s != null -> ReviewInfo(s.activity, s.venueName, s.whenLabel, state.location.area, s.price, "You (solo)")
        else -> {
            val info = state.activityInfo()
            ReviewInfo(state.intent.activity, "Venue to be confirmed", state.intent.whenLabel, state.location.area, info.price, "You (solo)")
        }
    }
}

@Composable
fun BookingReviewScreen(state: AppState, router: Router) {
    val scope = rememberCoroutineScope()
    val checkoutLauncher = rememberRazorpayCheckoutLauncher()
    var busyLabel by remember { mutableStateOf<String?>(null) }
    val review = reviewInfo(state)
    val fee = if (review.price > 0) (review.price * 0.06).roundToInt() else 0
    val total = review.price + fee

    ScreenScaffold(
        topBar = { TopBar(title = "Review Booking", onBack = { router.goBack() }) },
        bottomBar = {
            BottomCta {
                val holdError = state.bookingHoldError
                if (holdError != null) {
                    Text(
                        holdError,
                        color = MaxTimeColors.red, fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                } else if (state.paymentFailed) {
                    Text(
                        "Payment didn't go through. Try again, or change your payment method.",
                        color = MaxTimeColors.red, fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                }
                PrimaryButton(
                    busyLabel ?: (if (state.paymentFailed) "Retry payment" else "Confirm & Pay" + if (total > 0) " ₹$total" else ""),
                    enabled = busyLabel == null,
                    onClick = {
                        scope.launch {
                            busyLabel = "Booking…"
                            when (val step = state.confirmPayment()) {
                                is AppState.ConfirmPaymentStep.ReadyForCheckout -> {
                                    busyLabel = null // hand off to Razorpay's own checkout UI
                                    val contact = state.currentUser.phoneOrEmail
                                    val result = checkoutLauncher.launch(
                                        RazorpayCheckoutRequest(
                                            order = step.order,
                                            name = "MaxTime",
                                            description = "${review.activity} · ${review.whenLabel}",
                                            prefillEmail = contact?.takeIf { "@" in it },
                                            prefillContact = contact?.takeIf { "@" !in it }
                                        )
                                    )
                                    busyLabel = "Confirming payment…"
                                    val ok = state.finalizeCheckout(step.bookingId, result)
                                    busyLabel = null
                                    if (ok) router.navigate(Screen.BookingSuccess)
                                }
                                is AppState.ConfirmPaymentStep.Rejected -> busyLabel = null
                                AppState.ConfirmPaymentStep.Confirmed -> {
                                    busyLabel = null
                                    router.navigate(Screen.BookingSuccess)
                                }
                            }
                        }
                    }
                )
            }
        }
    ) {
        SectionLabel("${review.activity} · ${review.whenLabel}")
        MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)) {
            Text("${review.venue}, ${review.where} · Participants: ${review.participants}", color = MaxTimeColors.text, fontSize = 12.sp)
        }

        SectionLabel("Price")
        MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)) {
            PriceRow("Activity", if (review.price > 0) "₹${review.price}" else "Free")
            PriceRow("Fees", if (fee > 0) "₹$fee" else "₹0")
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).border(0.dp, MaxTimeColors.cardBorder))
            PriceRow("Total", if (total > 0) "₹$total" else "Free", emphasized = true)
        }
    }
}

@Composable
private fun PriceRow(key: String, value: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaxTimeColors.muted2, fontSize = if (emphasized) 14.sp else 12.5.sp, fontWeight = if (emphasized) FontWeight.ExtraBold else FontWeight.Normal)
        Text(value, color = if (emphasized) MaxTimeColors.accent2 else MaxTimeColors.textStrong, fontSize = if (emphasized) 14.sp else 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

