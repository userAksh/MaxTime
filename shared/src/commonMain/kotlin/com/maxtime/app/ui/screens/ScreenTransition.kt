package com.maxtime.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Simple fade + slight upward slide on entry — matches Section 5's transition spec for
 *  every screen after onboarding (no deck/peek effect here). */
@Composable
fun <T> ScreenTransition(key: T, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = key,
        modifier = Modifier,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 20 }) togetherWith fadeOut(tween(160))
        },
        label = "screen-transition"
    ) { state ->
        content(state)
    }
}
