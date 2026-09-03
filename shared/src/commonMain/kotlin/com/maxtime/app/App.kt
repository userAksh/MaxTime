package com.maxtime.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.maxtime.app.data.AppState
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
    // A prior session (Firebase Auth caches it natively) means this person already finished
    // onboarding — land them back on Home instead of walking Splash -> AgeGate -> Auth -> ...
    // again. `currentUser` reads Firebase's own restored-on-launch session synchronously, no
    // extra async gating/loading state needed here.
    val router = remember { Router(if (Firebase.auth.currentUser != null) Screen.Home else Screen.Splash) }

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
                        Screen.Interests -> InterestsScreen(onFinish = { router.navigate(Screen.Home) })
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
