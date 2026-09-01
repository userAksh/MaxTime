package com.maxtime.app.ui.screens

import androidx.compose.runtime.Composable
import com.maxtime.app.data.AppState
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.screens.booking.BookingReviewScreen
import com.maxtime.app.ui.screens.booking.BookingSuccessScreen
import com.maxtime.app.ui.screens.discovery.ActivityDetailScreen
import com.maxtime.app.ui.screens.discovery.MatchRequestScreen
import com.maxtime.app.ui.screens.discovery.PersonDetailScreen
import com.maxtime.app.ui.screens.discovery.SessionDetailScreen
import com.maxtime.app.ui.screens.groups.CreateGroupScreen
import com.maxtime.app.ui.screens.groups.GroupChatScreen
import com.maxtime.app.ui.screens.groups.GroupDetailScreen
import com.maxtime.app.ui.screens.intent.FindResultsScreen
import com.maxtime.app.ui.screens.intent.FindWhatScreen
import com.maxtime.app.ui.screens.intent.FindWhenCustomScreen
import com.maxtime.app.ui.screens.intent.FindWhenScreen
import com.maxtime.app.ui.screens.intent.FindWhereScreen
import com.maxtime.app.ui.screens.intent.HomeScreen

/**
 * Dispatches to whichever screen `router.current` points to — mirrors the prototype's
 * `screens[current]()` lookup. No transition animation between screens.
 */
@Composable
fun RootScreenHost(state: AppState, router: Router) {
    ScreenTransition(key = router.current) {
        when (router.current) {
            Screen.Home -> HomeScreen(state, router)
            Screen.FindWhat -> FindWhatScreen(state, router)
            Screen.FindWhen -> FindWhenScreen(state, router)
            Screen.FindWhenCustom -> FindWhenCustomScreen(state, router)
            Screen.FindWhere -> FindWhereScreen(state, router)
            Screen.FindResults -> FindResultsScreen(state, router)
            Screen.ActivityDetail -> ActivityDetailScreen(state, router)
            Screen.SessionDetail -> SessionDetailScreen(state, router)
            Screen.PersonDetail -> PersonDetailScreen(state, router)
            Screen.MatchRequest -> MatchRequestScreen(state, router)
            Screen.CreateGroup -> CreateGroupScreen(state, router)
            Screen.GroupDetail -> GroupDetailScreen(state, router)
            Screen.GroupChat -> GroupChatScreen(state, router)
            Screen.BookingReview -> BookingReviewScreen(state, router)
            Screen.BookingSuccess -> BookingSuccessScreen(state, router)
            else -> Unit
        }
    }
}
