package com.maxtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import maxtime.shared.generated.resources.Res
import maxtime.shared.generated.resources.nav_discover_active
import maxtime.shared.generated.resources.nav_discover_solid
import maxtime.shared.generated.resources.nav_groups_active
import maxtime.shared.generated.resources.nav_groups_solid
import maxtime.shared.generated.resources.nav_home_active
import maxtime.shared.generated.resources.nav_home_solid
import maxtime.shared.generated.resources.nav_plans_active
import maxtime.shared.generated.resources.nav_plans_solid
import maxtime.shared.generated.resources.nav_profile_active
import maxtime.shared.generated.resources.nav_profile_solid
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private data class NavTab(
    val label: String,
    val screen: Screen,
    val iconSolid: DrawableResource,
    val iconActive: DrawableResource
)

private val navTabs = listOf(
    NavTab("Home", Screen.Home, Res.drawable.nav_home_solid, Res.drawable.nav_home_active),
    NavTab("Discover", Screen.Discover, Res.drawable.nav_discover_solid, Res.drawable.nav_discover_active),
    NavTab("Groups", Screen.Groups, Res.drawable.nav_groups_solid, Res.drawable.nav_groups_active),
    NavTab("Plans", Screen.Plans, Res.drawable.nav_plans_solid, Res.drawable.nav_plans_active),
    NavTab("Profile", Screen.MyProfile, Res.drawable.nav_profile_solid, Res.drawable.nav_profile_active)
)

/** Persistent tab bar shown on every [com.maxtime.app.ui.nav.bottomNavScreens] destination —
 *  background reaches the true bottom edge (navigationBarsPadding insets only its content, not
 *  itself) so it never looks like it's floating above a gap. */
@Composable
fun BottomNav(router: Router, active: Screen) {
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp)
            .background(MaxTimeColors.bg1)
            .navigationBarsPadding()
            .padding(vertical = 12.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        navTabs.forEach { tab ->
            val isOn = tab.screen == active
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .clip(MaxTimeShapes.small)
                    .clickable { if (router.current != tab.screen) router.navigate(tab.screen) }
                    .padding(vertical = 6.dp, horizontal = 10.dp)
            ) {
                if (isOn) {
                    Icon(
                        painter = painterResource(tab.iconActive),
                        contentDescription = tab.label,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(tab.iconSolid),
                        contentDescription = tab.label,
                        tint = MaxTimeColors.muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(tab.label, color = if (isOn) MaxTimeColors.accent else MaxTimeColors.muted, fontSize = 11.sp, fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
