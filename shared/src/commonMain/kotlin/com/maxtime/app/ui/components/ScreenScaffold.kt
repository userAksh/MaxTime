package com.maxtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
        Column(
            modifier = run {
                val base = if (scrollable) {
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                } else {
                    Modifier.weight(1f).fillMaxWidth()
                }
                if (bottomBar == null) base.navigationBarsPadding() else base
            },
            content = content
        )
        bottomBar?.invoke()
    }
}

/** Elevated footer bar — shadow instead of a border, background reaching the true bottom edge
 *  (navigationBarsPadding insets its content, not its background) so it never looks like it's
 *  floating above a gap. Needs bottom space for the nav bar OR the keyboard, whichever applies —
 *  navigationBars.union(ime) takes the larger of the two, not both added together. Chaining
 *  .navigationBarsPadding().imePadding() as separate modifiers would SUM them instead, leaving a
 *  gap the height of the nav bar between the keyboard and this bar whenever the keyboard is up
 *  (the nav bar inset doesn't go away just because the keyboard is now covering that area). */
@Composable
fun BottomCta(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp)
            .background(MaxTimeColors.bg1)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 16.dp),
        content = content
    )
}
