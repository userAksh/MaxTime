package com.maxtime.app.ui.screens.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.ACTIVITY_CATEGORIES
import com.maxtime.app.data.AppState
import com.maxtime.app.ui.components.BottomNav
import com.maxtime.app.ui.components.InterestChip
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

/** Open-ended browsing, deliberately distinct from Home/FindWhat's goal-directed search — no
 *  "when"/"where" commitment, tapping a category just opens that activity to look at. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(state: AppState, router: Router) {
    ScreenScaffold(bottomBar = { BottomNav(router, active = Screen.Discover) }) {
        Text("Discover", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp))
        Text(
            "Browse activities — no plans needed yet.",
            style = MaxTimeType.body,
            modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)
        )

        ACTIVITY_CATEGORIES.forEach { (category, items) ->
            Text(
                category.uppercase(),
                color = MaxTimeColors.accent2,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = MaxTimeSpacing.screenPadding, top = 14.dp, bottom = 9.dp)
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items.forEach { item ->
                    InterestChip(item, selected = false) {
                        state.intent = state.intent.copy(activity = item)
                        router.navigate(Screen.ActivityDetail)
                    }
                }
            }
        }
    }
}
