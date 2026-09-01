package com.maxtime.app.ui.screens.intent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.Session
import com.maxtime.app.data.StatusChipVariant
import com.maxtime.app.ui.components.AvatarStack
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SecondaryButton
import com.maxtime.app.ui.components.SectionLabel
import com.maxtime.app.ui.components.StatusChip
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType
import kotlinx.coroutines.launch

@Composable
fun FindResultsScreen(state: AppState, router: Router) {
    val scope = rememberCoroutineScope()
    val info = state.activityInfo()
    var sessions by remember { mutableStateOf<List<Session>>(emptyList()) }
    LaunchedEffect(state.intent.activity, state.location) {
        sessions = state.sessionsFor()
    }
    val people = state.eligiblePeopleForIntent()

    ScreenScaffold(
        topBar = { TopBar(onBack = { router.goBack() }) },
        bottomBar = {
            androidx.compose.foundation.layout.Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaxTimeColors.bg1)
                        .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryButton("Create an activity", Modifier.weight(1f), onClick = { router.navigate(Screen.CreateGroup) })
                    PrimaryButton("Book solo", Modifier.weight(1f), onClick = {
                        scope.launch {
                            state.selectSession()
                            router.navigate(Screen.SessionDetail)
                        }
                    })
                }
            }
        }
    ) {
        MaxTimeCard(
            Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp),
            onClick = { router.navigate(Screen.ActivityDetail) }
        ) {
            Text("${info.icon} ${state.intent.activity}", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(
                "Requested: ${state.intent.whenLabel} · Within ${state.location.radiusLabel} of ${state.location.area}",
                style = MaxTimeType.caption,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            "People are already doing this",
            style = MaxTimeType.body,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding)
        )

        SectionLabel("Join a group", actionText = "No invite used")
        if (sessions.isEmpty()) {
            EmptyNote("Nothing nearby yet. Try expanding your radius or choose another time.")
        } else {
            sessions.take(2).forEachIndexed { index, session ->
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    onClick = {
                        scope.launch {
                            val g = state.joinExistingGroup()
                            if (g != null) router.navigate(Screen.GroupDetail)
                        }
                    }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            val going = if (index == 0) "3 going · 1 spot open" else "2 going · 2 spots open"
                            Text(going, color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            Text("${session.venueName} · ${session.date}, ${session.startTime} · ${session.distanceKm} km", style = MaxTimeType.caption)
                        }
                        StatusChip("Filling", StatusChipVariant.FILLING)
                    }
                    if (index == 0) {
                        Text("◆ Ayesha, who you know, is going", color = MaxTimeColors.accent2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        AvatarStack(if (index == 0) listOf("A", "B", "C") else listOf("A", "B"))
                        androidx.compose.foundation.layout.Box(
                            Modifier.clip(RoundedCornerShape(9.dp)).background(MaxTimeColors.accent).padding(horizontal = 14.dp, vertical = 7.dp)
                        ) { Text("Join group", color = MaxTimeColors.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        SectionLabel("People you could invite", actionText = "Uses 1 invite if accepted")
        if (people.isEmpty()) {
            EmptyNote("No compatible people found for ${state.intent.activity} yet. Try inviting a connection, or check back soon.")
        } else {
            people.forEach { person ->
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    onClick = {
                        state.selectedPersonName = person.name
                        router.navigate(Screen.PersonDetail)
                    }
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AvatarStack(listOf(person.name), size = 52.dp)
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 6.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(start = 6.dp)) {
                            Text(person.name, color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            val connected = if (person.connected) " · connected to you" else ""
                            Text(
                                "${person.skillFor(state.intent.activity)} ${state.intent.activity.lowercase()} · ${person.distanceLabel} · ${person.attendance} attendance$connected",
                                style = MaxTimeType.caption
                            )
                        }
                        androidx.compose.foundation.layout.Box(
                            Modifier.clip(RoundedCornerShape(9.dp)).background(MaxTimeColors.accent)
                                .clickable {
                                    state.selectedPersonName = person.name
                                    router.navigate(Screen.PersonDetail)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text("Invite", color = MaxTimeColors.onAccent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        SectionLabel("No luck yet?")
        EmptyNote("Start your own activity and MaxTime will find people to fill it.")
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = MaxTimeColors.muted, fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp))
}
