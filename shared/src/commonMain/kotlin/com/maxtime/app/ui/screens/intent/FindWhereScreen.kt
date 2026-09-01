package com.maxtime.app.ui.screens.intent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.UserLocation
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeType

@Composable
fun FindWhereScreen(state: AppState, router: Router) {
    var manualArea by remember { mutableStateOf(state.location.area.substringBefore(',')) }
    var radiusKm by remember { mutableStateOf(state.location.radiusKm) }

    ScreenScaffold(
        topBar = { TopBar(onBack = { router.goBack() }) },
        bottomBar = {
            BottomCta {
                PrimaryButton("Find", onClick = {
                    state.location = UserLocation(area = manualArea.ifBlank { state.location.area }, radiusKm = radiusKm)
                    router.navigate(Screen.FindResults)
                })
            }
        }
    ) {
        Text(
            "${state.intent.activity} · ${state.intent.whenLabel}",
            color = MaxTimeColors.muted2, fontSize = 11.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        Text("Where?", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

        Box(
            Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(MaxTimeShapes.medium)
                .background(MaxTimeColors.accent)
                .clickable {
                    state.location = UserLocation(area = "Hauz Khas, South Delhi", radiusKm = radiusKm)
                    router.navigate(Screen.FindResults)
                }
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) { Text("◆ Use current location", color = MaxTimeColors.onAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp) }

        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text("or enter manually", color = MaxTimeColors.muted, fontSize = 11.sp)
        }

        Box(
            Modifier
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(MaxTimeShapes.medium)
                .background(MaxTimeColors.cardBg)
                .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            BasicTextField(
                value = manualArea,
                onValueChange = { manualArea = it },
                textStyle = TextStyle(color = MaxTimeColors.textStrong, fontSize = 13.5.sp),
                cursorBrush = SolidColor(MaxTimeColors.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 5, 10).forEach { km ->
                val on = km == radiusKm
                Box(
                    Modifier
                        .clip(MaxTimeShapes.pill)
                        .background(if (on) MaxTimeColors.accent else MaxTimeColors.bg2)
                        .border(1.dp, if (on) Color.Transparent else MaxTimeColors.chipBorder, MaxTimeShapes.pill)
                        .clickable { radiusKm = km }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("$km km", color = if (on) MaxTimeColors.onAccent else MaxTimeColors.muted2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        Box(
            Modifier
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(MaxTimeShapes.medium)
                .background(MaxTimeColors.cardBg)
                .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) { Text("🗺 Select on map instead", color = MaxTimeColors.muted2, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
    }
}
