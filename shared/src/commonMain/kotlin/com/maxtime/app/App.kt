package com.maxtime.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.maxtime.app.analytics.AnalyticsEvent
import com.maxtime.app.analytics.AnalyticsTracker
import com.maxtime.app.analytics.CrashReporter
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
import com.maxtime.app.ui.screens.onboarding.ReferralScreen
import com.maxtime.app.ui.screens.onboarding.SplashScreen
import com.maxtime.app.ui.theme.MaxTimeTheme
import kotlinx.coroutines.launch

/**
 * Where a just-completed sign-in (OTP verified, or "Continue as guest") should land — used by
 * AuthScreen's callbacks below, NOT by MaxTimeApp's own cold-launch resume logic above (that one
 * already has its own synchronous-then-corrected version of this same check). This is what makes
 * an uninstall+reinstall correctly land back on Home for an account that already finished
 * onboarding: iOS Keychain survives an uninstall, but if App.kt's cold-launch check ever forces a
 * sign-out (a stale session with no matching "has launched before" flag) the person re-verifies
 * their OTP within THIS same running session — that always used to hardcode Screen.Referral next,
 * with nothing checking whether this account had already onboarded before.
 */
private suspend fun resolvePostAuthDestination(): Screen {
    val uid = Firebase.auth.currentUser?.uid ?: return Screen.Referral
    if (LocalSettings.getBoolean(AppPrefsKeys.onboardingCompleteKey(uid), false)) return Screen.Home
    val profile = UserProfileRepository().loadProfile(uid)
    if (profile?.onboardingComplete == true) {
        LocalSettings.putBoolean(AppPrefsKeys.onboardingCompleteKey(uid), true)
        return Screen.Home
    }
    return Screen.Referral
}

@Composable
fun MaxTimeApp() {
    // Coil 3's KMP setup requires exactly one singleton ImageLoader registered before any
    // AsyncImage is used anywhere in the app — this is that one registration point. The Ktor
    // fetcher is what lets a plain https:// photoUrl (Firebase Storage download URLs) actually
    // load, rather than only local/bundled images.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }

    val state = remember { AppState() }
    val scope = rememberCoroutineScope()

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
    // is well before Referral/Profile/Interests in the flow, and ONBOARDING_COMPLETE is only ever
    // set once InterestsScreen's final step actually runs. Someone signed in but not yet complete
    // resumes at Referral (the first screen after Auth) rather than being sent back through
    // AgeGate/Auth, which they've already done, or bounced straight to Home past steps they never
    // filled in — this also covers someone who closed the app WHILE on the Referral step itself,
    // before tapping Continue or Skip, who would otherwise silently lose the chance to enter a
    // code. (Like the pre-existing Profile-vs-Interests case, this doesn't track exactly how far
    // someone got beyond this one fixed resume point — redeemReferralCode/getMyReferralSummary
    // are both safe to revisit if they'd actually gotten further, per ReferralScreen's own
    // "returning to the flow" handling.)
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
                !onboardingComplete -> Screen.Referral
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
            // Reset BEFORE signing out, and before AppOpened fires below — otherwise AppOpened
            // (and anything else in this cold start) would still carry whatever distinct_id was
            // persisted from the now-invalid session, misattributing it to the previous account.
            Firebase.auth.signOut()
            AnalyticsTracker.reset()
            CrashReporter.reset()
            AnalyticsTracker.track(AnalyticsEvent.AppOpened(entryPoint = "cold_start"))
            return@LaunchedEffect
        }
        AnalyticsTracker.track(AnalyticsEvent.AppOpened(entryPoint = "cold_start"))
        val uid = Firebase.auth.currentUser?.uid
        if (uid != null) {
            val profileRepository = UserProfileRepository()
            val profile = profileRepository.loadProfile(uid)
            if (profile != null) {
                if (profile.name.isNotBlank()) {
                    state.currentUser = state.currentUser.copy(name = profile.name)
                }
                if (profile.photoUrl != null) {
                    state.currentUser = state.currentUser.copy(photoUrl = profile.photoUrl)
                }
                if (profile.interests.isNotEmpty()) {
                    state.interests = profile.interests.mapValues { it.value.toSet() }
                }
                // The synchronous `onboardingComplete` above only reflects THIS device's local
                // flag, so it can't tell a genuinely new signup apart from an existing account
                // signing in on a new device/reinstall — both start with that flag false. This
                // async correction is what actually distinguishes them: if the account's own
                // Firestore profile says onboarding already finished (on any device), send them
                // straight to Home instead of leaving them on whatever onboarding screen the
                // router had to guess at before this profile load resolved.
                if (profile.onboardingComplete && !onboardingComplete) {
                    LocalSettings.putBoolean(AppPrefsKeys.onboardingCompleteKey(uid), true)
                    router.reset(Screen.Home)
                } else if (onboardingComplete && !profile.onboardingComplete) {
                    // The reverse gap: this device's local flag already says they finished
                    // onboarding, but their account predates this field (or a prior write of it
                    // failed) — backfill it now so the NEXT device/reinstall for this account
                    // reads the correct signal instead of repeating this same guesswork.
                    profileRepository.markOnboardingComplete(uid)
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
                OnboardingDeck(router) { screen, isActive ->
                    when (screen) {
                        Screen.Splash -> SplashScreen(onGetStarted = { router.navigate(Screen.AgeGate) })
                        Screen.AgeGate -> AgeGateScreen(isActive = isActive, onContinue = { router.navigate(Screen.Auth) })
                        Screen.Auth -> AuthScreen(
                            isActive = isActive,
                            onGoogleSkip = { scope.launch { router.navigate(resolvePostAuthDestination()) } },
                            onVerified = { scope.launch { router.navigate(resolvePostAuthDestination()) } }
                        )
                        Screen.Referral -> ReferralScreen(isActive = isActive, onContinue = { router.navigate(Screen.Profile) })
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
