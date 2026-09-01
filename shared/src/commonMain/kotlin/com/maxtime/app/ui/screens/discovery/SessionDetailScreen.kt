package com.maxtime.app.ui.screens.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.ui.components.AvatarStack
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SecondaryButton
import com.maxtime.app.ui.components.SectionLabel
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

@Composable
fun SessionDetailScreen(state: AppState, router: Router) {
    LaunchedEffect(Unit) {
        if (state.selectedSession == null) state.selectSession()
    }
    val session = state.selectedSession ?: return
    val info = state.activityInfo(session.activity)

    ScreenScaffold(
        topBar = { TopBar(title = "Session", onBack = { router.goBack() }) },
        bottomBar = {
            BottomCta {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Find People", Modifier.weight(1f), onClick = { router.navigate(Screen.FindResults) })
                    PrimaryButton(
                        "Book Solo" + if (session.price > 0) " — ₹${session.price}" else "",
                        Modifier.weight(1f),
                        onClick = { router.navigate(Screen.BookingReview) }
                    )
                }
            }
        }
    ) {
        Box(
            Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 14.dp).fillMaxWidth().height(150.dp)
                .clip(MaxTimeShapes.large).background(MaxTimeColors.bg2),
            contentAlignment = Alignment.Center
        ) { Text(info.icon, fontSize = 40.sp) }

        Text(session.venueName, color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding))
        Text("${state.location.area} · ${session.activity}", style = MaxTimeType.caption, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 2.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("When", session.whenLabel, Modifier.weight(1f))
            StatBox("Duration", "${session.durationMinutes} min", Modifier.weight(1f))
            StatBox("Price", if (session.price > 0) "₹${session.price}" else "Free", Modifier.weight(1f))
        }

        SectionLabel("Capacity")
        MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)) {
            Text("${session.capacity} total · ${session.spotsLeft} spots left", color = MaxTimeColors.text, fontSize = 12.sp)
        }

        SectionLabel("Cancellation policy")
        MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)) {
            Text(session.cancellationPolicy, color = MaxTimeColors.text, fontSize = 12.sp)
        }

        MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AvatarStack(listOf("A", "B", "C"))
                Text("3 going", style = MaxTimeType.caption)
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    MaxTimeCard(modifier) {
        Text(label.uppercase(), color = MaxTimeColors.muted, fontSize = 9.5.sp)
        Text(value, color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
