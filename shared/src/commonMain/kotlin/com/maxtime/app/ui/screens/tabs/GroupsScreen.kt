package com.maxtime.app.ui.screens.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.ui.components.AvatarStack
import com.maxtime.app.ui.components.BottomNav
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.StatusChip
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

/** Every group you've started or joined — people/connection-focused (who's in it), as opposed
 *  to Plans which is the same underlying data viewed schedule-first. */
@Composable
fun GroupsScreen(state: AppState, router: Router) {
    ScreenScaffold(bottomBar = { BottomNav(router, active = Screen.Groups) }) {
        Text("Groups", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp))
        Text(
            "Activities you've started or joined.",
            style = MaxTimeType.body,
            modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)
        )

        val groups = state.groups.values.toList()
        if (groups.isEmpty()) {
            Text(
                "You haven't started or joined any groups yet — find something to do and invite people along.",
                color = MaxTimeColors.muted, fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)
            )
        } else {
            groups.forEach { group ->
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    onClick = {
                        state.selectedGroup = group
                        router.navigate(Screen.GroupDetail)
                    }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${group.icon} ${group.activity} Group", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        StatusChip(group.statusLabel, group.statusVariant)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        AvatarStack(if (group.connections.isEmpty()) listOf("You") else listOf("You") + group.connections)
                        Text("${group.members}/${group.capacity} going", style = MaxTimeType.caption)
                    }
                }
            }
        }
    }
}
