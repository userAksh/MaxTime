package com.maxtime.app.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun CreateGroupScreen(state: AppState, router: Router) {
    var needed by remember { mutableStateOf(state.intent.peopleNeeded) }

    ScreenScaffold(
        topBar = { TopBar(title = "Start an Activity", onBack = { router.goBack() }) },
        bottomBar = {
            BottomCta {
                PrimaryButton("Create activity", onClick = {
                    state.intent = state.intent.copy(peopleNeeded = needed)
                    state.selectedGroup = state.getOrCreateMyGroup()
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
                        .size(42.dp)
                        .clip(MaxTimeShapes.medium)
                        .background(if (on) MaxTimeColors.accent else MaxTimeColors.cardBg)
                        .border(1.dp, if (on) Color.Transparent else MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                        .clickable { needed = n },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (n == 4) "Need 4+" else "Need $n",
                        color = if (on) MaxTimeColors.onAccent else MaxTimeColors.muted2,
                        fontWeight = FontWeight.Bold, fontSize = 11.sp
                    )
                }
            }
        }

        MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)) {
            Text(state.intent.activity, color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(state.intent.whenLabel, style = MaxTimeType.caption, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Finding a suitable session near ${state.location.area}…",
                color = MaxTimeColors.muted, fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
