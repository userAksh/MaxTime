package com.maxtime.app.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.maxtime.app.ui.components.BottomNav
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SectionLabel
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

@Composable
fun MyProfileScreen(state: AppState, router: Router) {
    ScreenScaffold(bottomBar = { BottomNav(router, active = Screen.MyProfile) }) {
        Text("Profile", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp))

        val user = state.currentUser
        Box(
            Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp)
                .size(72.dp)
                .clip(CircleShape)
                .background(MaxTimeColors.accentWash),
            contentAlignment = Alignment.Center
        ) {
            Text(
                user.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase(),
                color = MaxTimeColors.accent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp
            )
        }
        Text(user.name, color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding))
        val meta = listOfNotNull(user.phoneOrEmail, user.dateOfBirth?.let { "Born $it" }).joinToString("  ·  ")
        if (meta.isNotEmpty()) {
            Text(meta, style = MaxTimeType.caption, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp))
        }

        SectionLabel("Location")
        MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp)) {
            Text("◆ ${state.location.area}", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text("Within ${state.location.radiusLabel}", style = MaxTimeType.caption, modifier = Modifier.padding(top = 2.dp))
        }

        SectionLabel("Invites")
        MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp)) {
            Text(
                "⚡ ${state.freeInvitesLeft} free invite${if (state.freeInvitesLeft == 1) "" else "s"} left",
                color = MaxTimeColors.accent2, fontWeight = FontWeight.Bold, fontSize = 13.5.sp
            )
        }
    }
}
