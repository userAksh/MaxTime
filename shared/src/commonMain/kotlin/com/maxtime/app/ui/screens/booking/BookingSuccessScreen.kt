package com.maxtime.app.ui.screens.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SecondaryButton
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

@Composable
fun BookingSuccessScreen(state: AppState, router: Router) {
    val group = state.selectedGroup
    val session = state.selectedSession
    val activity = group?.activity ?: session?.activity ?: state.intent.activity
    val venue = group?.venue ?: session?.venueName ?: state.activityInfo().venues.firstOrNull()?.name.orEmpty()
    val whenLabel = group?.whenLabel ?: session?.whenLabel ?: state.intent.whenLabel
    val icon = state.activityInfo(activity).icon
    val code = remember { "MXT-${randomCode()}-${randomCode()}" }

    ScreenScaffold {
        Box(Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaxTimeColors.accentWash).border(2.dp, MaxTimeColors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("✓", color = MaxTimeColors.accent, fontSize = 28.sp) }
        }
        Text(
            "You are in", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Text(
            "Your spot for $activity is confirmed.", style = MaxTimeType.body,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)
        )

        Box(
            Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 16.dp)
                .fillMaxWidth().clip(MaxTimeShapes.large).background(MaxTimeColors.cardBg).border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.large)
        ) {
            androidx.compose.foundation.layout.Column {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(activity, color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("$whenLabel · $venue", style = MaxTimeType.caption, modifier = Modifier.padding(top = 3.dp))
                    }
                    Text(icon, fontSize = 20.sp)
                }
                Box(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(code, color = MaxTimeColors.accent2, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
        }

        Box(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding)) {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (group != null) {
                    SecondaryButton("Open Group", onClick = { router.navigate(Screen.GroupDetail) })
                }
                PrimaryButton("Done", onClick = { router.navigate(Screen.Home) })
            }
        }
    }
}

private fun randomCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (0 until 4).map { chars.random() }.joinToString("")
}
