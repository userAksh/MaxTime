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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.Group
import com.maxtime.app.ui.components.AvatarPerson
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
    var checkedSession by remember { mutableStateOf(state.selectedSession != null) }
    LaunchedEffect(Unit) {
        if (state.selectedSession == null) state.selectSession()
        checkedSession = true
    }

    val session = state.selectedSession
    if (session == null) {
        ScreenScaffold(topBar = { TopBar(title = "Session", onBack = { router.goBack() }) }) {
            if (checkedSession) {
                Text(
                    "No real sessions available for ${state.intent.activity} right now — check back once a partner has openings.",
                    color = MaxTimeColors.muted, fontSize = 12.5.sp,
                    modifier = Modifier.padding(MaxTimeSpacing.screenPadding)
                )
            }
        }
        return
    }
    val info = state.activityInfo(session.activity)

    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    LaunchedEffect(session.id) {
        groups = state.groupsForActivity(session.activity)
    }
    val exactGroup = groups.firstOrNull { it.sessionId == session.id }
    val sameDayElsewhere = groups.firstOrNull { it.sessionId != session.id && it.sessionDate == session.date }

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

        if (exactGroup != null) {
            MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    AvatarStack(
                        exactGroup.memberList.map { AvatarPerson(it.personName, it.photoUrl) }
                            .ifEmpty { listOf(AvatarPerson(exactGroup.creator)) }
                    )
                    Text("${exactGroup.members} going", style = MaxTimeType.caption)
                }
            }
        } else if (sameDayElsewhere != null) {
            MaxTimeCard(
                Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp),
                onClick = {
                    state.intent = state.intent.copy(activity = session.activity)
                    router.navigate(Screen.FindResults)
                }
            ) {
                Text(
                    "People are already doing this the same day, but at a different time — want to check?",
                    color = MaxTimeColors.text, fontSize = 12.5.sp
                )
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
