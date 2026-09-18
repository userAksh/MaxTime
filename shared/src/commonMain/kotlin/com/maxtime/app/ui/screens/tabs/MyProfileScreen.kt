package com.maxtime.app.ui.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.firebase.AuthRepository
import com.maxtime.app.ui.components.AvatarCircle
import com.maxtime.app.ui.components.BottomNav
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SectionLabel
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType
import kotlinx.coroutines.launch

@Composable
fun MyProfileScreen(state: AppState, router: Router) {
    val scope = rememberCoroutineScope()
    val authRepository = remember { AuthRepository() }

    fun signOutTapped() {
        scope.launch {
            authRepository.signOut()
            state.resetForSignOut()
            // reset, not navigate — the whole authenticated back stack (Home, Discover, etc.) is
            // no longer valid once signed out, so Back must not be able to return into it.
            router.reset(Screen.Splash)
        }
    }

    ScreenScaffold(bottomBar = { BottomNav(router, active = Screen.MyProfile) }) {
        Text("Profile", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp))

        val user = state.currentUser
        AvatarCircle(
            name = user.name,
            photoUrl = user.photoUrl,
            size = 72.dp,
            modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp)
        )
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

        SectionLabel("Account")
        MaxTimeCard(
            Modifier
                .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp)
                .clickable(onClick = ::signOutTapped)
        ) {
            Text("Sign out", color = MaxTimeColors.red, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
    }
}
