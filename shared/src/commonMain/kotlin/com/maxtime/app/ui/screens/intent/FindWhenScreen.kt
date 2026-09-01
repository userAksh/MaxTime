package com.maxtime.app.ui.screens.intent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FindWhenScreen(state: AppState, router: Router) {
    ScreenScaffold(topBar = { TopBar(onBack = { router.goBack() }) }) {
        Text(
            state.intent.activity,
            color = MaxTimeColors.muted2,
            fontSize = 11.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
        )
        Text("When?", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

        FlowRow(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            listOf("Today, evening" to "Today", "Tonight" to "Tonight", "Tomorrow" to "Tomorrow", "This weekend" to "This weekend").forEach { (value, label) ->
                Box(
                    Modifier
                        .clip(MaxTimeShapes.medium)
                        .background(MaxTimeColors.cardBg)
                        .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                        .clickable {
                            state.intent = state.intent.copy(whenLabel = value)
                            router.navigate(Screen.FindWhere)
                        }
                        .padding(horizontal = 15.dp, vertical = 11.dp)
                ) { Text(label, color = MaxTimeColors.text, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaxTimeShapes.medium)
                    .background(MaxTimeColors.cardBg)
                    .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                    .clickable { router.navigate(Screen.FindWhenCustom) }
                    .padding(horizontal = 15.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) { Text("Choose a specific date & time", color = MaxTimeColors.muted2, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
        }
    }
}
