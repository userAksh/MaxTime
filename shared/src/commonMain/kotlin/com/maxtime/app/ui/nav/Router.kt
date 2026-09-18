package com.maxtime.app.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen {
    Splash, AgeGate, Auth, Referral, Profile, Interests,
    Home, Discover, Groups, Plans, MyProfile,
    FindWhat, FindWhen, FindWhenCustom, FindWhere, FindResults,
    ActivityDetail, VenueSessions, SessionDetail, PersonDetail, MatchRequest,
    CreateGroup, GroupDetail, GroupChat,
    BookingReview, BookingSuccess,
    ReferAndEarn
}

/** The 5 screens with a persistent bottom nav bar — every screen reachable directly from a tab. */
val bottomNavScreens = listOf(Screen.Home, Screen.Discover, Screen.Groups, Screen.Plans, Screen.MyProfile)

// Referral sits right after Auth (needs a signed-in uid to call redeemReferralCode / check
// account age) and before Profile — matches the product brief's own described sequence ("User B
// may optionally enter User A's code during onboarding... User B continues onboarding").
val onboardingOrder = listOf(Screen.Splash, Screen.AgeGate, Screen.Auth, Screen.Referral, Screen.Profile, Screen.Interests)

fun nextOnboarding(current: Screen): Screen {
    val i = onboardingOrder.indexOf(current)
    return if (i == -1 || i == onboardingOrder.lastIndex) Screen.Home else onboardingOrder[i + 1]
}

/**
 * Real navigation history stack, not a fixed parent-child hierarchy — every screen's back
 * button returns to the actual previous screen, matching the prototype's historyStack/current
 * router exactly (Section 3 of the handoff doc).
 */
class Router(start: Screen = Screen.Splash) {
    private val backStack = mutableStateListOf<Screen>()
    var current by mutableStateOf(start)
        private set

    val canGoBack: Boolean get() = backStack.isNotEmpty()

    fun navigate(screen: Screen) {
        backStack.add(current)
        current = screen
    }

    fun goBack(): Boolean {
        if (backStack.isEmpty()) return false
        current = backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Starts over at [screen] with no history — unlike [navigate], the old back stack is
     *  discarded rather than pushed onto, so Back can't return into screens that are no longer
     *  valid (e.g. authenticated screens after a sign-out). */
    fun reset(screen: Screen) {
        backStack.clear()
        current = screen
    }
}
