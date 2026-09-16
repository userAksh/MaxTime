package com.maxtime.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.maxtime.app.data.AppPrefsKeys
import com.maxtime.app.data.AppState
import com.maxtime.app.data.LocalSettings
import com.maxtime.app.data.firebase.UserProfileRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.nav.onboardingOrder
import com.maxtime.app.ui.screens.RootScreenHost
import com.maxtime.app.ui.screens.onboarding.AgeGateScreen
import com.maxtime.app.ui.screens.onboarding.AuthScreen
import com.maxtime.app.ui.screens.onboarding.InterestsScreen
import com.maxtime.app.ui.screens.onboarding.OnboardingDeck
import com.maxtime.app.ui.screens.onboarding.ProfileScreen
import com.maxtime.app.ui.screens.onboarding.SplashScreen
import com.maxtime.app.ui.theme.MaxTimeTheme

@Composable
fun MaxTimeApp() {
    val state = remember { AppState() }

    // iOS Keychain — unlike NSUserDefaults/SharedPreferences — survives a full app delete, so
    // Firebase Auth's session can still be sitting there on what the user reasonably expects to
    // be a fresh install. hasLaunchedBefore lives in storage an uninstall actually clears, so its
    // absence really does mean "this install has never launched before" — if a Firebase session
    // exists anyway, it's stale and must be signed out, or deleting the app to log out would
    // silently log the person back in on reinstall instead.
    val hasStaleSession = remember {
        !LocalSettings.getBoolean(AppPrefsKeys.HAS_LAUNCHED_BEFORE, false) && Firebase.auth.currentUser != null
    }

    // Being signed in (currentUser != null) is NOT the same as having finished onboarding — Auth
    // is well before Profile/Interests in the flow, and ONBOARDING_COMPLETE is only ever set once
    // InterestsScreen's final step actually runs. Someone signed in but not yet complete resumes
    // at Profile (the first screen after Auth) rather than being sent back through AgeGate/Auth,
    // which they've already done, or bounced straight to Home past steps they never filled in.
    val onboardingComplete = remember {
        Firebase.auth.currentUser?.uid?.let { LocalSettings.getBoolean(AppPrefsKeys.onboardingCompleteKey(it), false) } ?: false
    }

    // A prior session (Firebase Auth caches it natively) means this person already signed in —
    // `currentUser` reads Firebase's own restored-on-launch session synchronously, no extra async
    // gating/loading state needed here. A stale session (above) is never treated as "returning"
    // even though currentUser is momentarily non-null.
    val router = remember {
        Router(
            when {
                Firebase.auth.currentUser == null || hasStaleSession -> Screen.Splash
                !onboardingComplete -> Screen.Profile
                else -> Screen.Home
            }
        )
    }

    // A returning user whose Firebase session was restored skips straight to Home (above) —
    // but their name/interests, saved to Firestore during onboarding, still need to be pulled
    // back into this fresh AppState so Home doesn't show the hardcoded default name forever.
    LaunchedEffect(Unit) {
        LocalSettings.putBoolean(AppPrefsKeys.HAS_LAUNCHED_BEFORE, true)
        if (hasStaleSession) {
            Firebase.auth.signOut()
            return@LaunchedEffect
        }
        val uid = Firebase.auth.currentUser?.uid
        if (uid != null) {
            val profile = UserProfileRepository().loadProfile(uid)
            if (profile != null) {
                if (profile.name.isNotBlank()) {
                    state.currentUser = state.currentUser.copy(name = profile.name)
                }
                if (profile.interests.isNotEmpty()) {
                    state.interests = profile.interests.mapValues { it.value.toSet() }
                }
            }
            // Restores whatever groups this name created/joined last session — after the real
            // name (if any) is in place, so it matches what those groups were actually saved
            // under. Only for an already-authenticated returning user: Firestore's security
            // rules require request.auth != null, and a brand-new user on Splash has no session
            // yet, so this would otherwise fire an unauthenticated query and crash.
            state.loadPersistedGroups()
        }
    }

    MaxTimeTheme {
        if (router.current in onboardingOrder) {
            Box(
                Modifier.fillMaxSize()
                    .background(com.maxtime.app.ui.theme.MaxTimeColors.bg1)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                OnboardingDeck(router) { screen ->
                    when (screen) {
                        Screen.Splash -> SplashScreen(onGetStarted = { router.navigate(Screen.AgeGate) })
                        Screen.AgeGate -> AgeGateScreen(onContinue = { router.navigate(Screen.Auth) })
                        Screen.Auth -> AuthScreen(
                            onGoogleSkip = { router.navigate(Screen.Profile) },
                            onVerified = { router.navigate(Screen.Profile) }
                        )
                        Screen.Profile -> ProfileScreen(state, onContinue = { router.navigate(Screen.Interests) })
                        Screen.Interests -> InterestsScreen(state, onFinish = { router.navigate(Screen.Home) })
                        else -> Unit
                    }
                }
            }
        } else {
            // No statusBarsPadding/navigationBarsPadding here — each screen draws its own
            // background full-bleed behind the status and navigation bars (see ScreenScaffold,
            // HomeScreen, BottomCta) so those areas always match that screen's own background
            // instead of leaving a shell-colored gap, with content itself inset from the bars.
            Box(Modifier.fillMaxSize()) {
                RootScreenHost(state, router)
            }
        }
    }
}
