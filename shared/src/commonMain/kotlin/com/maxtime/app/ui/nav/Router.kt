package com.maxtime.app.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen {
    Splash, AgeGate, Auth, Profile, Interests,
    Home, FindWhat, FindWhen, FindWhenCustom, FindWhere, FindResults,
    ActivityDetail, SessionDetail, PersonDetail, MatchRequest,
    CreateGroup, GroupDetail, GroupChat,
    BookingReview, BookingSuccess
}

val onboardingOrder = listOf(Screen.Splash, Screen.AgeGate, Screen.Auth, Screen.Profile, Screen.Interests)

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
}
