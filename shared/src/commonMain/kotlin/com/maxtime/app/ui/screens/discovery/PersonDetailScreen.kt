package com.maxtime.app.ui.screens.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.getPerson
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonDetailScreen(state: AppState, router: Router) {
    val person = getPerson(state.selectedPersonName ?: "Harsh")
    val otherActivities = person.skills.keys.filter { it != state.intent.activity }

    ScreenScaffold(
        topBar = { TopBar(title = "Activity Profile", onBack = { router.goBack() }) },
        bottomBar = {
            BottomCta {
                PrimaryButton("Invite to activity", onClick = { router.navigate(Screen.MatchRequest) })
                Text(
                    "Not this time",
                    color = MaxTimeColors.muted, fontSize = 11.5.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { router.goBack() }
                )
            }
        }
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(MaxTimeColors.accent))
        }
        Text(
            person.name, color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Text(
            "${person.skillFor(state.intent.activity)} · ${state.intent.activity}",
            style = MaxTimeType.caption,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
        )
        if (person.connected) {
            Text(
                "◆ Connected to you", color = MaxTimeColors.accent2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Activities", person.activitiesCompleted.toString(), Modifier.weight(1f))
            Metric("Attendance", person.attendance, Modifier.weight(1f))
            Metric("Activity Rating", person.rating.toString(), Modifier.weight(1f))
        }

        Text("ALSO DOES", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp))
        FlowRow(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val tags = if (otherActivities.isNotEmpty()) otherActivities else listOf("${state.intent.activity} only")
            tags.forEach { tag ->
                Box(
                    Modifier
                        .clip(MaxTimeShapes.pill)
                        .background(MaxTimeColors.cardBg)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) { Text(tag, color = MaxTimeColors.muted2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 16.dp)) {
            Text("For this activity", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "${state.intent.activity} · ${state.intent.whenLabel} · ${person.distanceLabel} away",
                style = MaxTimeType.caption,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    MaxTimeCard(modifier) {
        Text(value, color = MaxTimeColors.accent2, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(label.uppercase(), color = MaxTimeColors.muted, fontSize = 9.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
    }
}
