package com.maxtime.app.ui.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.analytics.AnalyticsEvent
import com.maxtime.app.analytics.AnalyticsTracker
import com.maxtime.app.analytics.CrashReporter
import com.maxtime.app.data.AppState
import com.maxtime.app.data.firebase.AccountFunctions
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
    val accountFunctions = remember { AccountFunctions() }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    fun signOutTapped() {
        scope.launch {
            authRepository.signOut()
            AnalyticsTracker.reset()
            CrashReporter.reset()
            state.resetForSignOut()
            // reset, not navigate — the whole authenticated back stack (Home, Discover, etc.) is
            // no longer valid once signed out, so Back must not be able to return into it.
            router.reset(Screen.Splash)
        }
    }

    fun confirmDeleteAccount() {
        AnalyticsTracker.track(AnalyticsEvent.AccountDeletionRequested)
        scope.launch {
            deleting = true
            deleteError = null
            try {
                accountFunctions.deleteMyAccount()
                // The Cloud Function already deleted the Auth user server-side; this just clears
                // the now-invalid session out of the local SDK/app state, same teardown signOut()
                // does.
                authRepository.signOut()
                AnalyticsTracker.reset()
                CrashReporter.reset()
                state.resetForSignOut()
                router.reset(Screen.Splash)
            } catch (e: Exception) {
                deleting = false
                deleteError = "Couldn't delete your account. Please try again."
            }
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

        MaxTimeCard(
            Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
            onClick = { router.navigate(Screen.ReferAndEarn) }
        ) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Column {
                    Text("🎁 Refer & Earn", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Text("You get 20%, they get 10%", style = MaxTimeType.caption, modifier = Modifier.padding(top = 2.dp))
                }
                Text("›", color = MaxTimeColors.muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
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
        MaxTimeCard(
            Modifier
                .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp)
                .clickable(onClick = { showDeleteConfirm = true })
        ) {
            Text("Delete account", color = MaxTimeColors.red, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
        if (deleteError != null) {
            Text(deleteError!!, color = MaxTimeColors.red, style = MaxTimeType.caption, modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "This permanently deletes your MaxTime account and profile. Group chats and " +
                        "bookings you were part of aren't removed for other members. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDeleteAccount() },
                    enabled = !deleting
                ) {
                    Text(if (deleting) "Deleting…" else "Delete account", color = MaxTimeColors.red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !deleting) {
                    Text("Cancel")
                }
            }
        )
    }
}
