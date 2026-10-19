package com.maxtime.app.ui.screens.discovery

import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.maxtime.app.data.formatSessionDate
import com.maxtime.app.data.formatSessionTime
import com.maxtime.app.data.sessionChronoKey
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.Session
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SecondaryButton
import com.maxtime.app.ui.components.SectionLabel
import com.maxtime.app.ui.components.ShimmerCard
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.analytics.AnalyticsEvent
import com.maxtime.app.analytics.AnalyticsTracker
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType
import kotlinx.coroutines.launch

@Composable
fun ActivityDetailScreen(state: AppState, router: Router) {
    val scope = rememberCoroutineScope()
    val info = state.activityInfo()
    var sessions by remember { mutableStateOf<List<Session>>(emptyList()) }
    var sessionsLoading by remember { mutableStateOf(true) }
    LaunchedEffect(state.intent.activity, state.location) {
        sessionsLoading = true
        sessions = state.sessionsFor()
        sessionsLoading = false
        val availabilityStatus = if (sessions.isNotEmpty()) "available" else "none"
        AnalyticsTracker.track(
            AnalyticsEvent.ActivityDetailViewed(
                activityId = state.intent.activity,
                categoryId = info.category,
                priceMinor = info.price * 100,
                currency = "INR",
                availabilityStatus = availabilityStatus,
            )
        )
        if (sessions.isEmpty()) {
            AnalyticsTracker.track(
                AnalyticsEvent.ActivitySearchNoResults(categoryId = info.category, areaId = state.location.area)
            )
        }
    }

    ScreenScaffold(
        topBar = { TopBar(title = state.intent.activity, onBack = { router.goBack() }) },
        bottomBar = {
            BottomCta {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Find People", Modifier.weight(1f), onClick = { router.navigate(Screen.FindResults) })
                    PrimaryButton(
                        "Book Solo", Modifier.weight(1f), enabled = sessions.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                state.selectSession()
                                router.navigate(Screen.SessionDetail)
                            }
                        }
                    )
                }
            }
        }
    ) {
        Box(
            Modifier
                .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 14.dp)
                .fillMaxWidth()
                .height(150.dp)
                .clip(MaxTimeShapes.large)
                .background(MaxTimeColors.bg2),
            contentAlignment = Alignment.Center
        ) { Text(info.icon, fontSize = 40.sp) }

        Text(state.intent.activity, color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding))
        Text("${info.category} · ${state.location.area}", style = MaxTimeType.caption, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 2.dp))
        Text(info.description, style = MaxTimeType.body, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp))

        SectionLabel("Upcoming sessions")
        if (sessionsLoading) {
            ShimmerCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp))
        } else if (sessions.isEmpty()) {
            Text(
                "No sessions found within ${state.location.radiusLabel} of ${state.location.area}. Try expanding your radius or choosing another time.",
                color = MaxTimeColors.muted, fontSize = 10.5.sp,
                modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding)
            )
        } else {
            val venueGroups = remember(sessions) {
                sessions.groupBy { it.venueName }.mapValues { (_, group) -> group.sortedBy(::sessionChronoKey) }
            }
            venueGroups.forEach { (venueName, venueSessions) ->
                val next = venueSessions.first()
                val minPrice = venueSessions.minOf { it.price }
                val maxPrice = venueSessions.maxOf { it.price }
                val priceLabel = when {
                    maxPrice <= 0 -> "Free"
                    minPrice == maxPrice -> "₹$minPrice"
                    else -> "₹$minPrice–₹$maxPrice"
                }
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    onClick = {
                        state.selectedVenueName = venueName
                        router.navigate(Screen.VenueSessions)
                    }
                ) {
                    Text(venueName, color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "$priceLabel · ${state.location.kmAwayLabel(next.distanceKm)} · ${venueSessions.size} time slot${if (venueSessions.size == 1) "" else "s"}",
                        style = MaxTimeType.caption,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        "Next: ${formatSessionDate(next.date)}, ${formatSessionTime(next.startTime)}",
                        style = MaxTimeType.caption,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
