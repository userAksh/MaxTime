package com.maxtime.app.ui.screens.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.BookingStatus
import com.maxtime.app.data.firebase.MyBooking
import com.maxtime.app.ui.components.BottomNav
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SectionLabel
import com.maxtime.app.ui.components.StatusChip
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

/** Your schedule. Two distinct sources, shown as separate sections: real, Firestore-backed
 *  Bookings (what you've actually paid for — including solo bookings, which never touch the
 *  Group system) and the existing mock/local Groups ("start an activity"/"join a group"). */
@Composable
fun PlansScreen(state: AppState, router: Router) {
    val bookingsFlow = remember { state.observeMyBookings() }
    val bookings by bookingsFlow.collectAsState(initial = emptyList<MyBooking>())

    ScreenScaffold(
        topBar = {
            Text("Plans", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp))
            Text(
                "Your upcoming and pending activities.",
                style = MaxTimeType.body,
                modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)
            )
        },
        bottomBar = { BottomNav(router, active = Screen.Plans) }
    ) {
        SectionLabel("Bookings")
        if (bookings.isEmpty()) {
            Text(
                "No bookings yet — find a real session and book it.",
                color = MaxTimeColors.muted, fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)
            )
        } else {
            bookings.forEach { booking ->
                MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(booking.activity, color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        StatusChip(booking.statusLabel, booking.statusVariant)
                    }
                    Text(booking.whenLabel, style = MaxTimeType.caption, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        "${booking.venueName} · ${booking.serviceName}" + if (booking.amount > 0) " · ₹${booking.amount}" else "",
                        style = MaxTimeType.caption,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        SectionLabel("Groups")
        val groups = state.groups.values.toList()
        if (groups.isEmpty()) {
            Text(
                "No group activities yet — find something to do and lock in a spot.",
                color = MaxTimeColors.muted, fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)
            )
        } else {
            groups.forEach { group ->
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    onClick = {
                        state.selectedGroup = group
                        router.navigate(Screen.GroupDetail)
                    }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${group.icon} ${group.activity}", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        StatusChip(group.statusLabel, group.statusVariant)
                    }
                    Text(group.whenLabel, style = MaxTimeType.caption, modifier = Modifier.padding(top = 4.dp))
                    Text(group.venueLine, style = MaxTimeType.caption, modifier = Modifier.padding(top = 2.dp))
                    if (group.bookingStatus == BookingStatus.CONFIRMED) {
                        Text("✓ Booked", color = MaxTimeColors.accent2, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}
