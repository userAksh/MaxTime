package com.maxtime.app.ui.screens.intent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.maxtime.app.data.Group
import com.maxtime.app.data.Session
import com.maxtime.app.data.StatusChipVariant
import com.maxtime.app.data.sessionDateMatch
import com.maxtime.app.ui.components.AvatarPerson
import com.maxtime.app.ui.components.AvatarStack
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SecondaryButton
import com.maxtime.app.ui.components.SectionLabel
import com.maxtime.app.ui.components.ShimmerCard
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
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var sessionsLoading by remember { mutableStateOf(true) }
    LaunchedEffect(state.intent.activity, state.location) {
        sessionsLoading = true
        sessions = state.sessionsFor()
        groups = state.groupsForActivity(state.intent.activity)
        sessionsLoading = false
    }
    val people = state.eligiblePeopleForIntent()
    val connectedNames = remember(people) { people.filter { it.connected }.map { it.name }.toSet() }
    val shown = sessions.take(2)
    val matches = shown.map { sessionDateMatch(it.date, state.intent.whenLabel) }
    val noneMatchRequest = shown.isNotEmpty() && matches.all { it != null } && matches.none { it!!.matchesRequest }

    ScreenScaffold(
        topBar = { TopBar(onBack = { router.goBack() }) },
        bottomBar = if (!sessionsLoading && noneMatchRequest) null else { {
            BottomCta {
                Row(
                    Modifier.fillMaxWidth(),
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
        } }
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

        if (sessionsLoading) {
            SectionLabel("Join a group")
            ShimmerCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp))
        } else if (sessions.isEmpty()) {
            SectionLabel("Join a group")
            EmptyNote("Nothing nearby yet. Try expanding your radius or choose another time.")
        } else {
            if (noneMatchRequest) {
                Text(
                    "Nothing filling for ${state.intent.whenLabel.lowercase()} yet — you can still",
                    color = MaxTimeColors.muted2, fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
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
            SectionLabel("Join a group")
            shown.forEachIndexed { index, session ->
                val dateMatch = matches[index]
                val mismatched = dateMatch != null && !dateMatch.matchesRequest
                val realGroup = groups.firstOrNull { it.sessionId == session.id }
                val connectedGoing = realGroup?.connections?.firstOrNull { it in connectedNames }
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    backgroundColor = if (mismatched) MaxTimeColors.bg2 else MaxTimeColors.cardBg,
                    borderColor = if (mismatched) MaxTimeColors.bg3 else MaxTimeColors.cardBorder,
                    onClick = {
                        if (realGroup != null) {
                            state.selectedGroup = realGroup
                            router.navigate(Screen.GroupDetail)
                        } else {
                            scope.launch {
                                val g = state.joinExistingGroup()
                                if (g != null) router.navigate(Screen.GroupDetail)
                            }
                        }
                    }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            val going = if (realGroup != null) {
                                "${realGroup.members} going · ${realGroup.openSlots} spot${if (realGroup.openSlots == 1) "" else "s"} open"
                            } else {
                                "No one's joined this time slot yet"
                            }
                            Text(going, color = if (mismatched) MaxTimeColors.muted2 else MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            val dayLabel = dateMatch?.relativeLabel ?: session.date
                            Text("${session.venueName} · $dayLabel, ${session.startTime} · ${state.location.kmLabel(session.distanceKm)}", style = MaxTimeType.caption)
                        }
                        if (mismatched) {
                            StatusChip("Different day", StatusChipVariant.FULL)
                        } else if (realGroup != null) {
                            StatusChip(realGroup.statusLabel, realGroup.statusVariant)
                        }
                    }
                    if (connectedGoing != null && !mismatched) {
                        Text("◆ $connectedGoing, who you know, is going", color = MaxTimeColors.accent2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (realGroup != null) {
                            AvatarStack(
                                realGroup.memberList.map { AvatarPerson(it.personName, it.photoUrl) }
                                    .ifEmpty { listOf(AvatarPerson(realGroup.creator)) }
                            )
                        } else {
                            Text("Be the first to join", style = MaxTimeType.caption)
                        }
                        val joinLabel = if (realGroup != null) "Join group" else "Start it"
                        if (mismatched) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.clip(RoundedCornerShape(9.dp)).background(MaxTimeColors.cardBg).border(1.dp, MaxTimeColors.cardBorder, RoundedCornerShape(9.dp)).padding(horizontal = 14.dp, vertical = 7.dp)
                            ) { Text(joinLabel, color = MaxTimeColors.muted2, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        } else {
                            androidx.compose.foundation.layout.Box(
                                Modifier.clip(RoundedCornerShape(9.dp)).background(MaxTimeColors.accent).padding(horizontal = 14.dp, vertical = 7.dp)
                            ) { Text(joinLabel, color = MaxTimeColors.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
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
                        AvatarStack(listOf(AvatarPerson(person.name)), size = 52.dp)
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
