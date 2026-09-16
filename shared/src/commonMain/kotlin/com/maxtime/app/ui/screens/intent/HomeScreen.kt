package com.maxtime.app.ui.screens.intent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.alexzhirkevich.compottie.Lottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import maxtime.shared.generated.resources.Res
import com.maxtime.app.data.ActivityInfo
import com.maxtime.app.data.AppState
import com.maxtime.app.data.Group
import com.maxtime.app.data.Session
import com.maxtime.app.ui.components.AvatarStack
import com.maxtime.app.ui.components.BottomNav
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.SectionLabel
import com.maxtime.app.ui.components.ShimmerCard
import com.maxtime.app.ui.components.StatusChip
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.screens.LocationPopup
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(state: AppState, router: Router) {
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(MaxTimeColors.screenGradient).statusBarsPadding()) {
            HomeTopBar()
            Text(
                "📍 ${state.location.area}",
                style = MaxTimeType.caption,
                modifier = Modifier
                    .padding(start = MaxTimeSpacing.screenPadding, end = MaxTimeSpacing.screenPadding, top = 0.dp, bottom = 6.dp)
                    .clickable { state.locationConfirmed = false }
            )
          Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(
                Modifier
                    .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 4.dp)
                    .clip(MaxTimeShapes.pill)
                    .background(MaxTimeColors.accentWash)
                    .border(1.dp, MaxTimeColors.accentDim, MaxTimeShapes.pill)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "⚡ ${state.freeInvitesLeft} free invite${if (state.freeInvitesLeft == 1) "" else "s"} left",
                    color = MaxTimeColors.accent2,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "What do you want to do?",
                style = MaxTimeType.heading.copy(fontSize = 21.sp),
                modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp)
            )
            Box(
                Modifier
                    .padding(horizontal = MaxTimeSpacing.screenPadding)
                    .fillMaxWidth()
                    .clip(MaxTimeShapes.medium)
                    .background(MaxTimeColors.accent)
                    .clickable { router.navigate(Screen.FindWhat) }
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Find something to do →", color = MaxTimeColors.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                Text("Already know what you want? ", style = MaxTimeType.caption, modifier = Modifier.alignByBaseline())
                Text(
                    "Book solo",
                    color = MaxTimeColors.accent2, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline().clickable {
                        scope.launch {
                            state.selectSession()
                            router.navigate(Screen.SessionDetail)
                        }
                    }
                )
                Text(" · ", style = MaxTimeType.caption, modifier = Modifier.alignByBaseline())
                Text(
                    "Start an activity",
                    color = MaxTimeColors.accent2, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline().clickable { router.navigate(Screen.CreateGroup) }
                )
            }

            SectionLabel("Popular near you")
            val popular = listOf("Badminton", "Swimming", "Pottery", "Pilates", "Football")
            PopularNearYouCarousel(
                activities = popular,
                selectedActivity = state.intent.activity,
                infoFor = { state.activityInfo(it) },
                onSelect = { activity ->
                    state.intent = state.intent.copy(activity = activity)
                    router.navigate(Screen.FindWhen)
                }
            )

            var recommendedGroup by remember { mutableStateOf<Group?>(null) }
            var nearby by remember { mutableStateOf<Pair<String, Session>?>(null) }
            var sessionsLoading by remember { mutableStateOf(true) }
            LaunchedEffect(state.intent.activity, state.location) {
                sessionsLoading = true
                recommendedGroup = state.recommendedRealGroup()
                nearby = state.nearbySession(recommendedGroup?.activity)
                sessionsLoading = false
            }

            SectionLabel("People & groups doing this", actionText = "See all", onAction = { router.navigate(Screen.FindWhat) })
            val groupValue = recommendedGroup
            if (sessionsLoading) {
                ShimmerCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp))
            } else if (groupValue != null) {
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    onClick = {
                        state.selectedGroup = groupValue
                        router.navigate(Screen.GroupDetail)
                    }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                "${groupValue.members} going · ${groupValue.openSlots} spot${if (groupValue.openSlots == 1) "" else "s"} open",
                                color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 15.sp
                            )
                            Text("${groupValue.activity} · ${groupValue.whenLabel} · ${groupValue.venueLine}", style = MaxTimeType.caption)
                        }
                        StatusChip(groupValue.statusLabel, groupValue.statusVariant)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        AvatarStack(groupValue.memberList.map { it.personName }.ifEmpty { listOf(groupValue.creator) })
                        Box(Modifier.clip(RoundedCornerShape(9.dp)).background(MaxTimeColors.accent).padding(horizontal = 14.dp, vertical = 7.dp)) {
                            Text("See details", color = MaxTimeColors.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Text(
                    "No groups started for these activities yet — be the first.",
                    style = MaxTimeType.caption,
                    modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding)
                )
            }

            Text(
                "SESSIONS NEAR YOU",
                color = MaxTimeColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp)
            )
            val nearbyValue = nearby
            if (sessionsLoading) {
                ShimmerCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp), height = 64.dp)
            } else if (nearbyValue != null) {
                val (activity, session) = nearbyValue
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    onClick = {
                        state.intent = state.intent.copy(activity = activity)
                        router.navigate(Screen.FindResults)
                    }
                ) {
                    Text("$activity · ${session.date}, ${session.startTime}", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "${session.venueName}, ${state.location.kmAwayLabel(session.distanceKm)} · ${session.spotsLeft} spots left",
                        style = MaxTimeType.caption,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            SectionLabel("Coming up")
            val recentGroup = state.selectedGroup
            if (recentGroup != null) {
                MaxTimeCard(
                    Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp),
                    onClick = { router.navigate(Screen.GroupDetail) }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${recentGroup.activity} Group", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text("${recentGroup.whenLabel} · ${recentGroup.venue ?: "Session pending"}", style = MaxTimeType.caption)
                        }
                        Text("›", color = MaxTimeColors.textStrong, fontSize = 18.sp)
                    }
                }
            } else {
                Text(
                    "No upcoming activities yet — find something below.",
                    style = MaxTimeType.body,
                    modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding)
                )
            }
          }

            BottomNav(router, active = Screen.Home)
        }

        if (!state.locationConfirmed) {
            LocationPopup(state)
        }
    }
}

@Composable
private fun HomeTopBar() {
    Row(
        Modifier.fillMaxWidth().padding(start = MaxTimeSpacing.screenPadding, end = MaxTimeSpacing.screenPadding, top = 12.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("MaxTime", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        Box(Modifier.size(32.dp).clip(CircleShape).background(MaxTimeColors.cardBg).border(1.dp, MaxTimeColors.cardBorder, CircleShape))
    }
}

/** Jewel-tone backgrounds for the promo cards below — cycled by index, deliberately distinct
 *  from [MaxTimeColors]' single lime accent so each card in the carousel reads as its own
 *  "product," the way a real promo/merchandising carousel would. */
private val carouselPalette = listOf(
    Color(0xFF2F6B4F), // jade
    Color(0xFF4A3F7A), // indigo
    Color(0xFF7C6A1F), // olive gold
    Color(0xFF8B3E3E), // terracotta
    Color(0xFF2F5D6B), // teal
)

/**
 * Auto-advancing, snap-paged promo carousel (each activity gets its own colored "card," with
 * its emoji bleeding past the top-right corner) — replaces the old plain chip row with something
 * that actually earns the "Popular near you" framing. Manual swipes are never fought: the
 * auto-advance loop always resumes from wherever the pager currently sits, not a fixed index.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PopularNearYouCarousel(
    activities: List<String>,
    selectedActivity: String,
    infoFor: (String) -> ActivityInfo,
    onSelect: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { activities.size })
    LaunchedEffect(pagerState) {
        while (true) {
            delay(2600)
            val next = (pagerState.currentPage + 1) % activities.size
            pagerState.animateScrollToPage(next, animationSpec = tween(550))
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = MaxTimeSpacing.screenPadding + 14.dp),
        pageSpacing = 10.dp
    ) { page ->
        val activity = activities[page]
        // 1 while this page is the fully-settled current page, falling off to 0 as it scrolls
        // away in either direction — read continuously off the pager's live scroll offset (not
        // animateFloatAsState) so the product image grows into focus in lockstep with the actual
        // drag/settle motion, exactly like the reference carousel.
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        val focusFraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
        val isCurrent = page == pagerState.currentPage
        val color = carouselPalette[page % carouselPalette.size]
        val selected = activity == selectedActivity
        val onClick = { onSelect(activity) }
        if (activity == "Badminton") {
            BadmintonPromoCard(
                activity = activity,
                info = infoFor(activity),
                color = color,
                selected = selected,
                focusFraction = focusFraction,
                isCurrent = isCurrent,
                onClick = onClick
            )
        } else {
            ActivityPromoCard(
                activity = activity,
                info = infoFor(activity),
                color = color,
                selected = selected,
                focusFraction = focusFraction,
                isCurrent = isCurrent,
                onClick = onClick
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
        activities.indices.forEach { i ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (i == pagerState.currentPage) 16.dp else 6.dp, 6.dp)
                    .clip(MaxTimeShapes.pill)
                    .background(if (i == pagerState.currentPage) MaxTimeColors.accent else MaxTimeColors.chipBorder)
            )
        }
    }
}

private const val ICON_RESTING_ROTATION = 14f
private const val PROMO_CARD_HEIGHT = 152

/** Shared shell for every promo card: fixed height, clipped/colored/clickable background, the
 *  icon/Lottie overlaid on the right (via [iconAlignment] — [Alignment.CenterEnd] centers it
 *  vertically with equal top and bottom margin, the same right-side position it's always held,
 *  just no longer bled toward the top corner), and a text block pinned at the bottom-left. */
@Composable
private fun PromoCardShell(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable BoxScope.() -> Unit,
    textZone: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(PROMO_CARD_HEIGHT.dp)
            .clip(MaxTimeShapes.large)
            .background(color)
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.55f), MaxTimeShapes.large) else Modifier
            )
    ) {
        icon()
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), content = textZone)
    }
}

@Composable
private fun PromoCardTextZone(activity: String, info: ActivityInfo) {
    Text(activity, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    Text(info.category, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "From ₹${info.price}",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun ActivityPromoCard(
    activity: String,
    info: ActivityInfo,
    color: Color,
    selected: Boolean,
    focusFraction: Float,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    // As a card scrolls into the centered/settled position its image grows — the "hero" motion
    // from the reference carousel, not just the card sliding as a flat unit.
    val iconScale = 0.6f + 0.4f * focusFraction

    // "Hanging from a string, dropping in and swinging to rest" — every time this card becomes
    // the front-and-center one, replay a drop-and-pendulum flourish on its image rather than
    // just letting it sit there at a fixed tilt. Keyed on the activity (not just isCurrent) so a
    // fresh Animatable is used per card instance and always starts from the same "just dropped"
    // pose. Replays on every visit — an auto-advancing carousel loops past the same card
    // repeatedly, so the flourish should too, not just play once ever. The card's own clip still
    // hides the icon while it's above the card (offset far enough up), so it visibly descends
    // into its centered resting spot rather than fading in from nowhere.
    val swing = remember(activity) { Animatable(0f) }
    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            swing.snapTo(0f)
            swing.animateTo(
                1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }
    val swingRotation = lerp(-65f, ICON_RESTING_ROTATION, swing.value)
    val dropOffset = lerpDp((-64).dp, 0.dp, swing.value)

    PromoCardShell(
        color = color,
        selected = selected,
        onClick = onClick,
        icon = {
            Text(
                info.icon,
                fontSize = 76.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-16).dp, y = dropOffset)
                    .scale(iconScale)
                    .rotate(swingRotation)
                    .alpha(0.7f + 0.3f * focusFraction)
            )
        },
        textZone = { PromoCardTextZone(activity, info) }
    )
}

/**
 * The literal "shuttle hits the racket" version of [ActivityPromoCard], used only for Badminton —
 * a real, professionally-authored Lottie animation (`files/badminton_racket.json`) rather than a
 * hand-rolled approximation, played from a single master progress value we drive ourselves (not
 * `animateLottieCompositionAsState`'s own looping) so it stays in the same "replay every time this
 * card becomes current" pattern as every other card's entrance, keyed off [isCurrent].
 */
@Composable
private fun BadmintonPromoCard(
    activity: String,
    info: ActivityInfo,
    color: Color,
    selected: Boolean,
    focusFraction: Float,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val iconScale = 0.6f + 0.4f * focusFraction
    val composition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(Res.readBytes("files/badminton_racket.json").decodeToString())
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(isCurrent, composition) {
        val duration = composition?.duration ?: return@LaunchedEffect
        if (isCurrent) {
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                animationSpec = tween(duration.inWholeMilliseconds.toInt().coerceAtLeast(1), easing = LinearEasing)
            )
        }
    }

    PromoCardShell(
        color = color,
        selected = selected,
        onClick = onClick,
        icon = {
            val comp = composition
            if (comp != null) {
                Lottie(
                    painter = rememberLottiePainter(composition = comp, progress = { progress.value }),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = (-16).dp)
                        .size(84.dp)
                        .scale(iconScale)
                )
            }
        },
        textZone = { PromoCardTextZone(activity, info) }
    )
}

