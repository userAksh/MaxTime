package com.maxtime.app.ui.screens.intent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.maxtime.app.ui.components.AvatarStack
import com.maxtime.app.ui.components.BottomNav
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.SectionLabel
import com.maxtime.app.ui.components.SelectableChip
import com.maxtime.app.ui.components.ShimmerCard
import com.maxtime.app.ui.components.StatusChip
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.screens.LocationPopup
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(state: AppState, router: Router) {
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(MaxTimeColors.screenGradient).statusBarsPadding()) {
          Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            HomeTopBar()
            Text(
                "◆ ${state.location.area} · within ${state.location.radiusLabel}  Change",
                style = MaxTimeType.caption,
                modifier = Modifier
                    .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp)
                    .clickable { state.locationConfirmed = false }
            )
            Box(
                Modifier
                    .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)
                    .clip(MaxTimeShapes.pill)
                    .background(MaxTimeColors.accentWash)
                    .border(1.dp, MaxTimeColors.accentDim, MaxTimeShapes.pill)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "⚡ ${state.freeInvitesLeft} free invite${if (state.freeInvitesLeft == 1) "" else "s"} left",
                    color = MaxTimeColors.accent2,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "What do you want to do?",
                style = MaxTimeType.heading.copy(fontSize = 21.sp),
                modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp)
            )
            Box(
                Modifier
                    .padding(horizontal = MaxTimeSpacing.screenPadding)
                    .fillMaxWidth()
                    .clip(MaxTimeShapes.medium)
                    .background(MaxTimeColors.accent)
                    .clickable { router.navigate(Screen.FindWhat) }
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Find something to do →", color = MaxTimeColors.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Already know what you want? ", style = MaxTimeType.caption)
                Text(
                    "Book solo",
                    color = MaxTimeColors.accent2, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        scope.launch {
                            state.selectSession()
                            router.navigate(Screen.SessionDetail)
                        }
                    }
                )
                Text(" · ", style = MaxTimeType.caption)
                Text(
                    "Start an activity",
                    color = MaxTimeColors.accent2, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { router.navigate(Screen.CreateGroup) }
                )
            }

            SectionLabel("Popular near you")
            val popular = listOf("Badminton", "Swimming", "Pottery", "Pilates", "Football")
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MaxTimeSpacing.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                popular.forEach { activity ->
                    SelectableChip(activity, selected = activity == "Badminton") {
                        state.intent = state.intent.copy(activity = activity)
                        router.navigate(Screen.FindWhen)
                    }
                }
            }

            var recommended by remember { mutableStateOf<Pair<String, Session>?>(null) }
            var nearby by remember { mutableStateOf<Pair<String, Session>?>(null) }
            var sessionsLoading by remember { mutableStateOf(true) }
            LaunchedEffect(state.intent.activity, state.location) {
                sessionsLoading = true
                recommended = state.recommendedGroup()
                nearby = state.nearbySession(recommended?.first)
                sessionsLoading = false
            }

            SectionLabel("People & groups doing this", actionText = "See all", onAction = { router.navigate(Screen.FindWhat) })
            val recommendedValue = recommended
            if (sessionsLoading) {
                ShimmerCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp))
            } else if (recommendedValue != null) {
                val (activity, session) = recommendedValue
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    onClick = {
                        state.intent = state.intent.copy(activity = activity)
                        router.navigate(Screen.FindResults)
                    }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            Text("3 going · 1 spot open", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "$activity · ${session.date}, ${session.startTime} · ${session.venueName}, ${session.distanceKm} km",
                                style = MaxTimeType.caption
                            )
                        }
                        StatusChip("Filling", com.maxtime.app.data.StatusChipVariant.FILLING)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        AvatarStack(listOf("A", "B", "C"))
                        Box(Modifier.clip(RoundedCornerShape(9.dp)).background(MaxTimeColors.accent).padding(horizontal = 14.dp, vertical = 7.dp)) {
                            Text("See details", color = MaxTimeColors.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Text(
                    "Nothing nearby yet — try expanding your radius.",
                    style = MaxTimeType.caption,
                    modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding)
                )
            }

            Text(
                "SESSIONS NEAR YOU",
                color = MaxTimeColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp)
            )
            val nearbyValue = nearby
            if (sessionsLoading) {
                ShimmerCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp), height = 64.dp)
            } else if (nearbyValue != null) {
                val (activity, session) = nearbyValue
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    onClick = {
                        state.intent = state.intent.copy(activity = activity)
                        router.navigate(Screen.FindResults)
                    }
                ) {
                    Text("$activity · ${session.date}, ${session.startTime}", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "${session.venueName}, ${session.distanceKm} km away · ${session.spotsLeft} spots left",
                        style = MaxTimeType.caption,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            SectionLabel("Coming up")
            val recentGroup = state.selectedGroup
            if (recentGroup != null) {
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    onClick = { router.navigate(Screen.GroupDetail) }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${recentGroup.activity} Group", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text("${recentGroup.whenLabel} · ${recentGroup.venue ?: "Session pending"}", style = MaxTimeType.caption)
                        }
                        Text("›", color = MaxTimeColors.textStrong, fontSize = 18.sp)
                    }
                }
            } else {
                Text(
                    "No upcoming activities yet — find something below.",
                    style = MaxTimeType.body,
                    modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding)
                )
            }
          }

            BottomNav(router, active = Screen.Home)
        }

        if (!state.locationConfirmed) {
            LocationPopup(state)
        }
    }
}

@Composable
private fun HomeTopBar() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("MaxTime", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        Box(Modifier.size(32.dp).clip(CircleShape).background(MaxTimeColors.cardBg).border(1.dp, MaxTimeColors.cardBorder, CircleShape))
    }
}

