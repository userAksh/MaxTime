package com.maxtime.app.ui.screens.intent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeType

private val DAYS = listOf("Mon 2", "Tue 3", "Wed 4", "Thu 5", "Fri 6", "Sat 7")
private val TIMES = listOf("7:00 AM", "9:00 AM", "11:00 AM", "4:00 PM", "6:00 PM", "8:00 PM")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FindWhenCustomScreen(state: AppState, router: Router) {
    var selectedDay by remember { mutableStateOf(DAYS[1]) }
    var selectedTime by remember { mutableStateOf(TIMES[1]) }

    ScreenScaffold(
        topBar = { TopBar(onBack = { router.goBack() }) },
        bottomBar = {
            BottomCta {
                PrimaryButton("Continue to Where", onClick = {
                    state.intent = state.intent.copy(whenLabel = "$selectedDay, $selectedTime")
                    router.navigate(Screen.FindWhere)
                })
            }
        }
    ) {
        Text(
            state.intent.activity, color = MaxTimeColors.muted2, fontSize = 11.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        Text("Pick a date & time", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

        Text("DATE", color = MaxTimeColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp))
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            DAYS.forEach { day -> PickChip(day, day == selectedDay) { selectedDay = day } }
        }

        Text("TIME", color = MaxTimeColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp))
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            TIMES.forEach { time -> PickChip(time, time == selectedTime) { selectedTime = time } }
        }

        MaxTimeCard(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("$selectedDay, $selectedTime", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text("Selected date & time", style = MaxTimeType.caption, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(MaxTimeShapes.medium)
            .background(if (selected) MaxTimeColors.accent else MaxTimeColors.cardBg)
            .border(1.dp, if (selected) androidx.compose.ui.graphics.Color.Transparent else MaxTimeColors.cardBorder, MaxTimeShapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp)
    ) {
        Text(label, color = if (selected) MaxTimeColors.onAccent else MaxTimeColors.text, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
    }
}
