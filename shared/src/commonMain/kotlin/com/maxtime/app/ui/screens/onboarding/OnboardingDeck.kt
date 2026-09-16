package com.maxtime.app.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.nav.nextOnboarding
import com.maxtime.app.ui.nav.onboardingOrder
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import kotlinx.coroutines.launch

/**
 * 3-card peek stack matching Section 5 of the handoff: front card draggable, next two screens
 * peek behind offset diagonally toward the top-left, decreasing opacity/scale with depth.
 * Dragging right past ~90dp advances; dragging left (if history exists) goes back; releasing
 * below threshold springs back to center.
 */
@Composable
fun OnboardingDeck(
    router: Router,
    screenContent: @Composable (Screen) -> Unit
) {
    val idx = onboardingOrder.indexOf(router.current)
    val nextKey = nextOnboarding(router.current)
    val next2Key = if (idx != -1 && idx + 2 < onboardingOrder.size) onboardingOrder[idx + 2] else null
    val hasPeek = idx < onboardingOrder.lastIndex
    val hasPeek2 = next2Key != null
    // Auth is the one step where "swipe forward" would skip something real instead of just an
    // optional field — Profile/Interests already fall back to sane defaults if left blank, but
    // there's no valid way to "fill in" a successful phone/OTP verification by swiping past it.
    val canSwipeForward = router.current != Screen.Auth

    val density = LocalDensity.current
    val animX = remember { Animatable(0f) }
    // Tracks the deck index this card has actually finished animating to. The drag gesture keeps
    // this in lockstep with the router itself (see onAdvance/onBack below); a screen's own button
    // calling router.navigate()/goBack() directly does not, so the effect below is what notices
    // the mismatch and plays the same slide transition the drag gesture uses — forward/back looks
    // identical regardless of whether the user swiped or tapped a button.
    var renderedIdx by remember { mutableStateOf(idx) }

    LaunchedEffect(idx) {
        if (idx != renderedIdx && idx != -1) {
            val forward = idx > renderedIdx
            renderedIdx = idx
            val startPx = with(density) { if (forward) 900.dp.toPx() else (-900).dp.toPx() }
            animX.snapTo(startPx)
            animX.animateTo(0f, tween(220))
        }
    }

    Box(Modifier.fillMaxSize().padding(34.dp)) {
        if (hasPeek2) {
            PeekCard(offsetX = (-24).dp, offsetY = (-24).dp, scale = 0.92f, rotationDeg = -3f, alpha = 0.85f) {
                screenContent(next2Key!!)
            }
        }
        if (hasPeek) {
            PeekCard(offsetX = (-13).dp, offsetY = (-13).dp, scale = 0.96f, rotationDeg = -1.5f, alpha = 1f) {
                screenContent(nextKey)
            }
        }
        DraggableFrontCard(
            animX = animX,
            canGoBack = router.canGoBack,
            canAdvance = canSwipeForward,
            onAdvance = {
                renderedIdx = idx + 1
                router.navigate(nextKey)
            },
            onBack = {
                renderedIdx = idx - 1
                router.goBack()
            }
        ) {
            screenContent(router.current)
        }
    }
}

@Composable
private fun PeekCard(
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    scale: Float,
    rotationDeg: Float,
    alpha: Float,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .offset(x = offsetX, y = offsetY)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotationDeg
                this.alpha = alpha
            }
            .clip(MaxTimeShapes.large)
            .background(MaxTimeColors.bg2)
            .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.large)
    ) { content() }
}

@Composable
private fun DraggableFrontCard(
    animX: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    canGoBack: Boolean,
    canAdvance: Boolean,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 90.dp.toPx() }
    val scope = rememberCoroutineScope()
    var badgeRightAlpha by remember { mutableStateOf(0f) }
    var badgeLeftAlpha by remember { mutableStateOf(0f) }

    val dragState = rememberDraggableState { delta ->
        scope.launch { animX.snapTo(animX.value + delta) }
        val progress = (animX.value / (thresholdPx * 1.6f)).coerceIn(-1f, 1f)
        badgeRightAlpha = if (canAdvance) progress.coerceAtLeast(0f) else 0f
        badgeLeftAlpha = if (canGoBack) (-progress).coerceAtLeast(0f) else 0f
    }

    Box(
        Modifier
            .fillMaxSize()
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    val dx = animX.value
                    when {
                        dx > thresholdPx && canAdvance -> {
                            animX.animateTo(with(density) { 900.dp.toPx() }, tween(180))
                            onAdvance()
                            animX.snapTo(0f)
                        }
                        dx < -thresholdPx && canGoBack -> {
                            animX.animateTo(with(density) { -900.dp.toPx() }, tween(180))
                            onBack()
                            animX.snapTo(0f)
                        }
                        else -> {
                            animX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                    badgeLeftAlpha = 0f
                    badgeRightAlpha = 0f
                }
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = animX.value
                    rotationZ = (animX.value / 12f).coerceIn(-16f, 16f)
                }
                .clip(MaxTimeShapes.large)
                .background(MaxTimeColors.bg1)
                .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.large)
        ) { content() }

        Text(
            "NEXT",
            color = MaxTimeColors.accent,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 24.dp)
                .graphicsLayer { alpha = badgeRightAlpha }
        )
        if (canGoBack) {
            Text(
                "BACK",
                color = Color(0xFFFF6B6B),
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 24.dp)
                    .graphicsLayer { alpha = badgeLeftAlpha }
            )
        }
    }
}
