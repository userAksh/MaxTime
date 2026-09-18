package com.maxtime.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeType
import maxtime.shared.generated.resources.Res
import maxtime.shared.generated.resources.referral_highfive
import org.jetbrains.compose.resources.painterResource

/**
 * V1 referral code entry — MaxTime_Claude_Product_Design_Context.docx §7/§11, visuals per the
 * approved reference (2026-09-18 design handoff). Purely presentational and state-driven (no
 * Firebase/network calls in here at all — see ReferralScreen in OnboardingScreens.kt for the
 * actual redeemReferralCode/getMyReferralSummary wiring), so the whole visual treatment can keep
 * changing independently of that logic.
 *
 * Deliberate deviations from the visual reference, agreed with the product owner rather than
 * invented here:
 * - The Valid state shows the referral CODE, not the referrer's name/photo/"friend since" card —
 *   Firestore rules only let a user read their own profile doc, so another user's display name
 *   isn't available client-side today. Not adding a public-profile lookup for this.
 * - "Contact Support" (Invalid state) renders as styled text only — no support flow exists yet
 *   anywhere in the app to link it to.
 * - The onboarding progress shown here is whatever step/total ReferralScreen's ScreenScaffold
 *   topBar actually is (3 of 5) — not the reference's "Step 3 of 4", which doesn't match the
 *   real 5-screen onboarding flow (AgeGate/Auth/Referral/Profile/Interests).
 * - The illustration is the supplied static artwork — no high-five animation yet (separate work).
 */
enum class ReferralCodePhase { INPUT, LOADING, VALID, INVALID }

data class ReferralProgramInfo(
    val refereeDiscountPercent: Int,
    val refereeDiscountCapAmount: Int,
    val referrerDiscountPercent: Int,
)

@Composable
fun ReferralCodeCard(
    phase: ReferralCodePhase,
    code: String,
    onCodeChange: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    programInfo: ReferralProgramInfo? = null,
    appliedCode: String? = null,
    errorMessage: String? = null,
) {
    // No horizontal padding at this top level — unlike every other element on this screen, the
    // illustration (INPUT/LOADING) needs to bleed edge-to-edge per the reference, not sit inset
    // the same 24dp as the text/button/field. Everything else gets that inset individually via
    // paddedSection() below instead of once for the whole card.
    Column(modifier.fillMaxWidth()) {
        // Badge-bearing states (VALID/INVALID) lead with the badge, matching the reference's
        // screens 3/4. The plain input states (INPUT/LOADING) put heading+subtext FIRST and the
        // illustration below them instead — matching screen 1, not the badge screens' order.
        when (phase) {
            ReferralCodePhase.VALID -> paddedSection { ReferralStatusBadge(valid = true) }
            ReferralCodePhase.INVALID -> paddedSection { ReferralStatusBadge(valid = false) }
            ReferralCodePhase.INPUT, ReferralCodePhase.LOADING -> Unit
        }

        Spacer(20.dp)

        // Badge states (VALID/INVALID) center this text block, matching the reference's
        // celebration/error screens — INPUT/LOADING stay left-aligned like the rest of this
        // card's copy (that's the layout already confirmed against the reference separately).
        val centerText = phase == ReferralCodePhase.VALID || phase == ReferralCodePhase.INVALID
        paddedSection {
            Text(
                referralHeading(phase, appliedCode),
                style = MaxTimeType.heading,
                textAlign = if (centerText) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(6.dp)
            Text(
                referralSubtitle(phase, programInfo, appliedCode),
                style = MaxTimeType.body,
                textAlign = if (centerText) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }

        when (phase) {
            ReferralCodePhase.INPUT, ReferralCodePhase.LOADING -> {
                // The field sits overlapping the bottom of the illustration (per the reference),
                // not as a separate element below it with its own gap — see
                // ReferralIllustrationWithField's doc comment for the overlap math. The image
                // itself is full-bleed (no horizontal padding); the field inside it still gets
                // the standard 24dp inset so it lines up with the heading/button above/below it.
                Spacer(20.dp)
                ReferralIllustrationWithField(
                    code = code,
                    onCodeChange = onCodeChange,
                    onSubmit = onContinue,
                    enabled = phase == ReferralCodePhase.INPUT
                )
            }
            ReferralCodePhase.INVALID -> {
                // No illustration in this state (matches the reference) — the field is a plain
                // standalone element here, same as before.
                Spacer(16.dp)
                paddedSection {
                    ReferralCodeField(
                        code = code,
                        onCodeChange = onCodeChange,
                        onSubmit = onContinue,
                        enabled = true,
                        isInvalid = true
                    )
                }
            }
            ReferralCodePhase.VALID -> Unit
        }

        // VALID skips the friend-card the reference shows here (see class doc comment — no
        // client-readable path to another user's name/photo), which otherwise supplies most of
        // the visual gap between subtitle and button — a plain 20dp reads as noticeably tighter
        // without it, so VALID gets extra breathing room instead.
        Spacer(if (phase == ReferralCodePhase.VALID) 44.dp else 20.dp)

        paddedSection {
            PrimaryButton(
                when (phase) {
                    ReferralCodePhase.LOADING -> "Checking…"
                    ReferralCodePhase.VALID -> "Continue"
                    ReferralCodePhase.INVALID -> "Try Again"
                    ReferralCodePhase.INPUT -> if (code.isBlank()) "Continue" else "Apply Code"
                },
                onClick = onContinue,
                enabled = phase != ReferralCodePhase.LOADING
            )

            when (phase) {
                ReferralCodePhase.INPUT, ReferralCodePhase.LOADING -> {
                    Spacer(4.dp)
                    Text(
                        "Skip for now",
                        color = MaxTimeColors.muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                            .clickable(enabled = phase != ReferralCodePhase.LOADING, onClick = onSkip)
                    )
                }
                ReferralCodePhase.INVALID -> {
                    Spacer(16.dp)
                    // Inert by design (see class doc comment) — no support flow exists to link to yet.
                    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Still having trouble?", color = MaxTimeColors.muted, fontSize = 12.sp)
                        Text(
                            "Contact Support",
                            color = MaxTimeColors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    }
                }
                ReferralCodePhase.VALID -> Unit
            }
        }
    }
}

/** The standard 24dp horizontal inset used by everything on this card EXCEPT the illustration,
 *  which needs to bleed full-width — see ReferralCodeCard's own top-level comment. */
@Composable
private fun paddedSection(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), content = content)
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(height))
}

private fun referralHeading(phase: ReferralCodePhase, appliedCode: String?): AnnotatedString = buildAnnotatedString {
    when (phase) {
        ReferralCodePhase.VALID -> {
            append("You're joining through ")
            withStyle(SpanStyle(color = MaxTimeColors.accent)) { append("${appliedCode.orEmpty()}!") }
            append(" 🎉")
        }
        ReferralCodePhase.INVALID -> append("Invalid code")
        ReferralCodePhase.INPUT, ReferralCodePhase.LOADING -> {
            append("Were you invited by a ")
            withStyle(SpanStyle(color = MaxTimeColors.accent)) { append("friend") }
            append("?")
        }
    }
}

private fun referralSubtitle(phase: ReferralCodePhase, programInfo: ReferralProgramInfo?, appliedCode: String?): String =
    when (phase) {
        ReferralCodePhase.VALID ->
            if (programInfo != null) {
                // Explicit line breaks, not organic wrapping — matches the reference's own
                // 3-line grouping (short phrase / short phrase / short phrase) rather than
                // whatever a plain paragraph happens to wrap to at this screen's width.
                "You'll get ${programInfo.refereeDiscountPercent}% off your first booking\n" +
                    "after it's completed.\n" +
                    "They'll get ${programInfo.referrerDiscountPercent}% too!"
            } else {
                "You're all set — this discount will apply once your first booking is completed."
            }
        ReferralCodePhase.INVALID -> "We couldn't find this referral code. Please check and try again."
        ReferralCodePhase.INPUT, ReferralCodePhase.LOADING ->
            if (programInfo != null) {
                "Enter their referral code to get ${programInfo.refereeDiscountPercent}% off your first booking. " +
                    "They'll get ${programInfo.referrerDiscountPercent}% too!"
            } else {
                "Enter their referral code to get a discount on your first booking."
            }
    }

/**
 * Illustration with the code field overlapping its lower edge — per the reference, the field
 * isn't a separate element sitting below the image with its own gap, it's pushed up so its top
 * half sits ON the image and its bottom half extends past it. Achieved by giving the containing
 * Box an explicit height of (imageHeight - overlap + fieldHeight): the image (top-aligned) then
 * occupies exactly its own [imageHeight], the field (bottom-aligned) occupies exactly its own
 * [fieldHeight] at the bottom, and since (imageHeight - overlap) < imageHeight, those two ranges
 * intersect by exactly [overlap] — the field can't be "pushed up" with a plain negative offset
 * here because that wouldn't reserve the right amount of space in the parent Column for what
 * comes after it.
 */
@Composable
private fun ReferralIllustrationWithField(
    code: String,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
) {
    val imageHeight = 200.dp
    val fieldHeight = 52.dp
    val overlap = 28.dp
    Box(Modifier.fillMaxWidth().height(imageHeight - overlap + fieldHeight)) {
        // Deliberately no border/background box around the image itself — the reference has this
        // bleed directly into the page background with no visible card edge, since the artwork's
        // own dark vignette already matches the surrounding screen. A large-radius clip is kept
        // only as a safety net against hard rectangular corners if the source art's own corners
        // aren't perfectly dark. Alignment.TopCenter (not the ContentScale.Crop default of
        // Center) biases the crop toward the top of the source image, where the high-five/faces
        // actually are — a plain center-crop was cutting in low enough to push the hands up
        // against the very top edge of this box.
        Image(
            painter = painterResource(Res.drawable.referral_highfive),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(imageHeight)
                .clip(MaxTimeShapes.large)
        )
        ReferralCodeField(
            code = code,
            onCodeChange = onCodeChange,
            onSubmit = onSubmit,
            enabled = enabled,
            isInvalid = false,
            height = fieldHeight,
            // The image above is full-bleed (no horizontal padding), but the field stays inset
            // to line up with the heading/button padding elsewhere on this card.
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp)
        )
    }
}

/** Soft radial-gradient glow behind the badge circle — reproduces the reference's neon halo
 *  without `Modifier.blur()`, which requires API 31+ and silently no-ops below it (this app's
 *  minSdk is 26, per the identical tradeoff already made for BottomNav's chip glow). */
private fun Modifier.softGlow(color: Color, radius: androidx.compose.ui.unit.Dp): Modifier = drawBehind {
    val r = radius.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = 0.35f),
            0.45f to color.copy(alpha = 0.18f),
            0.75f to color.copy(alpha = 0.06f),
            1.00f to color.copy(alpha = 0f),
            center = center,
            radius = r
        ),
        radius = r
    )
}

/** One confetti fleck — a tiny rotated dash or dot scattered around the badge circle. */
private data class ConfettiSpec(
    val offsetX: androidx.compose.ui.unit.Dp,
    val offsetY: androidx.compose.ui.unit.Dp,
    val width: androidx.compose.ui.unit.Dp,
    val height: androidx.compose.ui.unit.Dp,
    val rotation: Float,
    val color: Color,
)

/** Wider, more scattered burst than a fixed 4-corner cross — closer to the reference's loose
 *  confetti spray around the glow. Kept to this app's existing green palette rather than
 *  introducing new brand colors nothing else in the app uses. */
private fun confettiBurst(): List<ConfettiSpec> = listOf(
    ConfettiSpec((-46).dp, (-38).dp, 3.dp, 11.dp, -35f, MaxTimeColors.accent),
    ConfettiSpec(40.dp, (-42).dp, 3.dp, 10.dp, 30f, MaxTimeColors.accent2),
    ConfettiSpec((-54).dp, 6.dp, 5.dp, 5.dp, 0f, MaxTimeColors.accentDeep),
    ConfettiSpec(52.dp, 2.dp, 4.dp, 4.dp, 0f, MaxTimeColors.accent2),
    ConfettiSpec((-34).dp, 44.dp, 3.dp, 9.dp, 50f, MaxTimeColors.accent2),
    ConfettiSpec(36.dp, 46.dp, 3.dp, 9.dp, -50f, MaxTimeColors.accentDeep),
    ConfettiSpec((-14).dp, (-56).dp, 4.dp, 4.dp, 0f, MaxTimeColors.accent),
    ConfettiSpec(16.dp, 54.dp, 3.dp, 3.dp, 0f, MaxTimeColors.accent),
)

/** Celebratory (green check, glow + confetti scatter) / error (red exclamation, glow only) badge
 *  for the Valid/Invalid states — matches the approved reference's neon-glow + confetti flourish
 *  (2026-09-18 handoff) rather than the earlier plain 4-dash placeholder. */
@Composable
private fun ReferralStatusBadge(valid: Boolean) {
    val color = if (valid) MaxTimeColors.accent else MaxTimeColors.red
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            if (valid) {
                confettiBurst().forEach { spec ->
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .offset(x = spec.offsetX, y = spec.offsetY)
                            .size(spec.width, spec.height)
                            .graphicsLayer { rotationZ = spec.rotation }
                            .clip(MaxTimeShapes.pill)
                            .background(spec.color)
                    )
                }
            }
            Box(
                Modifier.size(72.dp).softGlow(color, 56.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(MaxTimeColors.bg2).border(2.dp, color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (valid) "✓" else "!", color = color, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun ReferralCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    isInvalid: Boolean,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 52.dp,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    // This field sits low in the layout (overlapping the illustration, per the design), so it's
    // an easy target for a race that's otherwise invisible on shorter onboarding screens: focus
    // fires and requests a scroll-into-view immediately, but the keyboard's WindowInsets.ime
    // height arrives over several animated frames — the very first bringIntoView() call can run
    // against a still-zero (or partial) inset and land short, leaving the field behind the
    // keyboard once it finishes sliding up. Re-issuing the request every time imeBottom changes
    // (while focused) corrects for wherever the keyboard's real final height ends up.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(isFocused, imeBottom) {
        if (isFocused && imeBottom > 0) {
            bringIntoViewRequester.bringIntoView()
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaxTimeShapes.medium)
            .background(MaxTimeColors.cardBg)
            .border(1.dp, if (isInvalid) MaxTimeColors.red else MaxTimeColors.cardBorder, MaxTimeShapes.medium)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonGlyph(tint = MaxTimeColors.muted)
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (code.isEmpty()) {
                    BasicText(
                        "Enter referral code (optional)",
                        style = TextStyle(color = MaxTimeColors.muted, fontSize = 13.5.sp)
                    )
                }
                BasicTextField(
                    value = code,
                    onValueChange = { onCodeChange(it.uppercase()) },
                    enabled = enabled,
                    textStyle = TextStyle(color = MaxTimeColors.textStrong, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                    singleLine = true,
                    cursorBrush = SolidColor(MaxTimeColors.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide(); onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                )
            }
            if (code.isNotEmpty() && enabled) {
                Text(
                    "✕",
                    color = MaxTimeColors.muted,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { onCodeChange("") }
                )
            }
        }
    }
}

/** Small custom-drawn silhouette — avoids pulling in a whole icon-font/library dependency for one
 *  glyph nobody else in this codebase needs yet. */
@Composable
private fun PersonGlyph(tint: Color) {
    Canvas(Modifier.size(16.dp)) {
        drawCircle(color = tint, radius = size.width * 0.22f, center = Offset(size.width / 2f, size.height * 0.28f))
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(size.width * 0.06f, size.height * 0.42f),
            size = Size(size.width * 0.88f, size.height * 0.9f)
        )
    }
}
