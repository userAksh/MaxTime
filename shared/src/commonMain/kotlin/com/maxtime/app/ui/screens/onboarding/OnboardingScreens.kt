package com.maxtime.app.ui.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppPrefsKeys
import com.maxtime.app.data.AppState
import com.maxtime.app.data.DEFAULT_SELECTED_INTERESTS
import com.maxtime.app.data.LocalSettings
import com.maxtime.app.data.firebase.AuthRepository
import com.maxtime.app.data.firebase.ReferralFunctions
import com.maxtime.app.data.firebase.RedeemReferralOutcome
import com.maxtime.app.data.firebase.UserProfileRepository
import com.maxtime.app.data.firebase.buildPhoneVerificationProvider
import com.maxtime.app.data.firebase.rememberPhoneAuthContext
import com.maxtime.app.data.media.decodeImageBitmap
import com.maxtime.app.data.media.pickProfilePhoto
import com.maxtime.app.data.media.rememberImagePickerContext
import com.maxtime.app.ui.components.InterestChip
import com.maxtime.app.ui.components.OtpCodeInput
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ProgressBar
import com.maxtime.app.ui.components.ReferralCodeCard
import com.maxtime.app.ui.components.ReferralCodePhase
import com.maxtime.app.ui.components.ReferralProgramInfo
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SecondaryButton
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType
import com.maxtime.app.util.friendlyErrorMessage
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingProgressHeader(step: Int, total: Int = 5, onBack: (() -> Unit)? = null) {
    Column {
        ProgressBar(step, total, Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 10.dp))
        TopBar(onBack = onBack)
    }
}

@Composable
fun SplashScreen(onGetStarted: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaxTimeColors.accent),
                contentAlignment = Alignment.Center
            ) {
                Text("MT", color = MaxTimeColors.onAccent, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(20.dp))
            Text("MaxTime", color = MaxTimeColors.textStrong, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text("Find the activity. Find the people.", style = MaxTimeType.body)
        }
        Column(Modifier.fillMaxWidth().background(MaxTimeColors.bg1).imePadding().padding(horizontal = 24.dp, vertical = 22.dp)) {
            PrimaryButton("Get started", onClick = onGetStarted)
        }
    }
}

@Composable
fun AgeGateScreen(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        OnboardingProgressHeader(step = 1)
        Text(
            "Meet people.\nDo more.",
            style = MaxTimeType.heading,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Text(
            "MaxTime helps you find real activities and the right people to do them with. You need to be 18 or older to continue.",
            style = MaxTimeType.body,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().background(MaxTimeColors.bg1).imePadding().padding(horizontal = 24.dp, vertical = 22.dp)) {
            PrimaryButton("I am 18 or older — Continue", onClick = onContinue)
        }
    }
}

private enum class AuthView { ENTRY, OTP }

@Composable
fun AuthScreen(onGoogleSkip: () -> Unit, onVerified: () -> Unit) {
    var view by remember { mutableStateOf(AuthView.ENTRY) }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var codeDeferred by remember { mutableStateOf<CompletableDeferred<String>?>(null) }
    // Bumped once per SMS actually sent (initial send + every resend) — the countdown
    // LaunchedEffect below is keyed on this rather than on `view`, since a resend tap doesn't
    // change `view` (it's already AuthView.OTP) and so wouldn't otherwise restart the timer.
    var otpAttempt by remember { mutableStateOf(0) }
    var resendSecondsLeft by remember { mutableStateOf(0) }
    // The in-flight verifyPhoneNumber coroutine, if any — tracked explicitly (rather than just
    // firing scope.launch and forgetting it) so a resend or "Change number" tap can actually
    // cancel a still-pending attempt instead of leaving it running in the background. Without
    // this, a stale attempt for a number the user already backed out of could still complete
    // later (e.g. Android's own-device SMS auto-verification) and silently call onVerified() —
    // signing the user in under a phone number they explicitly chose to change.
    var currentAttemptJob by remember { mutableStateOf<Job?>(null) }

    val scope = rememberCoroutineScope()
    val phoneAuthContext = rememberPhoneAuthContext()
    val authRepository = remember { AuthRepository() }

    LaunchedEffect(otpAttempt) {
        if (otpAttempt > 0) {
            resendSecondsLeft = 30
            while (resendSecondsLeft > 0) {
                delay(1000)
                resendSecondsLeft -= 1
            }
        }
    }

    fun sendOtp() {
        errorText = null
        isLoading = true
        // Cancelling any still-running previous attempt (an earlier send/resend, or one left
        // over from before a "Change number" trip) before starting a new one guarantees only
        // ever one verifyPhoneNumber call is live at a time — never two racing to call
        // onVerified() for two different phone numbers.
        currentAttemptJob?.cancel()
        currentAttemptJob = scope.launch {
            try {
                val provider = buildPhoneVerificationProvider(phoneAuthContext) {
                    val deferred = CompletableDeferred<String>()
                    codeDeferred = deferred
                    view = AuthView.OTP
                    isLoading = false
                    otpAttempt += 1
                    deferred.await()
                }
                authRepository.verifyPhoneNumber("+91$phone", provider)
                onVerified()
            } catch (e: CancellationException) {
                // Deliberately cancelled (resend / change number) — not a real failure, so no
                // error text and no isLoading reset (whatever superseded this already owns that).
                throw e
            } catch (e: Exception) {
                isLoading = false
                // Clearing the typed code lets the user immediately retry (e.g. after a wrong-code
                // rejection) without manually deleting 6 digits first — the field keeps focus
                // throughout since nothing here touches it, so the keyboard is still up and ready.
                otp = ""
                errorText = friendlyErrorMessage(e, "Something went wrong. Try again.")
            }
        }
    }

    // Resend re-runs the whole verifyPhoneNumber flow rather than threading Android's
    // forceResendingToken through this shared abstraction — iOS's GitLive wrapper has no
    // equivalent concept at all, so a token-based resend could only ever work on one platform.
    // sendOtp() above cancels the superseded attempt, so there's never more than one listening.
    fun resendOtp() {
        otp = ""
        sendOtp()
    }

    // Back to phone entry to fix a mistyped number. `phone` is untouched (still whatever was
    // typed) so it's naturally prefilled for editing. Everything specific to the verification
    // attempt in flight is torn down: the pending coroutine is cancelled (see sendOtp's comment
    // — otherwise it could complete later and sign the user in under the number they just chose
    // to change), its deferred is dropped so a stray submitOtp() call can't resolve into
    // nothing, and the OTP/countdown/error state resets so returning to this step later (after
    // re-sending) starts clean rather than showing stale leftovers. This is also what the top
    // bar's own back arrow calls (not a bare `view = AuthView.ENTRY`) — that arrow used to skip
    // all of this teardown entirely, which was the exact same "stale attempt could still sign
    // the user in later" risk "Change number" was built to avoid, just reachable from a
    // different button.
    fun changeNumber() {
        currentAttemptJob?.cancel()
        currentAttemptJob = null
        codeDeferred = null
        otp = ""
        errorText = null
        isLoading = false
        resendSecondsLeft = 0
        view = AuthView.ENTRY
    }

    fun submitOtp() {
        errorText = null
        isLoading = true
        codeDeferred?.complete(otp)
    }

    fun continueAsGuest() {
        errorText = null
        isLoading = true
        scope.launch {
            try {
                authRepository.signInAnonymously()
                onGoogleSkip()
            } catch (e: Exception) {
                isLoading = false
                errorText = friendlyErrorMessage(e, "Something went wrong. Try again.")
            }
        }
    }

    ScreenScaffold(
        topBar = {
            OnboardingProgressHeader(
                step = 2,
                onBack = if (view != AuthView.ENTRY) ::changeNumber else null
            )
        }
        // Deliberately no bottomBar/BottomCta here — the CTA buttons are rendered inline, right
        // after each view's field, instead of pinned to the screen's true bottom edge in a
        // separately keyboard-aware sibling. That separate-sibling design is what caused every
        // round of this screen's bugs (button-over-field overlap, shadow bleed onto the field
        // above, a scroll/resize animation desync at the keyboard transition, and the dead gap
        // between the field and the button) — all stemming from BottomCta needing its own
        // independent keyboard-inset handling that never quite stayed in sync with the scrollable
        // content next to it. Inline placement means the button is just the next item in the same
        // scrollable Column as the field: it sits directly below it with ordinary padding, and
        // follows the keyboard the same standard way any scrolled-into-view content does — no
        // separate resize/inset logic to desync in the first place.
    ) {
        Text("Get you set up", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

        when (view) {
            AuthView.ENTRY -> {
                Text("Enter your number and we will text you a code.", style = MaxTimeType.body, modifier = Modifier.padding(horizontal = 24.dp))
                FieldLabel("Mobile number")
                Row(
                    // A fixed height instead of IntrinsicSize.Min — the latter needs to query
                    // BasicTextField's intrinsic height, which is unreliable now that this Row
                    // sits inside ScreenScaffold's scrollable content (verticalScroll gives its
                    // children an effectively infinite height constraint, and intrinsic
                    // measurement doesn't handle that well), so the row was measuring shorter
                    // than the field's real rendered content and the Send OTP button below ended
                    // up drawn over the bottom of the field instead of below it.
                    Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.fillMaxHeight().clip(MaxTimeShapes.medium).background(MaxTimeColors.cardBg)
                            .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        contentAlignment = Alignment.CenterStart
                    ) { Text("+91", color = MaxTimeColors.textStrong, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp) }
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(MaxTimeShapes.medium).background(MaxTimeColors.cardBg)
                            .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        MaxTimeTextField(phone, { phone = it }, "98765 43210", KeyboardType.Phone, maxLength = 10, digitsOnly = true)
                    }
                }
                errorText?.let {
                    Text(it, color = MaxTimeColors.red, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                }
                PrimaryButton(
                    if (isLoading) "Sending…" else "Send OTP",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    onClick = ::sendOtp,
                    enabled = !isLoading && phone.length == 10
                )
                OrDivider(Modifier.padding(horizontal = 24.dp))
                // Plain underlined link, not a button — guest is the "skip for now" path, not a
                // peer sign-in option, matching the same link styling as "Change number" /
                // "Resend code" on the OTP step for a consistent visual language across this screen.
                Text(
                    "Continue as guest",
                    color = MaxTimeColors.muted,
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .clickable(onClick = ::continueAsGuest)
                )
            }
            AuthView.OTP -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Code sent to +91 $phone",
                        style = MaxTimeType.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Change number",
                        color = MaxTimeColors.accent,
                        fontSize = 12.sp,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(onClick = ::changeNumber)
                    )
                }
                Box(
                    // Fixed height for the same reason as the phone-number row above — inside
                    // ScreenScaffold's verticalScroll, intrinsic sizing sees an effectively
                    // infinite height constraint, so without a fixed height this box's rendered
                    // height would shift depending on whether the field is empty or has content.
                    Modifier.padding(horizontal = 24.dp, vertical = 10.dp).fillMaxWidth().height(52.dp)
                        .clip(MaxTimeShapes.medium).background(MaxTimeColors.cardBg)
                        .border(1.dp, MaxTimeColors.accent, MaxTimeShapes.medium)
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    // Without an explicit size modifier, the underlying native view (UIKitView on
                    // iOS, BasicTextField on Android) only lays out to wrap its placeholder text —
                    // leaving the rest of this bordered box as inert decoration with a visibly
                    // different background, and the actual tap/focus target shrunk down to just
                    // that narrow patch instead of the whole field.
                    OtpCodeInput(
                        otp,
                        { otp = it },
                        onComplete = ::submitOtp,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = "6-digit code",
                        // otpAttempt bumps on the initial send AND every resend — re-requesting
                        // focus each time is what actually lands the keyboard for a resent code,
                        // not just the very first one.
                        refocusTrigger = otpAttempt
                    )
                }
                Text(
                    if (resendSecondsLeft > 0) {
                        "Resend code in 0:${resendSecondsLeft.toString().padStart(2, '0')}"
                    } else {
                        "Resend code"
                    },
                    color = if (resendSecondsLeft > 0) MaxTimeColors.muted else MaxTimeColors.accent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .let { if (resendSecondsLeft == 0 && !isLoading) it.clickable(onClick = ::resendOtp) else it }
                )
                errorText?.let {
                    Text(it, color = MaxTimeColors.red, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                }
                PrimaryButton(
                    if (isLoading) "Verifying…" else "Verify",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    onClick = ::submitOtp,
                    enabled = !isLoading && otp.length == 6
                )
            }
        }
    }
}

/**
 * MaxTime_Claude_Product_Design_Context.docx §6-11 — optional referral code entry, right after
 * Auth (needs a signed-in uid) and before Profile. All referral business logic (attribution,
 * fraud checks, qualification, reward state machine) lives entirely in the referral Cloud
 * Functions (functions/src/referral.ts) — this screen only calls ReferralFunctions and renders
 * whatever it gets back; nothing here duplicates or reimplements a referral rule.
 *
 * Visuals here are deliberately plain/placeholder (see ReferralCodeCard's own doc comment) —
 * the approved character artwork/animation is supplied separately per the brief and slots into
 * ReferralCodeCard without needing to touch this screen's state/wiring at all.
 */
@Composable
fun ReferralScreen(onContinue: () -> Unit) {
    var phase by remember { mutableStateOf(ReferralCodePhase.INPUT) }
    var code by remember { mutableStateOf("") }
    var programInfo by remember { mutableStateOf<ReferralProgramInfo?>(null) }
    var appliedCode by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val referralFunctions = remember { ReferralFunctions() }

    LaunchedEffect(Unit) {
        // Returning to this step after a valid referral was already established (app closed and
        // reopened mid-onboarding, or the user swiped back and forward again) — reflect that
        // immediately instead of showing a blank form for an already-decided step.
        val alreadyLinked = try {
            val summary = referralFunctions.getMyReferralSummary()
            summary.asReferee
        } catch (e: Exception) {
            null // a failed summary read shouldn't block onboarding, only the "already linked" preview
        }
        if (alreadyLinked != null) {
            appliedCode = alreadyLinked.code
            val reward = alreadyLinked.refereeReward
            if (reward != null) {
                programInfo = ReferralProgramInfo(
                    refereeDiscountPercent = reward.discountPercent ?: 0,
                    refereeDiscountCapAmount = reward.discountCapAmount ?: 0,
                    referrerDiscountPercent = 0 // not shown once already applied — see referralSubtitle's VALID branch
                )
            }
            phase = ReferralCodePhase.VALID
            return@LaunchedEffect
        }

        // Not yet linked — fetch the live admin-configured percentages for the prompt copy
        // (never hardcode these client-side; they're admin-controlled server-side). Reuses
        // generateMyReferralCode rather than a new endpoint — it also mints/returns this user's
        // own shareable code as a side effect, which is harmless (every user gets one eventually
        // anyway for sharing later).
        try {
            val info = referralFunctions.generateMyReferralCode()
            programInfo = ReferralProgramInfo(
                refereeDiscountPercent = info.refereeDiscountPercent,
                refereeDiscountCapAmount = info.refereeDiscountCapAmount,
                referrerDiscountPercent = info.referrerDiscountPercent
            )
        } catch (e: Exception) {
            // Referral program not configured/active server-side, or a transient network issue —
            // programInfo just stays null and the generic (no-numbers) copy shows instead. Must
            // never block Continue/Skip just because this optional preview failed to load.
        }
    }

    fun submit() {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            onContinue()
            return
        }
        errorText = null
        phase = ReferralCodePhase.LOADING
        scope.launch {
            when (val outcome = referralFunctions.redeemReferralCode(trimmed)) {
                is RedeemReferralOutcome.Redeemed -> {
                    appliedCode = trimmed
                    val percent = outcome.result.refereeDiscountPercent
                    val cap = outcome.result.refereeDiscountCapAmount
                    if (percent != null) {
                        programInfo = ReferralProgramInfo(
                            refereeDiscountPercent = percent,
                            refereeDiscountCapAmount = cap ?: 0,
                            // Now sourced directly from this same redeemReferralCode response
                            // (server-snapshotted from the same config read as the referee
                            // fields above) — previously this fell back to whatever the earlier,
                            // unrelated generateMyReferralCode() call had left in programInfo, so
                            // if that call hadn't resolved yet (or failed), this silently showed
                            // "They'll get 0% too!" despite the referee's own percent being correct.
                            referrerDiscountPercent = outcome.result.referrerDiscountPercent
                                ?: programInfo?.referrerDiscountPercent ?: 0
                        )
                    }
                    phase = ReferralCodePhase.VALID
                }
                is RedeemReferralOutcome.Rejected -> {
                    errorText = outcome.message
                    phase = ReferralCodePhase.INVALID
                }
            }
        }
    }

    ScreenScaffold(topBar = { OnboardingProgressHeader(step = 3) }) {
        Spacer(Modifier.height(8.dp))
        ReferralCodeCard(
            phase = phase,
            code = code,
            // Deliberately does NOT flip phase back to INPUT on the first keystroke — that would
            // swap ReferralCodeCard from the INVALID layout (standalone field, no illustration)
            // to the INPUT layout (field overlapping the illustration) mid-edit. Since those are
            // different composable call sites, the old BasicTextField gets torn down and a new
            // one created elsewhere on screen, which drops focus and dismisses the keyboard —
            // exactly the "backspace kicks me out" bug. Staying on INVALID keeps the same field
            // in place so the user can correct the code and resubmit via the same "Try Again"
            // button without ever losing focus.
            onCodeChange = { code = it },
            onContinue = { if (phase == ReferralCodePhase.VALID) onContinue() else submit() },
            onSkip = onContinue,
            programInfo = programInfo,
            appliedCode = appliedCode,
            errorMessage = errorText
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(state: AppState, onContinue: () -> Unit) {
    var name by remember { mutableStateOf(state.currentUser.name) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val profileRepository = remember { UserProfileRepository() }
    val imagePickerContext = rememberImagePickerContext()

    // DatePickerState works in epoch millis (UTC start-of-day) — formatDateOfBirth converts that
    // straight to a DD/MM/YYYY string with pure integer date math, deliberately not going through
    // any platform Calendar/Date API, so there's no local-timezone rounding to shift the day by one.
    var dobMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dobMillis)

    var photoUrl by remember { mutableStateOf(state.currentUser.photoUrl) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    // Decoded once from the picked bytes and kept for this screen's lifetime, independent of the
    // upload — shows what was actually picked immediately, rather than a checkmark/placeholder
    // standing in for it while the upload runs (or if it fails).
    var pickedPreview by remember { mutableStateOf<ImageBitmap?>(null) }

    fun pickPhoto() {
        scope.launch {
            val picked = pickProfilePhoto(imagePickerContext) ?: return@launch
            pickedPreview = decodeImageBitmap(picked.bytes)
            val uid = Firebase.auth.currentUser?.uid ?: return@launch
            errorText = null
            isUploadingPhoto = true
            try {
                val url = profileRepository.uploadProfilePhoto(uid, picked)
                photoUrl = url
                state.currentUser = state.currentUser.copy(photoUrl = url)
            } catch (e: Exception) {
                errorText = friendlyErrorMessage(e, "Couldn't upload photo. Try again.")
            } finally {
                isUploadingPhoto = false
            }
        }
    }

    fun continueTapped() {
        val finalName = name.trim().ifBlank { state.currentUser.name }
        val dobFormatted = dobMillis?.let { formatDateOfBirth(it) } ?: state.currentUser.dateOfBirth
        state.currentUser = state.currentUser.copy(name = finalName, dateOfBirth = dobFormatted)
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            onContinue()
            return
        }
        errorText = null
        isSaving = true
        scope.launch {
            try {
                profileRepository.saveProfile(uid, finalName, Firebase.auth.currentUser?.phoneNumber, dobFormatted)
                onContinue()
            } catch (e: Exception) {
                isSaving = false
                errorText = friendlyErrorMessage(e, "Couldn't save your profile. Try again.")
            }
        }
    }

    // Deliberately no bottomBar/BottomCta here — a fixed bottom bar sits pinned right above the
    // keyboard, independent of this content's own scroll position, which with fields this far
    // down the screen (photo, name, THEN date of birth) meant the pinned Continue button could
    // end up sitting directly on top of/right against the date-of-birth field once the keyboard
    // opened, instead of the two ever being able to separate via scrolling. Putting the button
    // inside the same scrollable column as the fields (matching ReferralCodeCard's approach)
    // means it scrolls together with them and ScreenScaffold's own imePadding (the
    // `bottomBar == null` branch) keeps it clear of the keyboard like everything else here.
    ScreenScaffold(topBar = { OnboardingProgressHeader(step = 4) }) {
        Text("Add your name and photo", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text("This is how others recognize you at an activity.", style = MaxTimeType.body, modifier = Modifier.padding(horizontal = 24.dp))
        Box(
            Modifier.padding(top = 6.dp, bottom = 22.dp).align(Alignment.CenterHorizontally)
                .size(104.dp).clip(CircleShape).background(MaxTimeColors.cardBg)
                .border(2.dp, MaxTimeColors.cardBorder, CircleShape)
                .clickable(enabled = !isUploadingPhoto) { pickPhoto() },
            contentAlignment = Alignment.Center
        ) {
            when {
                pickedPreview != null -> Image(
                    bitmap = pickedPreview!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                isUploadingPhoto -> Text("…", color = MaxTimeColors.muted, fontSize = 20.sp)
                // A resumed session (photoUrl known from an earlier upload, but not picked again
                // this time) has no local bytes to decode a preview from — but Coil3's
                // AsyncImage (registered once in App.kt, already used by AvatarCircle elsewhere)
                // loads the real remote photo directly, same as everywhere else in the app shows
                // a saved profile photo. This used to show a plain checkmark instead, from before
                // that image loader existed here.
                photoUrl != null -> coil3.compose.AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // A bare "+" reads as a generic "add" affordance, not specifically "add a photo" —
                // a camera glyph is the standard, immediately-recognizable convention for that
                // (same idea as WhatsApp/Instagram's own profile-photo placeholder).
                else -> CameraGlyph(tint = MaxTimeColors.muted)
            }
        }
        FieldLabel("Full name")
        EditableFieldBox(name, { name = it }, "Enter your name")
        FieldLabel("Date of birth")
        FieldValueBox(dobMillis?.let { formatDateOfBirth(it) } ?: "Select date of birth", onClick = { showDatePicker = true })
        errorText?.let {
            Text(it, color = MaxTimeColors.red, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            if (isSaving) "Saving…" else "Continue",
            onClick = ::continueTapped,
            enabled = !isSaving && name.isNotBlank() && dobMillis != null,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
    }

    if (showDatePicker) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    dobMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }
}

/** Pure integer civil-calendar conversion (Howard Hinnant's days_from_civil algorithm) — no
 *  platform Calendar/Date API, so there's no local-timezone rounding to worry about, since
 *  DatePickerState's selectedDateMillis is UTC start-of-day already. */
private fun formatDateOfBirth(epochMillis: Long): String {
    val days = epochMillis.floorDiv(86_400_000L)
    val z = days + 719468
    val era = (if (z >= 0) z else z - 146096).floorDiv(146097)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = if (month <= 2) y + 1 else y
    fun Long.pad2() = if (this < 10) "0$this" else "$this"
    return "${day.pad2()} / ${month.pad2()} / $year"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsScreen(state: AppState, onFinish: () -> Unit) {
    var selected: Map<String, Set<String>> by remember { mutableStateOf(DEFAULT_SELECTED_INTERESTS) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val profileRepository = remember { UserProfileRepository() }

    fun finishTapped() {
        state.interests = selected
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            // No uid means no auth session at all — shouldn't be reachable this far into
            // onboarding, but with nothing to key the completion flag by, there's nothing to
            // persist here either.
            onFinish()
            return
        }
        isSaving = true
        scope.launch {
            try {
                profileRepository.saveInterests(uid, selected)
                profileRepository.markOnboardingComplete(uid)
            } catch (_: Exception) {
                // Best-effort — interests aren't a blocking step, the user still lands on Home.
                // markOnboardingComplete not reaching Firestore here just means this device's own
                // local flag (below) is what carries them to Home this time; App.kt's own
                // backfill (see its LaunchedEffect) will retry writing the server flag next launch.
            }
            LocalSettings.putBoolean(AppPrefsKeys.onboardingCompleteKey(uid), true)
            onFinish()
        }
    }

    Column(Modifier.fillMaxSize()) {
        OnboardingProgressHeader(step = 5)
        Text("What are you into?", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text("Pick a few, this shapes your first recommendations.", style = MaxTimeType.body, modifier = Modifier.padding(horizontal = 24.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            com.maxtime.app.data.INTEREST_CATEGORIES.forEach { (category, items) ->
                Text(
                    category.uppercase(),
                    color = MaxTimeColors.accent2,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 9.dp)
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items.forEach { item ->
                        val isOn = selected[category]?.contains(item) == true
                        InterestChip(item, isOn) {
                            val current = selected[category] ?: emptySet()
                            val updated = if (isOn) current - item else current + item
                            selected = selected + (category to updated)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.fillMaxWidth().background(MaxTimeColors.bg1).imePadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            PrimaryButton(if (isSaving) "Saving…" else "Finish — Go to Home", onClick = ::finishTapped, enabled = !isSaving)
        }
    }
}

/** Small custom-drawn camera outline — avoids pulling in a whole icon-font/library dependency
 *  for one glyph, same reasoning as ReferralCodeCard's PersonGlyph. */
@Composable
private fun CameraGlyph(tint: Color) {
    Canvas(Modifier.size(28.dp)) {
        val strokeWidth = size.width * 0.09f
        val bodyTop = size.height * 0.34f
        val bodyHeight = size.height * 0.48f
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, bodyTop),
            size = Size(size.width, bodyHeight),
            cornerRadius = CornerRadius(size.width * 0.14f, size.width * 0.14f),
            style = Stroke(width = strokeWidth)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.34f, bodyTop - size.height * 0.1f),
            size = Size(size.width * 0.32f, size.height * 0.14f),
            cornerRadius = CornerRadius(size.width * 0.05f, size.width * 0.05f),
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = tint,
            radius = size.width * 0.15f,
            center = Offset(size.width / 2f, bodyTop + bodyHeight / 2f),
            style = Stroke(width = strokeWidth * 0.9f)
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        color = MaxTimeColors.muted2,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp).padding(top = 10.dp)
    )
}

@Composable
private fun EditableFieldBox(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Box(
        Modifier.padding(horizontal = 24.dp, vertical = 0.dp).padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(MaxTimeShapes.medium)
            .background(MaxTimeColors.cardBg)
            .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        // Capitalize the first letter of each word — a name typed from empty (including after
        // clearing it to retype) should start capitalized without needing a manual shift tap,
        // same as the system keyboard already does for a genuine "new text" field elsewhere.
        MaxTimeTextField(
            value, onValueChange, placeholder, KeyboardType.Text,
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words
        )
    }
}

@Composable
private fun FieldValueBox(text: String, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.padding(horizontal = 24.dp, vertical = 0.dp).padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(MaxTimeShapes.medium)
            .background(MaxTimeColors.cardBg)
            .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) { Text(text, color = MaxTimeColors.muted, fontSize = 13.5.sp) }
}

@Composable
private fun OrDivider(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(MaxTimeColors.cardBorder))
        Text("or continue with", color = MaxTimeColors.muted, fontSize = 11.sp)
        Box(Modifier.weight(1f).height(1.dp).background(MaxTimeColors.cardBorder))
    }
}

@Composable
private fun MaxTimeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    maxLength: Int? = null,
    digitsOnly: Boolean = false,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Disables the native Select/Select All edit menu for digits-only fields (phone/OTP) — no
    // legitimate reason to select/copy/paste those, and on iOS specifically, calling
    // focusManager.clearFocus() below (once the field is fully typed) can surface that menu as a
    // residual artifact of the text-input session ending, overlapping the field's own label above
    // it. Left untouched for non-digits fields (digitsOnly = false, e.g. name) — pasting copied
    // text there is genuinely useful.
    val defaultToolbar = androidx.compose.ui.platform.LocalTextToolbar.current
    val noToolbar = remember {
        object : androidx.compose.ui.platform.TextToolbar {
            override val status: androidx.compose.ui.platform.TextToolbarStatus
                get() = androidx.compose.ui.platform.TextToolbarStatus.Hidden
            override fun showMenu(
                rect: androidx.compose.ui.geometry.Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) = Unit
            override fun hide() = Unit
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalTextToolbar provides if (digitsOnly) noToolbar else defaultToolbar
    ) {
    Box {
        if (value.isEmpty()) {
            // BasicText, not Text — Material's Text pulls in an ambient line-height from its
            // typography system that BasicTextField's own raw textStyle below does not, so even
            // at the identical fontSize the two don't share the same line metrics and visibly
            // don't align vertically (the cursor renders above the placeholder digits). BasicText
            // goes through the exact same foundation-level text layout as BasicTextField, with no
            // Material inheritance, so an identical TextStyle here really does mean identical
            // vertical positioning.
            androidx.compose.foundation.text.BasicText(
                placeholder,
                style = TextStyle(color = MaxTimeColors.muted, fontSize = 13.5.sp)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                val filtered = if (digitsOnly) raw.filter { it.isDigit() } else raw
                val limited = if (maxLength != null) filtered.take(maxLength) else filtered
                onValueChange(limited)
                // Reaching the expected length (a full phone number, a full OTP) means this field
                // is done — drop focus and dismiss the keyboard so it doesn't sit there covering
                // the CTA button once there's nothing left to type. Clearing focus (not just hiding
                // the keyboard) is what makes tapping the field again reliably bring it back up.
                if (maxLength != null && limited.length == maxLength) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            },
            textStyle = TextStyle(color = MaxTimeColors.textStrong, fontSize = 13.5.sp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaxTimeColors.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
    }
}
