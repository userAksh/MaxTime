package com.maxtime.app.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import maxtime.shared.generated.resources.Res
import maxtime.shared.generated.resources.nav_discover_active
import maxtime.shared.generated.resources.nav_discover_solid
import maxtime.shared.generated.resources.nav_groups_active
import maxtime.shared.generated.resources.nav_groups_solid
import maxtime.shared.generated.resources.nav_home_active
import maxtime.shared.generated.resources.nav_home_solid
import maxtime.shared.generated.resources.nav_plans_active
import maxtime.shared.generated.resources.nav_plans_solid
import maxtime.shared.generated.resources.nav_profile_active
import maxtime.shared.generated.resources.nav_profile_solid
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private data class NavTab(
    val label: String,
    val screen: Screen,
    val iconSolid: DrawableResource,
    val iconActive: DrawableResource
)

private val navTabs = listOf(
    NavTab("Home", Screen.Home, Res.drawable.nav_home_solid, Res.drawable.nav_home_active),
    NavTab("Discover", Screen.Discover, Res.drawable.nav_discover_solid, Res.drawable.nav_discover_active),
    NavTab("Groups", Screen.Groups, Res.drawable.nav_groups_solid, Res.drawable.nav_groups_active),
    NavTab("Plans", Screen.Plans, Res.drawable.nav_plans_solid, Res.drawable.nav_plans_active),
    NavTab("Profile", Screen.MyProfile, Res.drawable.nav_profile_solid, Res.drawable.nav_profile_active)
)

/** Home's "presence" icon — the one tab whose selected-state animation loops forever instead of
 *  playing once and settling: a dot that grows once on selection, plus two rings that pulse
 *  outward on an infinite, staggered (400ms apart) loop the whole time the tab stays selected.
 *  Drawn on a 24x24 logical grid with center (12,13), matching the original SVG spec exactly —
 *  drawn directly rather than swapping static drawables, since a plain image swap can't show the
 *  ring motion at all (that flat swap is what read as a "blink" rather than a pulse). */
private const val RING_CENTER_X = 11f
private const val RING_CENTER_Y = 13f
private const val RING_BASE_RADIUS = 4f

// Dot placed on the ring's base-radius circumference at the 5 o'clock position, treating the
// ring as a clock face: hour -> angle clockwise from 12 (top) is hour*30deg, so 5 o'clock = 150deg.
// Cartesian offset from the ring's center, in a y-down coordinate system, is
// (r*sin(theta), -r*cos(theta)) — at theta=150deg that's (r*0.5, r*0.8660), i.e. down more than right.
private const val DOT_CENTER_X = RING_CENTER_X + RING_BASE_RADIUS * 0.5f
private const val DOT_CENTER_Y = RING_CENTER_Y + RING_BASE_RADIUS * 0.8660254f

@Composable
private fun HomeNavIcon(selected: Boolean, modifier: Modifier = Modifier) {
    val dotRadius by animateFloatAsState(
        targetValue = if (selected) 3.1f else 2.7f,
        animationSpec = tween(300),
        label = "home_dot_radius"
    )
    val dotColor = if (selected) MaxTimeColors.accent else MaxTimeColors.muted

    if (selected) {
        // Matches the source CSS keyframes exactly — including the part a plain 0→1 tween
        // misses: each ring holds fully invisible at max size from 80% to 100% of the cycle,
        // then snaps back instantly. That hard snap (not motion) is what read as a "blink."
        fun scaleSpec(delayMs: Int) = infiniteRepeatable<Float>(
            animation = keyframes {
                durationMillis = 1400
                0.3f at 0 using LinearOutSlowInEasing
                1.9f at 1120 using LinearOutSlowInEasing
                1.9f at 1400
            },
            initialStartOffset = StartOffset(delayMs, StartOffsetType.Delay)
        )
        fun alphaSpec(delayMs: Int) = infiniteRepeatable<Float>(
            animation = keyframes {
                durationMillis = 1400
                0.85f at 0 using LinearOutSlowInEasing
                0f at 1120 using LinearOutSlowInEasing
                0f at 1400
            },
            initialStartOffset = StartOffset(delayMs, StartOffsetType.Delay)
        )

        val transition = rememberInfiniteTransition(label = "home_rings")
        val ring1Scale by transition.animateFloat(0.3f, 1.9f, scaleSpec(0), label = "ring1_scale")
        val ring1Alpha by transition.animateFloat(0.85f, 0f, alphaSpec(0), label = "ring1_alpha")
        val ring2Scale by transition.animateFloat(0.3f, 1.9f, scaleSpec(400), label = "ring2_scale")
        val ring2Alpha by transition.animateFloat(0.85f, 0f, alphaSpec(400), label = "ring2_alpha")

        Canvas(modifier.size(28.dp)) {
            val unit = size.width / 24f
            val ringCenter = Offset(unit * RING_CENTER_X, unit * RING_CENTER_Y)
            val dotCenter = Offset(unit * DOT_CENTER_X, unit * DOT_CENTER_Y)
            drawHomeRing(ringCenter, unit, ring1Scale, ring1Alpha)
            drawHomeRing(ringCenter, unit, ring2Scale, ring2Alpha)
            drawCircle(color = dotColor, radius = unit * dotRadius, center = dotCenter)
        }
    } else {
        Canvas(modifier.size(28.dp)) {
            val unit = size.width / 24f
            drawCircle(color = dotColor, radius = unit * dotRadius, center = Offset(unit * DOT_CENTER_X, unit * DOT_CENTER_Y))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeRing(center: Offset, unit: Float, scale: Float, alpha: Float) {
    if (alpha <= 0f) return
    drawCircle(
        color = MaxTimeColors.accent.copy(alpha = alpha),
        radius = unit * 4f * scale,
        center = center,
        style = Stroke(width = unit * 1.6f)
    )
}

// Selected-chip background: a subtle dark vertical gradient (not a flat tint), plus a soft
// olive-green bloom behind it — colors and falloff pulled directly from the approved reference
// tile design (a 285x300dp tile with an 80dp-blurred, 40dp-oversized glow behind it).
private val ChipTop = Color(0xFF1C2013)
private val ChipBottom = Color(0xFF15180E)
private val ChipGlow = Color(0xFF9AD72D)
private val ChipTopHighlight = Color(0xFFC8F56E).copy(alpha = 0.05f)

/** Soft glow behind the active tab's chip — a plain 2D radial-gradient paint via `drawBehind`,
 *  fixed density-derived radius, no elevation/RenderNode involved at all (an earlier
 *  `Modifier.shadow(...)`-based version rendered a visible rectangular compositing artifact on
 *  this emulator, reproduced on every tab — a RenderNode/SwiftShader issue). Also deliberately
 *  not `Modifier.blur()`: that requires API 31+ and silently no-ops below it, and this app's
 *  minSdk is 26 — so the 4-stop gradient below is the *primary* implementation, not a fallback,
 *  reproducing the reference's Gaussian falloff shape (0.19 -> 0.11 -> 0.03 -> 0 alpha) directly. */
private fun Modifier.softGlow(color: Color, radius: androidx.compose.ui.unit.Dp): Modifier = drawBehind {
    val r = radius.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = 0.19f),
            0.55f to color.copy(alpha = 0.11f),
            0.80f to color.copy(alpha = 0.03f),
            1.00f to color.copy(alpha = 0f),
            center = center,
            radius = r
        ),
        radius = r
    )
}

/** The chip's vertical-gradient fill plus its top hairline highlight, drawn as raw canvas calls
 *  in one `drawBehind` rather than via `Modifier.background(Brush)` + a separate highlight `Box`.
 *  Both of those, independently, reproduced the same full-width layout corruption on this emulator
 *  that `Modifier.shadow()` did (isolated by A/B testing each alone) — `Modifier.background(Brush)`
 *  and a `fillMaxWidth()` sibling both appear to hit the same RenderNode/shader-layer problem.
 *  Plain `drawRect`/`drawLine` calls inside `drawBehind`, like `softGlow` above, don't touch that
 *  code path at all, so this reproduces the identical pixels a different way. Rounded corners come
 *  from the surrounding `.clip(MaxTimeShapes.medium)` already in the chain — no need to duplicate
 *  that here. */
private fun Modifier.chipBackground(top: Color, bottom: Color, highlight: Color): Modifier = drawBehind {
    drawRect(brush = Brush.verticalGradient(listOf(top, bottom), startY = 0f, endY = size.height))
    val hairlineY = 0.5.dp.toPx()
    drawLine(
        color = highlight,
        start = Offset(0f, hairlineY),
        end = Offset(size.width, hairlineY),
        strokeWidth = 1.dp.toPx()
    )
}

private val NavBarShape = RoundedCornerShape(18.dp)

/** Persistent tab bar shown on every [com.maxtime.app.ui.nav.bottomNavScreens] destination — a
 *  floating rounded card with margin from the screen edges and a full outline all the way
 *  around, matching the original CSS reference exactly:
 *  `.navbottom{background:var(--bg1);border:1px solid var(--card-border);border-radius:18px;}`
 *  (An earlier iteration made this bar full-width/flush with a top-only divider line instead —
 *  this reverts back to the floating-card look from the approved reference screenshot.)
 *
 *  Active tab treatment matches the approved spec: accent-wash chip background with a soft
 *  outward lime glow. */
@Composable
fun BottomNav(router: Router, active: Screen) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .shadow(elevation = 14.dp, shape = NavBarShape)
            .clip(NavBarShape)
            .background(MaxTimeColors.bg1)
            // Full outline around the whole card — a plain stroked rounded-rect via drawBehind
            // (not Modifier.border, to stay on the same safe raw-canvas pattern as the rest of
            // this file). Inset by half the stroke width so the 1dp line isn't half-clipped by
            // the .clip(NavBarShape) above.
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val inset = strokeWidth / 2f
                drawRoundRect(
                    color = MaxTimeColors.cardBorder,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(18.dp.toPx() - inset),
                    style = Stroke(width = strokeWidth)
                )
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        navTabs.forEach { tab ->
            val isOn = tab.screen == active
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    // Fixed width for every tab, active or not — sized to comfortably fit the
                    // longest label ("Discover") — so the chip doesn't wrap-content to a
                    // different size per label (narrow for "Home", wide for "Discover") and so
                    // switching the active tab doesn't reflow the other tabs' spacing either.
                    .width(62.dp)
                    .then(
                        if (isOn) Modifier.softGlow(color = ChipGlow, radius = 52.dp) else Modifier
                    )
                    .clip(MaxTimeShapes.medium)
                    .then(
                        if (isOn) Modifier.chipBackground(ChipTop, ChipBottom, ChipTopHighlight) else Modifier
                    )
                    .clickable { if (router.current != tab.screen) router.navigate(tab.screen) }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    // Fixed-height icon slot for every tab — Home's icon is drawn a bit bigger
                    // (28.dp) than the others (22.dp), but without this common slot height each
                    // tab's Column ends up a different total height, and centering columns of
                    // different heights in the row shifts their text baselines out of alignment
                    // (Home's text sat visibly lower than Discover's).
                    Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                        if (tab.screen == Screen.Home) {
                            HomeNavIcon(selected = isOn, modifier = Modifier.offset(y = (-4).dp))
                        } else if (isOn) {
                            Icon(
                                painter = painterResource(tab.iconActive),
                                contentDescription = tab.label,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(tab.iconSolid),
                                contentDescription = tab.label,
                                tint = MaxTimeColors.muted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Text(tab.label, color = if (isOn) MaxTimeColors.accent else MaxTimeColors.muted, fontSize = 11.sp, fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
