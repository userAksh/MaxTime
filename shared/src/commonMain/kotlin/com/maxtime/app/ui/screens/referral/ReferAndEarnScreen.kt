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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.compose.resources.imageResource
import com.maxtime.app.analytics.AnalyticsEvent
import com.maxtime.app.analytics.AnalyticsTracker
import com.maxtime.app.data.firebase.ReferralCodeInfo
import com.maxtime.app.data.firebase.ReferralFunctions
import com.maxtime.app.data.share.encodeToPngBytes
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
 * - Copy Code / More / the big "Share Referral Code" button all open the same cross-platform
 *   native share sheet ([rememberShareLauncher.shareImage]). WhatsApp gets its own link-only path
 *   (see shareToWhatsApp below — WhatsApp's share extension never renders a link preview when an
 *   image is attached to the same share). Instagram is the
 *   one dedicated deep-link integration, going straight to Story on both platforms — see
 *   [ShareLauncher.shareToInstagram]/[ShareLauncher.shareToInstagramStory]. Direct-message sharing
 *   was investigated and ruled out: on-device diagnostics confirmed Instagram's `direct-inbox`
 *   scheme opens a blank inbox with no image/contact attached, since Meta never published a
 *   Direct equivalent of the Stories sticker API.
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

/**
 * Records the share-card directly into [graphicsLayer] every frame via raw [DrawScope] drawing —
 * NOT by hosting a `ShareCardTemplate` composable and redirecting its `drawContent()` into the
 * recorder. That was tried first (both via `Modifier.drawWithContent` and this exact low-level
 * `Modifier.Node`/`DrawModifierNode` API) and confirmed, via a controlled isolation test on a
 * real iOS device, to always capture nothing: swapping `this@draw.drawContent()` for a plain
 * `drawRect(Color.Red)` in the exact same `record{}` block came through as a perfect solid red
 * pixel, proving `record()`/`toImageBitmap()`/`readPixels()` all work correctly and the bug is
 * specifically that `drawContent()` never redirects into this recording on this Skia backend.
 * Direct `DrawScope` primitives are the one technique with actual on-device proof of working, so
 * the whole card is drawn procedurally instead — real trade-off: this can't be laid out with
 * normal Box/Column/Text like every other screen, positions are computed by hand below.
 */
// FULL_BLEED = opaque, full-bleed 9:16 render for generic "Share via"/WhatsApp (those pipelines
// have no sticker concept and always flatten any transparency to a solid color, so matching the
// destination's own canvas ratio is the only way to avoid a letterboxed look there).
// STICKER = a real transparent PNG with genuine rounded corners (alpha=0 outside the rounded
// rect) — used only by iOS's pasteboard-based Instagram Story deep link (see
// ShareLauncher.ios.kt's shareToInstagramStory), which composites it over a
// background Instagram generates itself via the "stickerImage" pasteboard key.
// INSTAGRAM_SHEET = opaque card with a baked-in dark margin, corner radius painted directly into
// the pixels (no real alpha transparency) — used when Instagram is picked from the plain
// native/generic share sheet, and by Android's dedicated Instagram button (its share-receiver
// activity has no sticker/pasteboard concept and flattens everything it receives regardless) —
// see drawShareCardForInstagramSheet's doc comment for why this needs its own render at all.
private enum class ShareCardVariant { FULL_BLEED, STICKER, INSTAGRAM_SHEET }

private fun Modifier.recordShareCard(
    graphicsLayer: GraphicsLayer,
    textMeasurer: TextMeasurer,
    highfiveImage: ImageBitmap,
    code: String,
    refereePercent: Int,
    referrerPercent: Int,
    variant: ShareCardVariant
): Modifier = this then RecordShareCardElement(graphicsLayer, textMeasurer, highfiveImage, code, refereePercent, referrerPercent, variant)

private data class RecordShareCardElement(
    private val graphicsLayer: GraphicsLayer,
    private val textMeasurer: TextMeasurer,
    private val highfiveImage: ImageBitmap,
    private val code: String,
    private val refereePercent: Int,
    private val referrerPercent: Int,
    private val variant: ShareCardVariant
) : ModifierNodeElement<RecordShareCardNode>() {
    override fun create() = RecordShareCardNode(graphicsLayer, textMeasurer, highfiveImage, code, refereePercent, referrerPercent, variant)
    override fun update(node: RecordShareCardNode) {
        node.graphicsLayer = graphicsLayer
        node.textMeasurer = textMeasurer
        node.highfiveImage = highfiveImage
        node.code = code
        node.refereePercent = refereePercent
        node.referrerPercent = referrerPercent
        node.variant = variant
    }
}

private class RecordShareCardNode(
    var graphicsLayer: GraphicsLayer,
    var textMeasurer: TextMeasurer,
    var highfiveImage: ImageBitmap,
    var code: String,
    var refereePercent: Int,
    var referrerPercent: Int,
    var variant: ShareCardVariant
) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        graphicsLayer.record(
            density = this,
            layoutDirection = layoutDirection,
            size = IntSize(size.width.toInt(), size.height.toInt())
        ) {
            when (variant) {
                ShareCardVariant.FULL_BLEED -> drawShareCard(highfiveImage, textMeasurer, code, refereePercent, referrerPercent)
                ShareCardVariant.STICKER -> drawShareSticker(highfiveImage, textMeasurer, code, refereePercent, referrerPercent)
                ShareCardVariant.INSTAGRAM_SHEET -> drawShareCardForInstagramSheet(highfiveImage, textMeasurer, code, refereePercent, referrerPercent)
            }
        }
        drawLayer(graphicsLayer)
    }
}

/** Procedural re-implementation of the share card's visuals — see [recordShareCard]'s doc comment
 *  for why this isn't a normal Box/Column/Text composable. Colors/sizes match what the card
 *  looked like as a composable as closely as hand-computed positions reasonably allow; expect
 *  this to need the same kind of visual nudging every other screen in this app got, now done by
 *  adjusting the numbers below instead of Modifier chains. */
private fun DrawScope.drawShareCard(
    highfiveImage: ImageBitmap,
    textMeasurer: TextMeasurer,
    code: String,
    refereePercent: Int,
    referrerPercent: Int
) {
    // The captured canvas is now sized to Instagram/WhatsApp Stories' own 9:16 aspect ratio (see
    // the Box in ReferAndEarnScreen that hosts this modifier) and this background fill covers it
    // fully, edge to edge — no margin, no inset card. Earlier versions kept the on-screen 340x400
    // (~4:5) card shape for the SHARED image too, so Instagram centered that image on its own
    // black 9:16 canvas and letterboxed the rest with black — that black bar was Instagram's own
    // canvas showing through, not anything drawn here, which is what actually produced the
    // "floating rounded card in a rectangular container" look. Matching the destination's own
    // aspect ratio removes the letterboxing entirely, which is the only real fix for that look.
    drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF0E2413), Color(0xFF040B06))))

    val padding = 28.dp.toPx()
    // Both Instagram's and WhatsApp Status's own UI chrome (profile row, reply bar, progress
    // bars) overlays roughly the top/bottom ~13% of the canvas regardless of what's in the image
    // underneath — keeping content clear of that band is standard Stories-design practice, not
    // specific to this app.
    val safeZone = size.height * 0.13f

    val imgWidth = size.width * 0.62f
    val imgHeight = imgWidth * (highfiveImage.height.toFloat() / highfiveImage.width.toFloat())
    val imgTop = safeZone + 24.dp.toPx()
    drawImage(
        image = highfiveImage,
        dstOffset = IntOffset(
            ((size.width - imgWidth) / 2f).toInt(),
            imgTop.toInt()
        ),
        dstSize = IntSize(imgWidth.toInt(), imgHeight.toInt())
    )

    var cursorY = imgTop + imgHeight + 36.dp.toPx()

    val headingLayout = textMeasurer.measure(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) { append("Let's do more ") }
            withStyle(SpanStyle(color = MaxTimeColors.accent, fontWeight = FontWeight.ExtraBold)) { append("together.") }
        },
        style = TextStyle(fontSize = 26.sp, lineHeight = 30.sp),
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (size.width - padding * 2).toInt())
    )
    drawText(headingLayout, topLeft = Offset((size.width - headingLayout.size.width) / 2f, cursorY))
    cursorY += headingLayout.size.height + 14.dp.toPx()

    val subtitleLayout = textMeasurer.measure(
        buildAnnotatedString {
            append("Join me on MaxTime and get ")
            withStyle(SpanStyle(color = MaxTimeColors.accent, fontWeight = FontWeight.Bold)) { append("$refereePercent% off") }
            append(" your first booking. I'll get ")
            withStyle(SpanStyle(color = MaxTimeColors.accent, fontWeight = FontWeight.Bold)) { append("$referrerPercent%") }
            append(" too!")
        },
        style = TextStyle(color = Color(0xFFD8E0D8), fontSize = 14.sp, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (size.width - padding * 2).toInt())
    )
    drawText(subtitleLayout, topLeft = Offset((size.width - subtitleLayout.size.width) / 2f, cursorY))

    val footerHeight = 110.dp.toPx()
    val footerLeft = padding
    val footerWidth = size.width - padding * 2
    val footerTop = size.height - safeZone - 24.dp.toPx() - footerHeight
    drawRoundRect(
        color = Color(0x1AFFFFFF),
        topLeft = Offset(footerLeft, footerTop),
        size = Size(footerWidth, footerHeight),
        cornerRadius = CornerRadius(20.dp.toPx())
    )

    val footerPad = 18.dp.toPx()
    var footerCursorY = footerTop + footerPad
    val footerContentLeft = footerLeft + footerPad

    val maxTimeLayout = textMeasurer.measure(
        AnnotatedString("MaxTime"),
        style = TextStyle(color = MaxTimeColors.accent, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    )
    drawText(maxTimeLayout, topLeft = Offset(footerContentLeft, footerCursorY))
    footerCursorY += maxTimeLayout.size.height + 2.dp.toPx()

    val taglineLayout = textMeasurer.measure(
        AnnotatedString("Real People. Active Lives."),
        style = TextStyle(color = Color(0xFFB8C4B8), fontSize = 11.sp)
    )
    drawText(taglineLayout, topLeft = Offset(footerContentLeft, footerCursorY))

    val codeChipLayout = textMeasurer.measure(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFFB8C4B8))) { append("Use code: ") }
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) { append(code) }
        },
        style = TextStyle(fontSize = 12.sp)
    )
    val chipPadH = 12.dp.toPx()
    val chipPadV = 8.dp.toPx()
    val chipSize = Size(codeChipLayout.size.width + chipPadH * 2, codeChipLayout.size.height + chipPadV * 2)
    val chipLeft = footerLeft + footerWidth - footerPad - chipSize.width
    val chipTop = footerTop + (footerHeight - chipSize.height) / 2f
    drawRoundRect(
        color = Color(0x33FFFFFF),
        topLeft = Offset(chipLeft, chipTop),
        size = chipSize,
        cornerRadius = CornerRadius(10.dp.toPx()),
        style = Stroke(width = 1.dp.toPx())
    )
    drawText(codeChipLayout, topLeft = Offset(chipLeft + chipPadH, chipTop + chipPadV))
}

/** A real transparent PNG with genuine rounded corners — everything outside the rounded rect is
 *  left at the canvas's native alpha=0. Used two ways: standalone (ShareCardVariant.STICKER), for
 *  iOS's pasteboard-based Instagram Story deep link (see ShareLauncher.ios.kt's
 *  shareToInstagramStory), which needs the real transparency for Instagram
 *  to composite it over its own generated background; and as the content painted inside
 *  [drawShareCardForInstagramSheet]'s larger opaque canvas, for destinations that flatten
 *  transparency regardless. [drawShareCard] (opaque, full-bleed) separately covers generic
 *  "Share via"/WhatsApp. */
private fun DrawScope.drawShareSticker(
    highfiveImage: ImageBitmap,
    textMeasurer: TextMeasurer,
    code: String,
    refereePercent: Int,
    referrerPercent: Int
) {
    val corner = CornerRadius(28.dp.toPx())
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF0E2413), Color(0xFF040B06))),
        size = size,
        cornerRadius = corner
    )
    drawRoundRect(
        color = MaxTimeColors.accent.copy(alpha = 0.35f),
        size = size,
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx())
    )

    val padding = 26.dp.toPx()

    val imgWidth = 150.dp.toPx()
    val imgHeight = imgWidth * (highfiveImage.height.toFloat() / highfiveImage.width.toFloat())
    drawImage(
        image = highfiveImage,
        dstOffset = IntOffset(
            (size.width - imgWidth - padding + 6.dp.toPx()).toInt(),
            (padding + 22.dp.toPx()).toInt()
        ),
        dstSize = IntSize(imgWidth.toInt(), imgHeight.toInt())
    )

    var cursorY = padding
    val contentLeft = padding

    val headingLayout = textMeasurer.measure(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) { append("Let's\n") }
            withStyle(SpanStyle(color = MaxTimeColors.accent, fontWeight = FontWeight.ExtraBold)) { append("do more\n") }
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) { append("together.") }
        },
        style = TextStyle(fontSize = 28.sp, lineHeight = 30.sp)
    )
    drawText(headingLayout, topLeft = Offset(contentLeft, cursorY))
    cursorY += headingLayout.size.height + 16.dp.toPx()

    val subtitleLayout = textMeasurer.measure(
        buildAnnotatedString {
            append("Join me on MaxTime and get\n")
            withStyle(SpanStyle(color = MaxTimeColors.accent, fontWeight = FontWeight.Bold)) { append("$refereePercent% off") }
            append(" your first booking.\nI'll get ")
            withStyle(SpanStyle(color = MaxTimeColors.accent, fontWeight = FontWeight.Bold)) { append("$referrerPercent%") }
            append(" too!")
        },
        style = TextStyle(color = Color(0xFFD8E0D8), fontSize = 13.sp, lineHeight = 18.sp),
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (size.width - padding * 2).toInt())
    )
    drawText(subtitleLayout, topLeft = Offset(contentLeft, cursorY))

    val footerHeight = 96.dp.toPx()
    val footerTop = size.height - padding - footerHeight
    drawRoundRect(
        color = Color(0x1AFFFFFF),
        topLeft = Offset(contentLeft, footerTop),
        size = Size(size.width - padding * 2, footerHeight),
        cornerRadius = CornerRadius(18.dp.toPx())
    )

    val footerPad = 14.dp.toPx()
    var footerCursorY = footerTop + footerPad
    val footerLeft = contentLeft + footerPad

    val maxTimeLayout = textMeasurer.measure(
        AnnotatedString("MaxTime"),
        style = TextStyle(color = MaxTimeColors.accent, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    )
    drawText(maxTimeLayout, topLeft = Offset(footerLeft, footerCursorY))
    footerCursorY += maxTimeLayout.size.height

    val taglineLayout = textMeasurer.measure(
        AnnotatedString("Real People. Active Lives."),
        style = TextStyle(color = Color(0xFFB8C4B8), fontSize = 9.5.sp)
    )
    drawText(taglineLayout, topLeft = Offset(footerLeft, footerCursorY))
    footerCursorY += taglineLayout.size.height + 8.dp.toPx()

    val codeChipLayout = textMeasurer.measure(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFFB8C4B8))) { append("Use code: ") }
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) { append(code) }
        },
        style = TextStyle(fontSize = 10.sp)
    )
    val chipPadH = 10.dp.toPx()
    val chipPadV = 6.dp.toPx()
    val chipSize = Size(codeChipLayout.size.width + chipPadH * 2, codeChipLayout.size.height + chipPadV * 2)
    drawRoundRect(
        color = Color(0x33FFFFFF),
        topLeft = Offset(footerLeft, footerCursorY),
        size = chipSize,
        cornerRadius = CornerRadius(10.dp.toPx()),
        style = Stroke(width = 1.dp.toPx())
    )
    drawText(codeChipLayout, topLeft = Offset(footerLeft + chipPadH, footerCursorY + chipPadV))
}

/** Instagram, when picked from the plain native/generic share sheet (as opposed to the dedicated
 *  Instagram sticker button), receives a normal opaque image via its share extension — it has no
 *  access to the sticker pasteboard API there, so it just imports whatever image it gets and
 *  flattens/scales it as an ordinary attachment (see this file's earlier design-decision comment
 *  for why that's a hard platform limit, not a bug in our code). Relying on Instagram's own
 *  scaling to reproduce the "floating rounded card on a dark backdrop" look is exactly what
 *  earlier produced the unpredictable letterboxing that [drawShareCard]'s full-bleed redesign
 *  fixed — so instead of hoping any receiving app letterboxes this correctly, the margin is baked
 *  directly into the exported pixels: this draws the exact same proven [drawShareSticker] card
 *  (unchanged, still 340x420dp) centered via [inset] on a larger solid-filled canvas, so it always
 *  looks the same no matter what app or preview surface displays it. */
private fun DrawScope.drawShareCardForInstagramSheet(
    highfiveImage: ImageBitmap,
    textMeasurer: TextMeasurer,
    code: String,
    refereePercent: Int,
    referrerPercent: Int
) {
    drawRect(color = Color(0xFF040B06))
    val cardWidth = 340.dp.toPx()
    val cardHeight = 420.dp.toPx()
    val marginH = (size.width - cardWidth) / 2f
    val marginV = (size.height - cardHeight) / 2f
    inset(left = marginH, top = marginV, right = marginH, bottom = marginV) {
        drawShareSticker(highfiveImage, textMeasurer, code, refereePercent, referrerPercent)
    }
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
    LaunchedEffect(Unit) { AnalyticsTracker.track(AnalyticsEvent.ReferAndEarnViewed) }

    // The actual share-card pixels, drawn procedurally (see drawShareCard's doc comment for why)
    // into this graphicsLayer every frame — real per-user code/percentages, not a bundled
    // flattened image. Only converted to real bytes — via toImageBitmap() + encodeToPngBytes() —
    // the moment someone taps a share action, not continuously.
    val graphicsLayer = rememberGraphicsLayer()
    // Separate layer/canvas for the "opaque card with baked-in margin" render — see
    // drawShareCardForInstagramSheet's doc comment. Used both when Instagram ends up being the
    // destination someone picks from the plain native share sheet, and by Android's dedicated
    // Instagram button (shareToInstagram).
    val instagramSheetGraphicsLayer = rememberGraphicsLayer()
    // Separate again — the real transparent sticker render, used only by iOS's pasteboard-based
    // Instagram Story deep link (shareToInstagramStory).
    val stickerGraphicsLayer = rememberGraphicsLayer()
    val textMeasurer = rememberTextMeasurer()
    val highfiveImage = imageResource(Res.drawable.refer_earn_highfive)

    suspend fun renderShareCardPng(): ByteArray = graphicsLayer.toImageBitmap().encodeToPngBytes()
    suspend fun renderInstagramSheetCardPng(): ByteArray = instagramSheetGraphicsLayer.toImageBitmap().encodeToPngBytes()
    suspend fun renderShareStickerPng(): ByteArray = stickerGraphicsLayer.toImageBitmap().encodeToPngBytes()

    val state = uiState
    val refereePercent = (state as? ReferAndEarnUiState.Loaded)?.info?.refereeDiscountPercent ?: FALLBACK_REFEREE_PERCENT
    val refereeCap = (state as? ReferAndEarnUiState.Loaded)?.info?.refereeDiscountCapAmount ?: FALLBACK_REFEREE_CAP
    val referrerPercent = (state as? ReferAndEarnUiState.Loaded)?.info?.referrerDiscountPercent ?: FALLBACK_REFERRER_PERCENT
    val referrerCap = (state as? ReferAndEarnUiState.Loaded)?.info?.referrerDiscountCapAmount ?: FALLBACK_REFERRER_CAP
    val code = (state as? ReferAndEarnUiState.Loaded)?.info?.code

    fun referralText(code: String, refereePercent: Int, refereeCap: Int): String =
        "Join me on MaxTime! Use my code $code to get $refereePercent% off your first booking " +
            "(up to ₹$refereeCap) — once you complete it, I get a reward too 🎉"

    // The real, deployed landing page behind every referral share link — see
    // functions/src/referralLandingPage.ts (same "https://maxtime.in" domain, duplicated here
    // since this is a genuinely separate codebase/runtime, same as other cross-repo constants in
    // this app). Only consumed by shareToWhatsApp below now — see its doc comment for why WhatsApp
    // needs the raw link and nothing else.
    fun referralLink(code: String): String = "https://maxtime.in/r/$code"

    fun shareCard(caption: String? = null) {
        if (code == null) return
        AnalyticsTracker.track(AnalyticsEvent.ReferralCodeShared(channel = "share_sheet"))
        scope.launch {
            val bytes = renderShareCardPng()
            // Always rendered alongside the normal card, not just for the "More"/big
            // button path — Instagram can be picked from either call site's share sheet.
            val instagramBytes = renderInstagramSheetCardPng()
            shareLauncher.shareImage(bytes, caption = caption, instagramBytes = instagramBytes)
        }
    }

    // WhatsApp gets the link alone, with no image attached — on-device testing showed WhatsApp's
    // share extension never renders its own Open-Graph preview card for the link when an image is
    // also part of the same share (even though the landing page's OG tags are confirmed correct),
    // apparently treating the attached image as "the" visual and skipping the link unfurl
    // entirely. Dropping the image is what lets WhatsApp recognize the URL as the sole content and
    // fetch/render its own brand-mark + title + description card for it.
    fun shareToWhatsApp() {
        val c = code ?: return
        AnalyticsTracker.track(AnalyticsEvent.ReferralCodeShared(channel = "whatsapp"))
        shareLauncher.shareLink(referralLink(c), targetPackage = "com.whatsapp")
    }

    // Instagram's custom button goes straight to Story on both platforms — Direct/Message was
    // removed after on-device diagnostics confirmed `instagram://direct-inbox` only opens a blank
    // inbox with no image/contact attached (Meta never exposed a Direct equivalent of the Stories
    // sticker API); see git history for the ruled-out destination-picker UI this replaced.
    fun onInstagramIconClick() {
        if (code == null) return
        AnalyticsTracker.track(AnalyticsEvent.ReferralCodeShared(channel = "instagram_story"))
        scope.launch {
            if (shareLauncher.instagramSharePickerIsNative) {
                // Android: no sticker-only API — the shared Instagram destination picker
                // (Story/Post/Reel/Message) is Instagram's own, reached via one Intent.
                shareLauncher.shareToInstagram(renderInstagramSheetCardPng())
            } else {
                shareLauncher.shareToInstagramStory(renderShareStickerPng())
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold {
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
                            AnalyticsTracker.track(AnalyticsEvent.ReferralCodeCopied)
                        }
                    )
                    is ReferAndEarnUiState.Failed -> CodeCardFailed(onRetry = ::load)
                }
            }

            Column(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding)) {
                ShareReferralCodeButton(
                    enabled = code != null,
                    onClick = { code?.let { shareCard(caption = referralText(it, refereePercent, refereeCap)) } }
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QuickShareIcon(
                        label = "WhatsApp",
                        background = Color(0xFF2FCE5B),
                        onClick = { shareToWhatsApp() }
                    ) { WhatsAppGlyph(tint = Color.White) }
                    QuickShareIcon(
                        label = "Instagram",
                        background = Brush.linearGradient(
                            listOf(Color(0xFFFEDA75), Color(0xFFD62976), Color(0xFF4F5BD5))
                        ),
                        // Android: jumps straight into Instagram's own Story/Post/Reel/Message
                        // picker. iOS: opens our own destination picker first, since no
                        // equivalent single-call mechanism exists there — see onInstagramIconClick.
                        onClick = { onInstagramIconClick() }
                    ) { InstagramGlyph() }
                    QuickShareIcon(
                        label = "Copy Code",
                        background = MaxTimeColors.cardBg,
                        onClick = {
                            code?.let {
                                clipboard.setText(AnnotatedString(it))
                                toastMessage = "Code copied"
                                AnalyticsTracker.track(AnalyticsEvent.ReferralCodeCopied)
                            }
                        }
                    ) { CopyGlyph(tint = MaxTimeColors.textStrong) }
                    QuickShareIcon(
                        label = "More",
                        background = MaxTimeColors.cardBg,
                        onClick = { code?.let { shareCard(caption = referralText(it, refereePercent, refereeCap)) } }
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

            // See drawShareCard's doc comment for the confirmed root cause this works around
            // (drawContent() doesn't redirect into graphicsLayer.record() on this backend) — this
            // draws the whole card procedurally instead of hosting a composable to capture. The
            // opaque cover on top is still needed since this Box otherwise renders its real
            // content (now correctly!) in this normal, non-hidden spot in the layout.
            // Pushed off-screen horizontally rather than covered with an opaque box — the real
            // screen background (MaxTimeColors.screenGradient) is a radial gradient, not a flat
            // color, so a flat cover could never seamlessly match it at this scroll position
            // (that mismatch was the visible dark seam in the earlier version). This keeps the
            // card's real, correctly-sized footprint — still needed for the recording to work —
            // while never actually painting anything inside the visible viewport at all, so
            // there's no color-matching problem to solve in the first place.
            // 360x640dp matches WhatsApp Status's own 9:16 canvas ratio (and, on a typical
            // 3x-density phone, rasterizes to ~1080x1920px — its native resolution) — this is
            // still correct for the generic "Share via"/WhatsApp path, which has no sticker
            // concept and always flattens transparency, so matching the destination canvas is the
            // only way to avoid letterboxing there.
            Box(
                Modifier
                    .width(360.dp)
                    .height(640.dp)
                    .offset(x = 20000.dp)
                    .recordShareCard(graphicsLayer, textMeasurer, highfiveImage, code ?: "", refereePercent, referrerPercent, ShareCardVariant.FULL_BLEED)
            )
            // The opaque, corner-radius-baked-in Instagram render — see
            // drawShareCardForInstagramSheet's doc comment. Used for whenever Instagram is picked
            // from the plain native share sheet, and by Android's dedicated Instagram button
            // (shareToInstagram). 420x580dp gives the 340x420dp card a ~40dp horizontal / ~80dp
            // vertical margin, baked into the exported pixels themselves.
            Box(
                Modifier
                    .width(420.dp)
                    .height(580.dp)
                    .offset(x = 20000.dp, y = 1200.dp)
                    .recordShareCard(instagramSheetGraphicsLayer, textMeasurer, highfiveImage, code ?: "", refereePercent, referrerPercent, ShareCardVariant.INSTAGRAM_SHEET)
            )
            // The real transparent sticker render — used only by iOS's pasteboard-based Instagram
            // Story deep link (shareToInstagramStory). A real card
            // shape (not 9:16), since Instagram positions/scales a sticker itself.
            Box(
                Modifier
                    .width(340.dp)
                    .height(420.dp)
                    .offset(x = 20000.dp, y = 2000.dp)
                    .recordShareCard(stickerGraphicsLayer, textMeasurer, highfiveImage, code ?: "", refereePercent, referrerPercent, ShareCardVariant.STICKER)
            )
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
