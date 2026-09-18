package com.maxtime.app.ui.screens.referral

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.firebase.ReferralCodeInfo
import com.maxtime.app.data.firebase.ReferralFunctions
import com.maxtime.app.data.share.rememberShareLauncher
import com.maxtime.app.ui.components.BackButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SuccessToast
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import kotlinx.coroutines.launch
import maxtime.shared.generated.resources.Res
import maxtime.shared.generated.resources.refer_earn_friends_icon
import maxtime.shared.generated.resources.refer_earn_gift_icon
import maxtime.shared.generated.resources.refer_earn_highfive
import maxtime.shared.generated.resources.refer_earn_more_friends_text
import maxtime.shared.generated.resources.refer_earn_people_icon
import org.jetbrains.compose.resources.painterResource

/**
 * V1 "share your own code" screen — the counterpart to ReferralCodeCard (which is for someone
 * ENTERING a friend's code during onboarding). This screen is for an already-joined user sharing
 * THEIR OWN code with others. Visuals match the approved reference (2026-09-19 handoff,
 * "Screenshot 2026-09-19 at 1.31.19 AM") as closely as reasonable within V1's constraints:
 *
 * - Every quick-share icon (WhatsApp / Instagram / Messages / Copy Code / More) — including the
 *   big "Share Referral Code" button — opens the SAME cross-platform native share sheet
 *   ([rememberShareLauncher]), not a real per-app integration. A genuine one-tap-into-WhatsApp
 *   deep link needs an `LSApplicationQueriesSchemes` entry in iosApp's Info.plist (a native
 *   project file, untested here), and Instagram's share URL scheme doesn't support prefilled
 *   text at all (only images/Stories via its own pasteboard API) — so a real Instagram button
 *   would silently do nothing useful. Routing everything through the one already-proven share
 *   sheet is also the more literal reading of "Do not build deep links" from the product brief:
 *   app URL schemes are themselves a form of deep link.
 * - No tracking/history/rewards-ledger view here — out of scope for this pass. The footer promo
 *   card's chevron is decorative only, it doesn't navigate anywhere.
 * - All reward numbers come from generateMyReferralCode() (functions/src/referral.ts), which
 *   itself snapshots the live, admin-configurable referral_configs doc — nothing here duplicates
 *   or hardcodes that logic. The only hardcoded numbers below are the documented V1 fallback
 *   values shown if that call fails, purely so the screen still explains the program instead of
 *   rendering blank.
 * - Unlike the single-paragraph copy this screen used before, the reward lifecycle accuracy
 *   (₹ caps, "applied at checkout" vs "after their booking is completed") now lives directly in
 *   the two reward cards' own description lines — no separate disclaimer caption needed anymore.
 */
private const val FALLBACK_REFEREE_PERCENT = 10
private const val FALLBACK_REFEREE_CAP = 50
private const val FALLBACK_REFERRER_PERCENT = 20
private const val FALLBACK_REFERRER_CAP = 100

private sealed interface ReferAndEarnUiState {
    object Loading : ReferAndEarnUiState
    data class Loaded(val info: ReferralCodeInfo) : ReferAndEarnUiState
    object Failed : ReferAndEarnUiState
}

@Composable
fun ReferAndEarnScreen(router: Router) {
    val referralFunctions = remember { ReferralFunctions() }
    val clipboard = LocalClipboardManager.current
    val shareLauncher = rememberShareLauncher()
    val scope = rememberCoroutineScope()

    var uiState by remember { mutableStateOf<ReferAndEarnUiState>(ReferAndEarnUiState.Loading) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            uiState = ReferAndEarnUiState.Loading
            uiState = try {
                ReferAndEarnUiState.Loaded(referralFunctions.generateMyReferralCode())
            } catch (e: Exception) {
                ReferAndEarnUiState.Failed
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun referralText(code: String, refereePercent: Int, refereeCap: Int): String =
        "Join me on MaxTime! Use my code $code to get $refereePercent% off your first booking " +
            "(up to ₹$refereeCap) — once you complete it, I get a reward too 🎉"

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold {
            val state = uiState
            val refereePercent = (state as? ReferAndEarnUiState.Loaded)?.info?.refereeDiscountPercent ?: FALLBACK_REFEREE_PERCENT
            val refereeCap = (state as? ReferAndEarnUiState.Loaded)?.info?.refereeDiscountCapAmount ?: FALLBACK_REFEREE_CAP
            val referrerPercent = (state as? ReferAndEarnUiState.Loaded)?.info?.referrerDiscountPercent ?: FALLBACK_REFERRER_PERCENT
            val referrerCap = (state as? ReferAndEarnUiState.Loaded)?.info?.referrerDiscountCapAmount ?: FALLBACK_REFERRER_CAP
            val code = (state as? ReferAndEarnUiState.Loaded)?.info?.code

            Row(
                Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding).padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = { router.goBack() })
                Spacer(Modifier.width(10.dp))
                Text("MaxTime", color = MaxTimeColors.accent, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding).padding(top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).offset(y = 10.dp)) {
                    Text(
                        "Refer & Earn",
                        color = MaxTimeColors.textStrong,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp
                    )
                    Text(
                        "Bring your friends to MaxTime.",
                        color = MaxTimeColors.muted,
                        fontSize = 12.sp,
                        softWrap = false,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        buildAnnotatedString {
                            append("You get ")
                            withStyle(SpanStyle(color = MaxTimeColors.accent, fontWeight = FontWeight.Bold)) { append("$referrerPercent% off") }
                            append(", they get ")
                            withStyle(SpanStyle(color = MaxTimeColors.accent, fontWeight = FontWeight.Bold)) { append("$refereePercent% off") }
                        },
                        color = MaxTimeColors.muted,
                        fontSize = 12.sp,
                        softWrap = false
                    )
                }
                Box(Modifier.size(width = 171.dp, height = 132.dp).padding(start = 16.dp)) {
                    // The source PNG's own bounding box used to leave a much bigger margin on
                    // its right side than its left (94px vs 53px) — no amount of Compose sizing
                    // could fix that, since it was baked into the image file itself. It's now
                    // cropped tight to its actual content, and this Box's aspect ratio matches
                    // the cropped image's own (≈1.29:1) so ContentScale.Fit fills it exactly,
                    // with no letterboxing on any side.
                    Image(
                        painter = painterResource(Res.drawable.refer_earn_highfive),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Sits in the actual gap between the two figures — below the clasped hands,
                    // above their chests. That gap is around the vertical center of the
                    // illustration, not flush at its bottom edge (the very bottom is where their
                    // hoodies/chests fully render, so anchoring here instead of BottomCenter is
                    // what keeps the text from overlapping their bodies).
                    Image(
                        painter = painterResource(Res.drawable.refer_earn_more_friends_text),
                        contentDescription = "More Friends More Play",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 42.dp)
                            .size(width = 60.dp, height = 35.dp)
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RewardCard(
                    modifier = Modifier.weight(1f),
                    icon = Res.drawable.refer_earn_gift_icon,
                    label = "YOU GET",
                    percent = referrerPercent,
                    description = "On your next booking (up to ₹$referrerCap), after their booking is completed."
                )
                RewardCard(
                    modifier = Modifier.weight(1f),
                    icon = Res.drawable.refer_earn_friends_icon,
                    label = "THEY GET",
                    percent = refereePercent,
                    description = "On their first booking (up to ₹$refereeCap), applied at checkout."
                )
            }

            Box(Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 10.dp)) {
                when (state) {
                    is ReferAndEarnUiState.Loading -> CodeCardPlaceholder()
                    is ReferAndEarnUiState.Loaded -> CodeCard(
                        code = state.info.code,
                        onCopy = {
                            clipboard.setText(AnnotatedString(state.info.code))
                            toastMessage = "Code copied"
                        }
                    )
                    is ReferAndEarnUiState.Failed -> CodeCardFailed(onRetry = ::load)
                }
            }

            Column(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding)) {
                ShareReferralCodeButton(
                    enabled = code != null,
                    onClick = { code?.let { shareLauncher.shareText(referralText(it, refereePercent, refereeCap)) } }
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QuickShareIcon(
                        label = "WhatsApp",
                        background = Color(0xFF2FCE5B),
                        onClick = { code?.let { shareLauncher.shareText(referralText(it, refereePercent, refereeCap)) } }
                    ) { WhatsAppGlyph(tint = Color.White) }
                    QuickShareIcon(
                        label = "Instagram",
                        background = Brush.linearGradient(
                            listOf(Color(0xFFFEDA75), Color(0xFFD62976), Color(0xFF4F5BD5))
                        ),
                        onClick = { code?.let { shareLauncher.shareText(referralText(it, refereePercent, refereeCap)) } }
                    ) { InstagramGlyph() }
                    QuickShareIcon(
                        label = "Snapchat",
                        background = Color.Transparent,
                        onClick = { code?.let { shareLauncher.shareText(referralText(it, refereePercent, refereeCap)) } }
                    ) { SnapchatGlyph() }
                    QuickShareIcon(
                        label = "Copy Code",
                        background = MaxTimeColors.cardBg,
                        onClick = {
                            code?.let {
                                clipboard.setText(AnnotatedString(it))
                                toastMessage = "Code copied"
                            }
                        }
                    ) { CopyGlyph(tint = MaxTimeColors.textStrong) }
                    QuickShareIcon(
                        label = "More",
                        background = MaxTimeColors.cardBg,
                        onClick = { code?.let { shareLauncher.shareText(referralText(it, refereePercent, refereeCap)) } }
                    ) { MoreDotsGlyph(tint = MaxTimeColors.textStrong) }
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(
                Modifier
                    .padding(horizontal = MaxTimeSpacing.screenPadding)
                    .fillMaxWidth()
                    .clip(MaxTimeShapes.medium)
                    .background(MaxTimeColors.cardBg)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(Res.drawable.refer_earn_people_icon),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(MaxTimeShapes.medium)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("More friends. More play.", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Text(
                        "Build your crew, discover new activities and do more together!",
                        color = MaxTimeColors.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text("›", color = MaxTimeColors.muted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
        }
        SuccessToast(
            toastMessage ?: "",
            visible = toastMessage != null,
            onDismiss = { toastMessage = null }
        )
    }
}

@Composable
private fun RewardCard(
    icon: org.jetbrains.compose.resources.DrawableResource,
    label: String,
    percent: Int,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(MaxTimeShapes.medium)
            .background(MaxTimeColors.cardBg)
            .padding(14.dp)
    ) {
        Image(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp).clip(MaxTimeShapes.small))
        Text(label, color = MaxTimeColors.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 8.dp))
        Text("$percent% OFF", color = MaxTimeColors.accent, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.padding(top = 2.dp))
        Text(description, color = MaxTimeColors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CodeCard(code: String, onCopy: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaxTimeShapes.medium)
            .background(MaxTimeColors.cardBg)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your Referral Code", color = MaxTimeColors.muted, fontSize = 12.5.sp)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaxTimeShapes.medium)
                .background(MaxTimeColors.bg2)
                .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                .padding(vertical = 16.dp, horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                code,
                color = MaxTimeColors.textStrong,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Box(Modifier.size(32.dp).clickable(onClick = onCopy), contentAlignment = Alignment.Center) {
                CopyGlyph(tint = MaxTimeColors.muted)
            }
        }
    }
}

@Composable
private fun CodeCardPlaceholder() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(MaxTimeShapes.medium)
            .background(MaxTimeColors.cardBg),
        contentAlignment = Alignment.Center
    ) {
        Text("Loading your code…", color = MaxTimeColors.muted, fontSize = 13.5.sp)
    }
}

@Composable
private fun CodeCardFailed(onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaxTimeShapes.medium)
            .background(MaxTimeColors.cardBg)
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Couldn't load your code", color = MaxTimeColors.muted, fontSize = 13.5.sp)
        Text(
            "Retry",
            color = MaxTimeColors.accent,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            modifier = Modifier.padding(top = 10.dp).clickable(onClick = onRetry)
        )
    }
}

/** See this file's top doc comment: functionally this opens the same native share sheet as
 *  every icon below, not a real per-app deep link. */
@Composable
private fun ShareReferralCodeButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaxTimeShapes.medium)
            .background(if (enabled) MaxTimeColors.accent else MaxTimeColors.accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShareGlyph(tint = MaxTimeColors.onAccent)
            Spacer(Modifier.width(10.dp))
            Text("Share Referral Code", color = MaxTimeColors.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun QuickShareIcon(label: String, background: Color, onClick: () -> Unit, icon: @Composable () -> Unit) {
    QuickShareIcon(label, Brush.linearGradient(listOf(background, background)), onClick, icon)
}

@Composable
private fun QuickShareIcon(label: String, background: Brush, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.height(6.dp))
        Text(label, color = MaxTimeColors.muted, fontSize = 10.5.sp)
    }
}

@Composable
private fun ShareGlyph(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = size.width * 0.1f)
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.14f, size.height * 0.42f),
            size = Size(size.width * 0.72f, size.height * 0.46f),
            cornerRadius = CornerRadius(size.width * 0.1f, size.width * 0.1f),
            style = stroke
        )
        drawLine(color = tint, start = Offset(size.width * 0.5f, size.height * 0.06f), end = Offset(size.width * 0.5f, size.height * 0.56f), strokeWidth = stroke.width)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.02f)
            lineTo(size.width * 0.3f, size.height * 0.26f)
            moveTo(size.width * 0.5f, size.height * 0.02f)
            lineTo(size.width * 0.7f, size.height * 0.26f)
        }
        drawPath(path, color = tint, style = stroke)
    }
}

@Composable
private fun CopyGlyph(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = size.width * 0.09f)
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
            size = Size(size.width * 0.62f, size.height * 0.62f),
            cornerRadius = CornerRadius(size.width * 0.1f, size.width * 0.1f),
            style = stroke
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.32f, size.height * 0.32f),
            size = Size(size.width * 0.6f, size.height * 0.6f),
            cornerRadius = CornerRadius(size.width * 0.1f, size.width * 0.1f),
            style = stroke
        )
    }
}

// Real WhatsApp glyph path, taken from the hypd-creator-android project's ic_whatsapp.xml
// (same brand mark WhatsApp itself ships) — ported to a Compose ImageVector instead of a
// VectorDrawable XML resource so it renders identically on iOS, not just Android.
private const val WHATSAPP_PATH =
    "M16.6,14C16.4,13.9 15.1,13.3 14.9,13.2C14.7,13.1 14.5,13.1 14.3,13.3C14.1,13.5 13.7,14.1 13.5,14.3C13.4,14.5 13.2,14.5 13,14.4C12.3,14.1 11.6,13.7 11,13.2C10.5,12.7 10,12.1 9.6,11.5C9.5,11.3 9.6,11.1 9.7,11C9.8,10.9 9.9,10.7 10.1,10.6C10.2,10.5 10.3,10.3 10.3,10.2C10.4,10.1 10.4,9.9 10.3,9.8C10.2,9.7 9.7,8.5 9.5,8C9.4,7.3 9.2,7.3 9,7.3H8.5C8.3,7.3 8,7.5 7.9,7.6C7.3,8.2 7,8.9 7,9.7C7.1,10.6 7.4,11.5 8,12.3C9.1,13.9 10.5,15.2 12.2,16C12.7,16.2 13.1,16.4 13.6,16.5C14.1,16.7 14.6,16.7 15.2,16.6C15.9,16.5 16.5,16 16.9,15.4C17.1,15 17.1,14.6 17,14.2L16.6,14ZM19.1,4.9C15.2,1 8.9,1 5,4.9C1.8,8.1 1.2,13 3.4,16.9L2,22L7.3,20.6C8.8,21.4 10.4,21.8 12,21.8C17.5,21.8 21.9,17.4 21.9,11.9C22,9.3 20.9,6.8 19.1,4.9ZM16.4,18.9C15.1,19.7 13.6,20.2 12,20.2C10.5,20.2 9.1,19.8 7.8,19.1L7.5,18.9L4.4,19.7L5.2,16.7L5,16.4C2.6,12.4 3.8,7.4 7.7,4.9C11.6,2.4 16.6,3.7 19,7.5C21.4,11.4 20.3,16.5 16.4,18.9Z"

@Composable
private fun WhatsAppGlyph(tint: Color) {
    val vector = remember(tint) {
        ImageVector.Builder(name = "WhatsApp", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(WHATSAPP_PATH).toNodes(), fill = SolidColor(tint))
            .build()
    }
    Image(imageVector = vector, contentDescription = null, modifier = Modifier.size(18.dp))
}

// Real Snapchat badge — full 3-layer icon (yellow rounded-square background, white ghost
// silhouette, black outline/detail), taken from hypd-creator-android's snapchat_ic.xml. Unlike
// WhatsApp/Instagram above, this renders as its own complete badge rather than a white glyph on
// this app's own colored circle — Snapchat's brand identity IS the yellow badge, so QuickShareIcon
// is given a transparent background for this one and the badge supplies its own fill.
private const val SNAPCHAT_BG_PATH =
    "M12.5,0L34.5,0A12,12 0,0 1,46.5 12L46.5,34A12,12 0,0 1,34.5 46L12.5,46A12,12 0,0 1,0.5 34L0.5,12A12,12 0,0 1,12.5 0z"
private const val SNAPCHAT_GHOST_PATH =
    "M20.886,13.5L24.284,12.75H25.591L29.25,14.5L31.08,18.25V21L33.432,20.75V21.5V22.75L31.864,23.25L31.341,24L32.648,26.5L36.046,28.25L35.261,29.25L32.909,30L32.386,31L31.864,31.75H28.989L27.682,32.25L24.807,33.75L22.977,33.25L20.886,32.25L19.318,31.75H17.227L16.443,31.25L15.659,30L14.352,29.75L13.307,29V28.25L14.614,27.5L16.966,25.75L17.489,24L15.398,22.75L15.659,21.5L16.443,20.75L18.011,21L18.795,16.5L20.886,13.5Z"
private const val SNAPCHAT_DETAIL_PATH =
    "M24.485,13.435C27.698,13.436 30.375,15.998 30.379,19.082C30.38,19.804 30.404,20.47 30.446,21.082C30.472,21.46 30.801,21.741 31.178,21.741C31.233,21.741 31.29,21.735 31.346,21.722L32.353,21.496C32.396,21.486 32.439,21.482 32.481,21.482C32.737,21.482 32.972,21.649 33.03,21.897C33.111,22.247 32.906,22.6 32.552,22.719L31.024,23.31C30.638,23.459 30.431,23.861 30.539,24.246C31.765,28.585 35.19,28.134 35.19,28.699C35.19,29.409 32.609,29.509 32.375,29.733C32.142,29.956 32.365,31.04 31.844,31.249C31.746,31.289 31.607,31.303 31.436,31.303C31.044,31.303 30.489,31.229 29.904,31.229C29.396,31.229 28.866,31.285 28.4,31.491C27.164,32.037 25.973,33.058 24.495,33.058C23.018,33.058 21.826,32.037 20.59,31.491C20.124,31.285 19.594,31.229 19.087,31.229C18.502,31.229 17.947,31.303 17.555,31.303C17.384,31.303 17.245,31.289 17.146,31.249C16.625,31.04 16.848,29.956 16.615,29.733C16.382,29.509 13.801,29.409 13.801,28.699C13.801,28.134 17.225,28.585 18.451,24.246C18.56,23.861 18.353,23.459 17.967,23.31L16.439,22.719C16.084,22.6 15.88,22.247 15.961,21.897C16.018,21.649 16.254,21.482 16.509,21.482C16.552,21.482 16.595,21.486 16.638,21.496L17.644,21.722C17.701,21.735 17.758,21.741 17.813,21.741C18.19,21.741 18.518,21.46 18.545,21.082C18.587,20.469 18.611,19.804 18.611,19.082C18.615,15.998 21.271,13.436 24.485,13.435ZM24.485,12.212H24.484C23.525,12.212 22.59,12.396 21.707,12.76C20.859,13.109 20.095,13.607 19.438,14.239C18.782,14.871 18.265,15.604 17.903,16.419C17.526,17.266 17.334,18.162 17.333,19.08C17.333,19.531 17.323,19.969 17.304,20.389L16.929,20.304C16.792,20.274 16.651,20.258 16.509,20.258C16.102,20.258 15.7,20.39 15.377,20.629C15.041,20.879 14.805,21.235 14.713,21.631C14.494,22.575 15.038,23.527 15.981,23.862L17.103,24.295C16.36,26.478 14.991,26.896 14.068,27.178C13.824,27.253 13.612,27.317 13.411,27.413C12.609,27.794 12.523,28.439 12.523,28.699C12.523,29.197 12.769,29.651 13.216,29.978C13.438,30.141 13.714,30.275 14.058,30.389C14.527,30.543 15.072,30.641 15.514,30.72C15.535,30.877 15.566,31.045 15.619,31.215C15.839,31.921 16.301,32.237 16.651,32.377C16.983,32.511 17.326,32.526 17.555,32.526C17.778,32.526 18.015,32.51 18.266,32.492C18.535,32.473 18.813,32.453 19.087,32.453C19.512,32.453 19.829,32.502 20.054,32.601C20.364,32.739 20.693,32.924 21.041,33.119C22.011,33.664 23.109,34.282 24.495,34.282C25.882,34.282 26.98,33.664 27.949,33.119C28.298,32.924 28.626,32.739 28.937,32.601C29.162,32.501 29.479,32.453 29.904,32.453C30.178,32.453 30.456,32.473 30.725,32.492C30.976,32.51 31.212,32.526 31.436,32.526C31.665,32.526 32.008,32.511 32.34,32.377C32.689,32.237 33.152,31.921 33.371,31.215C33.424,31.045 33.456,30.877 33.477,30.72C33.919,30.641 34.463,30.543 34.933,30.389C35.277,30.275 35.553,30.141 35.775,29.978C36.222,29.651 36.468,29.197 36.468,28.699C36.468,28.439 36.382,27.794 35.579,27.413C35.378,27.317 35.167,27.253 34.922,27.178C34,26.896 32.631,26.478 31.888,24.296L33.009,23.862C33.952,23.527 34.496,22.575 34.278,21.631C34.186,21.235 33.95,20.879 33.613,20.629C33.291,20.39 32.889,20.258 32.481,20.258C32.34,20.258 32.199,20.274 32.061,20.304L31.687,20.388C31.668,19.969 31.658,19.531 31.658,19.08C31.656,18.161 31.463,17.265 31.083,16.416C30.719,15.602 30.199,14.869 29.54,14.237C28.88,13.605 28.115,13.108 27.265,12.759C26.379,12.396 25.444,12.212 24.485,12.212Z"

@Composable
private fun SnapchatGlyph() {
    val vector = remember {
        ImageVector.Builder(name = "Snapchat", defaultWidth = 48.dp, defaultHeight = 48.dp, viewportWidth = 47f, viewportHeight = 46f)
            .addPath(pathData = PathParser().parsePathString(SNAPCHAT_BG_PATH).toNodes(), fill = SolidColor(Color(0xFFFFF853)))
            .addPath(pathData = PathParser().parsePathString(SNAPCHAT_GHOST_PATH).toNodes(), fill = SolidColor(Color.White))
            .addPath(pathData = PathParser().parsePathString(SNAPCHAT_DETAIL_PATH).toNodes(), fill = SolidColor(Color(0xFF050505)))
            .build()
    }
    Image(imageVector = vector, contentDescription = null, modifier = Modifier.size(48.dp))
}

// Real Instagram glyph paths (rounded-square outline, camera-lens ring, flash dot), taken from
// hypd-creator-android's ic_instagram.xml — the XML fills these with Instagram's own gradient
// since it's normally drawn on a plain background, but here it sits on this app's own gradient
// circle (see QuickShareIcon's Instagram call site), so it's rendered in solid white instead —
// the standard "white glyph on a colored badge" treatment already used for every other icon here.
private const val INSTAGRAM_OUTLINE_PATH =
    "M16.5,0H7.5C5.511,0 3.603,0.79 2.197,2.197C0.79,3.603 0,5.511 0,7.5L0,16.5C0,18.489 0.79,20.397 2.197,21.803C3.603,23.21 5.511,24 7.5,24H16.5C18.489,24 20.397,23.21 21.803,21.803C23.21,20.397 24,18.489 24,16.5V7.5C24,5.511 23.21,3.603 21.803,2.197C20.397,0.79 18.489,0 16.5,0V0ZM21.75,16.5C21.75,19.395 19.395,21.75 16.5,21.75H7.5C4.605,21.75 2.25,19.395 2.25,16.5V7.5C2.25,4.605 4.605,2.25 7.5,2.25H16.5C19.395,2.25 21.75,4.605 21.75,7.5V16.5Z"
private const val INSTAGRAM_LENS_PATH =
    "M12,6C10.409,6 8.883,6.632 7.757,7.757C6.632,8.883 6,10.409 6,12C6,13.591 6.632,15.117 7.757,16.243C8.883,17.368 10.409,18 12,18C13.591,18 15.117,17.368 16.243,16.243C17.368,15.117 18,13.591 18,12C18,10.409 17.368,8.883 16.243,7.757C15.117,6.632 13.591,6 12,6ZM12,15.75C11.006,15.749 10.053,15.353 9.35,14.65C8.647,13.947 8.251,12.994 8.25,12C8.25,9.932 9.933,8.25 12,8.25C14.067,8.25 15.75,9.932 15.75,12C15.75,14.067 14.067,15.75 12,15.75Z"
private const val INSTAGRAM_FLASH_PATH =
    "M18.45,6.35C18.892,6.35 19.25,5.992 19.25,5.55C19.25,5.108 18.892,4.751 18.45,4.751C18.008,4.751 17.65,5.108 17.65,5.55C17.65,5.992 18.008,6.35 18.45,6.35Z"

@Composable
private fun InstagramGlyph() {
    val vector = remember {
        ImageVector.Builder(name = "Instagram", defaultWidth = 18.dp, defaultHeight = 18.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(INSTAGRAM_OUTLINE_PATH).toNodes(), fill = SolidColor(Color.White))
            .addPath(pathData = PathParser().parsePathString(INSTAGRAM_LENS_PATH).toNodes(), fill = SolidColor(Color.White))
            .addPath(pathData = PathParser().parsePathString(INSTAGRAM_FLASH_PATH).toNodes(), fill = SolidColor(Color.White))
            .build()
    }
    Image(imageVector = vector, contentDescription = null, modifier = Modifier.size(18.dp))
}

@Composable
private fun MoreDotsGlyph(tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val r = size.width * 0.07f
        val y = size.height / 2f
        listOf(0.22f, 0.5f, 0.78f).forEach { fx ->
            drawCircle(color = tint, radius = r, center = Offset(size.width * fx, y))
        }
    }
}
