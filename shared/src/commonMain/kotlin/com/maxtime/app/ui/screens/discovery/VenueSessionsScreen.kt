package com.maxtime.app.ui.screens.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.Session
import com.maxtime.app.data.formatSessionDate
import com.maxtime.app.data.formatSessionTime
import com.maxtime.app.data.sessionChronoKey
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.ShimmerCard
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

/**
 * Venue-scoped date/time picker — the destination of a venue card tapped from
 * [ActivityDetailScreen]'s venue-grouped list. Follows the same DATE/TIME chip idiom as
 * FindWhenCustomScreen, but built from a real venue's actual bookable Sessions instead of a
 * fixed list of options.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VenueSessionsScreen(state: AppState, router: Router) {
    val venueName = state.selectedVenueName
    val info = state.activityInfo()
    var sessions by remember { mutableStateOf<List<Session>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Session?>(null) }
    // A venue can offer more than one duration for the same activity — e.g. separate 30-min and
    // 60-min services the partner set up during onboarding, each with its own AvailabilityRule —
    // so this is derived from what's actually in the venue's real Sessions, never a fixed list.
    var selectedDuration by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.intent.activity, state.location, venueName) {
        loading = true
        sessions = state.sessionsFor()
            .filter { it.venueName == venueName }
            .sortedBy(::sessionChronoKey)
        selectedDuration = sessions.map { it.durationMinutes }.distinct().sorted().firstOrNull()
        selected = null
        loading = false
    }

    val durations = remember(sessions) { sessions.map { it.durationMinutes }.distinct().sorted() }
    val visibleSessions = remember(sessions, selectedDuration) {
        if (durations.size > 1) sessions.filter { it.durationMinutes == selectedDuration } else sessions
    }
    // groupBy preserves first-seen key order, which matches chronological order here since
    // `visibleSessions` is already sorted by sessionChronoKey above.
    val dateGroups = remember(visibleSessions) { visibleSessions.groupBy { formatSessionDate(it.date) } }

    ScreenScaffold(
        topBar = { TopBar(title = venueName ?: "Venue", onBack = { router.goBack() }) },
        bottomBar = {
            BottomCta {
                PrimaryButton(
                    "Continue", enabled = selected != null,
                    onClick = {
                        selected?.let {
                            state.selectExactSession(it)
                            router.navigate(Screen.SessionDetail)
                        }
                    }
                )
            }
        }
    ) {
        Box(
            Modifier
                .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 14.dp)
                .fillMaxWidth()
                .height(120.dp)
                .clip(MaxTimeShapes.large)
                .background(MaxTimeColors.bg2),
            contentAlignment = Alignment.Center
        ) { Text(info.icon, fontSize = 34.sp) }

        Text(
            venueName ?: "Venue", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp,
            modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding)
        )
        Text(
            "${state.intent.activity} · ${sessions.firstOrNull()?.let { state.location.kmAwayLabel(it.distanceKm) } ?: "distance unknown"}",
            style = MaxTimeType.caption,
            modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 2.dp)
        )

        Text(
            "Pick a date & time", style = MaxTimeType.heading,
            modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)
        )

        if (durations.size > 1) {
            Text(
                "DURATION", color = MaxTimeColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = MaxTimeSpacing.screenPadding, end = MaxTimeSpacing.screenPadding, top = 4.dp, bottom = 4.dp)
            )
            FlowRow(
                Modifier.padding(horizontal = MaxTimeSpacing.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                durations.forEach { duration ->
                    TimeChip(
                        label = "$duration min",
                        selected = duration == selectedDuration,
                        onClick = {
                            selectedDuration = duration
                            selected = null
                        }
                    )
                }
            }
        }

        if (loading) {
            ShimmerCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp))
        } else if (dateGroups.isEmpty()) {
            Text(
                "No upcoming time slots at this venue right now.",
                color = MaxTimeColors.muted, fontSize = 10.5.sp,
                modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding)
            )
        } else {
            dateGroups.forEach { (dateLabel, daySessions) ->
                Text(
                    dateLabel.uppercase(), color = MaxTimeColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = MaxTimeSpacing.screenPadding, end = MaxTimeSpacing.screenPadding, top = 12.dp, bottom = 4.dp)
                )
                FlowRow(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    daySessions.forEach { session ->
                        TimeChip(
                            label = formatSessionTime(session.startTime),
                            selected = selected?.id == session.id,
                            onClick = { selected = session }
                        )
                    }
                }
            }

            selected?.let { s ->
                MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 16.dp)) {
                    Text(
                        "${formatSessionDate(s.date)}, ${formatSessionTime(s.startTime)}",
                        color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp
                    )
                    Text(
                        "${s.durationMinutes} min · ${if (s.price > 0) "₹${s.price}" else "Free"} · ${s.spotsLeft} spots left",
                        style = MaxTimeType.caption, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(MaxTimeShapes.medium)
            .background(if (selected) MaxTimeColors.accent else MaxTimeColors.cardBg)
            .border(1.dp, if (selected) Color.Transparent else MaxTimeColors.cardBorder, MaxTimeShapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp)
    ) {
        Text(label, color = if (selected) MaxTimeColors.onAccent else MaxTimeColors.text, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
    }
}
