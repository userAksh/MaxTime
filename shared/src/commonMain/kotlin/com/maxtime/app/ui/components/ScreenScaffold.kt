package com.maxtime.app.ui.components

import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing

/** Full-bleed screen container: gradient background, optional pinned top/bottom bars, scrollable middle.
 *  The gradient is drawn behind the status bar too (statusBarsPadding is applied after the background,
 *  not before) so the status bar area always matches this screen's own background. When there's no
 *  bottomBar, the scrollable content itself gets navigationBarsPadding so its last element isn't
 *  covered by the gesture bar — when there IS one, [BottomCta] handles that itself (its background
 *  needs to reach the true bottom edge, not stop short of it). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        modifier
            .fillMaxSize()
            .background(MaxTimeColors.screenGradient)
            .statusBarsPadding()
            // Tapping anywhere that isn't itself an interactive child (a button, a text field)
            // dismisses the keyboard — a text field consumes its own taps for cursor placement,
            // so this only ever fires on genuinely empty space, never fighting a field's own tap.
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
    ) {
        topBar?.invoke()
        // A focused field's default "scroll into view" correction and BottomCta's keyboard-inset
        // driven resize (below) are two independently-triggered animations with no shared clock —
        // Compose's own bring-into-view tween vs. however Compose Multiplatform samples iOS's
        // native keyboard slide animation for WindowInsets.ime. When they don't finish in the same
        // frame, the visible result is exactly a height/spacing glitch right at the keyboard
        // transition, even though no single element's own size is ever actually wrong. Forcing the
        // scroll correction to be instant (snap, zero duration) removes it as a competing animation
        // instead of trying to resync two independently-paced ones.
        val instantBringIntoView = remember {
            object : BringIntoViewSpec {
                override val scrollAnimationSpec = snap<Float>()
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides instantBringIntoView) {
            Column(
                modifier = run {
                    val base = if (scrollable) {
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    } else {
                        Modifier.weight(1f).fillMaxWidth()
                    }
                    // A screen with no bottomBar has nothing else absorbing either inset, so its
                    // own scrollable content needs both directly: navigationBarsPadding so the
                    // last item isn't covered by the gesture bar at rest, and imePadding so it
                    // isn't covered by the keyboard once one comes up (with a bottomBar present,
                    // that bar owns both of these instead — see BottomCta).
                    if (bottomBar == null) base.navigationBarsPadding().imePadding() else base
                },
                content = content
            )
        }
        bottomBar?.invoke()
    }
}

/** Elevated footer bar — shadow instead of a border, background reaching the true bottom edge
 *  (navigationBarsPadding insets its content, not its background) so it never looks like it's
 *  floating above a gap. Needs bottom space for the nav bar OR the keyboard, whichever applies —
 *  navigationBars.union(ime) takes the larger of the two, not both added together. Chaining
 *  .navigationBarsPadding().imePadding() as separate modifiers would SUM them instead, leaving a
 *  gap the height of the nav bar between the keyboard and this bar whenever the keyboard is up
 *  (the nav bar inset doesn't go away just because the keyboard is now covering that area).
 *
 *  Elevation kept low (was 10.dp): a shadow renders OUTSIDE its casting element's own layout
 *  bounds — that's inherent to what a drop shadow is — and this bar is a later sibling in the
 *  same plain Column as the scrollable content above it (no isolated compositing layer between
 *  them), so the shadow paints directly over whatever content sits within its blur spread of
 *  this bar's top edge, independent of how much padding/whitespace separates them. A high
 *  elevation here previously read as a black bar bleeding over the field above it. */
@Composable
fun BottomCta(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp)
            .background(MaxTimeColors.bg1)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            // Top padding trimmed a bit more than bottom — the top edge is what sits closest to
            // (and, on a bar with several stacked elements like phone-entry's Send OTP + divider
            // + Email/Guest row, can crowd) whatever content is above it; bottom keeps its
            // breathing room against the home indicator / nav bar.
            .padding(start = MaxTimeSpacing.screenPadding, end = MaxTimeSpacing.screenPadding, top = 10.dp, bottom = 16.dp),
        content = content
    )
}
