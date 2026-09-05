package com.maxtime.app.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.maxtime.app.data.DURATION_OPTIONS
import com.maxtime.app.data.TIME_SLOTS
import com.maxtime.app.data.TimeSlotOption
import com.maxtime.app.data.dayPhraseOf
import com.maxtime.app.data.parseClockTimeToMinutes
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SectionLabel
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateGroupScreen(state: AppState, router: Router) {
    var needed by remember { mutableStateOf(state.intent.peopleNeeded) }
    val dayLabel = remember(state.intent.whenLabel) { dayPhraseOf(state.intent.whenLabel) }
    var selectedDuration by remember { mutableStateOf(state.intent.durationMinutes) }

    var slots by remember { mutableStateOf<List<TimeSlotOption>>(emptyList()) }
    var slotsLoading by remember { mutableStateOf(true) }
    LaunchedEffect(state.intent.activity, state.location, state.intent.whenLabel, selectedDuration) {
        slotsLoading = true
        slots = state.availableSlotsFor(selectedDuration)
        slotsLoading = false
    }
    // Real bookable (start, end) pairs for the chosen duration when a venue actually has room;
    // a neutral preference list when nothing real fits — never a fake "these are available" claim.
    val realStartTimes = remember(slots) {
        slots.map { it.startTime }.distinct().sortedBy { parseClockTimeToMinutes(it) ?: Int.MAX_VALUE }
    }
    val timeOptions = if (realStartTimes.isNotEmpty()) realStartTimes else TIME_SLOTS
    var selectedTime by remember(timeOptions) {
        val requestedTime = state.intent.whenLabel.substringAfter(",", "").trim()
        mutableStateOf(requestedTime.takeIf { it in timeOptions } ?: timeOptions.first())
    }
    val chosenSlot = slots.firstOrNull { it.startTime == selectedTime }

    ScreenScaffold(
        topBar = { TopBar(title = "Start an Activity", onBack = { router.goBack() }) },
        bottomBar = {
            BottomCta {
                PrimaryButton("Create activity", onClick = {
                    val label = chosenSlot?.let { "$dayLabel, ${it.startTime} – ${it.endTime}" } ?: "$dayLabel, $selectedTime"
                    state.intent = state.intent.copy(whenLabel = label, peopleNeeded = needed, durationMinutes = selectedDuration)
                    val g = state.getOrCreateMyGroup()
                    if (chosenSlot != null) state.confirmVenueSlot(g, chosenSlot)
                    state.selectedGroup = g
                    state.justCreatedActivity = true
                    router.navigate(Screen.GroupDetail)
                })
            }
        }
    ) {
        Text("How many people do you need?", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp))
        Text("${state.intent.activity} · ${state.intent.whenLabel}", style = MaxTimeType.body, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding))

        Row(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 4).forEach { n ->
                val on = if (n == 4) needed >= 4 else needed == n
                Box(
                    Modifier
                        .clip(MaxTimeShapes.medium)
                        .background(if (on) MaxTimeColors.accent else MaxTimeColors.cardBg)
                        .border(1.dp, if (on) Color.Transparent else MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                        .clickable { needed = n }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (n == 4) "Need 4+" else "Need $n",
                        color = if (on) MaxTimeColors.onAccent else MaxTimeColors.muted2,
                        fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }

        SectionLabel("How long do you want to play?")
        Row(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DURATION_OPTIONS.forEach { (minutes, label) ->
                val on = minutes == selectedDuration
                Box(
                    Modifier
                        .clip(MaxTimeShapes.medium)
                        .background(if (on) MaxTimeColors.accent else MaxTimeColors.cardBg)
                        .border(1.dp, if (on) Color.Transparent else MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                        .clickable { selectedDuration = minutes }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Text(
                        label,
                        color = if (on) MaxTimeColors.onAccent else MaxTimeColors.muted2,
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1
                    )
                }
            }
        }

        SectionLabel("What time works for you?")
        FlowRow(
            modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            timeOptions.forEach { time ->
                val on = time == selectedTime
                Box(
                    Modifier
                        .clip(MaxTimeShapes.medium)
                        .background(if (on) MaxTimeColors.accent else MaxTimeColors.cardBg)
                        .border(1.dp, if (on) Color.Transparent else MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                        .clickable { selectedTime = time }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Text(
                        time,
                        color = if (on) MaxTimeColors.onAccent else MaxTimeColors.muted2,
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1
                    )
                }
            }
        }
        Text(
            if (slotsLoading) "Checking real court availability nearby…"
            else if (realStartTimes.isNotEmpty()) "These are real openings that fit your ${DURATION_OPTIONS.first { it.first == selectedDuration }.second} — never a slot that's already booked or too short."
            else "Nothing open for ${dayLabel.lowercase()} at this duration nearby yet — pick a preference and we'll match a real one if it opens up.",
            color = MaxTimeColors.muted, fontSize = 10.5.sp,
            modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)
        )

        MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)) {
            Text(state.intent.activity, color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(
                chosenSlot?.let { "$dayLabel, ${it.startTime} – ${it.endTime}" } ?: "$dayLabel, $selectedTime",
                style = MaxTimeType.caption, modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                if (chosenSlot != null) "${chosenSlot.venueName}, ${state.location.area} — confirmed, waiting on people"
                else "Finding a suitable venue near ${state.location.area}…",
                color = MaxTimeColors.muted, fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        SectionLabel("How it works")
        listOf(
            "We only offer real openings that actually fit the duration you pick — never a slot that's already booked or too short to fit.",
            "Your activity stays open until it fills up or the deadline passes — nothing is charged until then.",
            "If it fills in time, everyone pays the normal price. If not, you can continue at a slightly higher price or book solo instead.",
            "You can invite people directly, or others can find and join it through search."
        ).forEach { point ->
            Row(
                Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 5.dp).fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text("◆", color = MaxTimeColors.accent2, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp, top = 2.dp))
                Text(point, style = MaxTimeType.caption, modifier = Modifier.weight(1f))
            }
        }
    }
}
