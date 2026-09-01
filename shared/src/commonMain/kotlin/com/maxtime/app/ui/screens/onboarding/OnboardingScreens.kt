package com.maxtime.app.ui.screens.onboarding

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.firebase.AuthRepository
import com.maxtime.app.data.firebase.UserProfileRepository
import com.maxtime.app.data.firebase.buildPhoneVerificationProvider
import com.maxtime.app.data.firebase.rememberPhoneAuthContext
import com.maxtime.app.ui.components.InterestChip
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ProgressBar
import com.maxtime.app.ui.components.SecondaryButton
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

@Composable
fun OnboardingProgressHeader(step: Int, total: Int = 4, onBack: (() -> Unit)? = null) {
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
        Column(Modifier.fillMaxWidth().background(MaxTimeColors.bg1).padding(horizontal = 24.dp, vertical = 22.dp)) {
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
        Column(Modifier.fillMaxWidth().background(MaxTimeColors.bg1).padding(horizontal = 24.dp, vertical = 22.dp)) {
            PrimaryButton("I am 18 or older — Continue", onClick = onContinue)
        }
    }
}

private enum class AuthView { ENTRY, EMAIL, OTP }

@Composable
fun AuthScreen(onGoogleSkip: () -> Unit, onVerified: () -> Unit) {
    var view by remember { mutableStateOf(AuthView.ENTRY) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var codeDeferred by remember { mutableStateOf<CompletableDeferred<String>?>(null) }

    val scope = rememberCoroutineScope()
    val phoneAuthContext = rememberPhoneAuthContext()
    val authRepository = remember { AuthRepository() }

    fun sendOtp() {
        errorText = null
        isLoading = true
        scope.launch {
            try {
                val provider = buildPhoneVerificationProvider(phoneAuthContext) {
                    val deferred = CompletableDeferred<String>()
                    codeDeferred = deferred
                    view = AuthView.OTP
                    isLoading = false
                    deferred.await()
                }
                authRepository.verifyPhoneNumber("+91$phone", provider)
                onVerified()
            } catch (e: Exception) {
                isLoading = false
                errorText = e.message ?: "Something went wrong. Try again."
            }
        }
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
                errorText = e.message ?: "Something went wrong. Try again."
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OnboardingProgressHeader(
            step = 2,
            onBack = if (view != AuthView.ENTRY) { { view = AuthView.ENTRY } } else null
        )
        Text("Get you set up", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

        when (view) {
            AuthView.ENTRY -> {
                Text("Enter your number and we will text you a code.", style = MaxTimeType.body, modifier = Modifier.padding(horizontal = 24.dp))
                FieldLabel("Mobile number")
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.clip(MaxTimeShapes.medium).background(MaxTimeColors.cardBg)
                            .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) { Text("+91", color = MaxTimeColors.textStrong, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp) }
                    Box(
                        Modifier.weight(1f).clip(MaxTimeShapes.medium).background(MaxTimeColors.cardBg)
                            .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        MaxTimeTextField(phone, { phone = it }, "98765 43210", KeyboardType.Phone)
                    }
                }
                errorText?.let {
                    Text(it, color = MaxTimeColors.red, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().background(MaxTimeColors.bg1).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    PrimaryButton(if (isLoading) "Sending…" else "Send OTP", onClick = ::sendOtp, enabled = !isLoading && phone.isNotBlank())
                    OrDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Email", Modifier.weight(1f), onClick = { view = AuthView.EMAIL })
                        SecondaryButton("Continue as guest", Modifier.weight(1f), onClick = ::continueAsGuest)
                    }
                }
            }
            AuthView.EMAIL -> {
                FieldLabel("Email address")
                Box(
                    Modifier.padding(horizontal = 24.dp).fillMaxWidth()
                        .clip(MaxTimeShapes.medium).background(MaxTimeColors.cardBg)
                        .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    MaxTimeTextField(email, { email = it }, "you@example.com", KeyboardType.Email)
                }
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().background(MaxTimeColors.bg1).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    PrimaryButton("Send code", onClick = { view = AuthView.OTP })
                }
            }
            AuthView.OTP -> {
                Text("Code sent to +91 $phone", style = MaxTimeType.body, modifier = Modifier.padding(horizontal = 24.dp))
                Box(
                    Modifier.padding(horizontal = 24.dp, vertical = 10.dp).fillMaxWidth()
                        .clip(MaxTimeShapes.medium).background(MaxTimeColors.cardBg)
                        .border(1.dp, MaxTimeColors.accent, MaxTimeShapes.medium)
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    MaxTimeTextField(otp, { if (it.length <= 6) otp = it }, "6-digit code", KeyboardType.NumberPassword)
                }
                errorText?.let {
                    Text(it, color = MaxTimeColors.red, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().background(MaxTimeColors.bg1).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    PrimaryButton(if (isLoading) "Verifying…" else "Verify", onClick = ::submitOtp, enabled = !isLoading && otp.length == 6)
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(state: AppState, onContinue: () -> Unit) {
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val profileRepository = remember { UserProfileRepository() }

    fun continueTapped() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            onContinue()
            return
        }
        errorText = null
        isSaving = true
        scope.launch {
            try {
                profileRepository.saveProfile(uid, state.currentUser.name, Firebase.auth.currentUser?.phoneNumber)
                profileRepository.loadProfile(uid)
                onContinue()
            } catch (e: Exception) {
                isSaving = false
                errorText = e.message ?: "Couldn't save your profile. Try again."
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OnboardingProgressHeader(step = 3)
        Text("Add your name and photo", style = MaxTimeType.heading, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text("This is how others recognize you at an activity.", style = MaxTimeType.body, modifier = Modifier.padding(horizontal = 24.dp))
        Box(
            Modifier.padding(top = 6.dp, bottom = 22.dp).align(Alignment.CenterHorizontally)
                .size(104.dp).clip(CircleShape).background(MaxTimeColors.cardBg)
                .border(2.dp, MaxTimeColors.cardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("+", color = MaxTimeColors.muted, fontSize = 26.sp) }
        FieldLabel("Full name")
        FieldValueBox(state.currentUser.name)
        FieldLabel("Date of birth")
        FieldValueBox("14 / 03 / 1998")
        errorText?.let {
            Text(it, color = MaxTimeColors.red, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().background(MaxTimeColors.bg1).padding(horizontal = 24.dp, vertical = 16.dp)) {
            PrimaryButton(if (isSaving) "Saving…" else "Continue", onClick = ::continueTapped, enabled = !isSaving)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsScreen(onFinish: () -> Unit) {
    var selected: Map<String, Set<String>> by remember {
        mutableStateOf(
            mapOf<String, Set<String>>(
                "Fitness" to setOf("Yoga"),
                "Sports" to setOf("Badminton", "Football"),
                "Creative" to emptySet(),
                "Learning" to emptySet(),
                "Outdoor" to setOf("Running"),
                "Social" to emptySet()
            )
        )
    }

    Column(Modifier.fillMaxSize()) {
        OnboardingProgressHeader(step = 4)
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
        Column(Modifier.fillMaxWidth().background(MaxTimeColors.bg1).padding(horizontal = 24.dp, vertical = 16.dp)) {
            PrimaryButton("Finish — Go to Home", onClick = onFinish)
        }
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
private fun FieldValueBox(text: String) {
    Box(
        Modifier.padding(horizontal = 24.dp, vertical = 0.dp).padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(MaxTimeShapes.medium)
            .background(MaxTimeColors.cardBg)
            .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) { Text(text, color = MaxTimeColors.muted, fontSize = 13.5.sp) }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(MaxTimeColors.cardBorder))
        Text("or continue with", color = MaxTimeColors.muted, fontSize = 11.sp)
        Box(Modifier.weight(1f).height(1.dp).background(MaxTimeColors.cardBorder))
    }
}

@Composable
private fun MaxTimeTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, keyboardType: KeyboardType) {
    Box {
        if (value.isEmpty()) {
            Text(placeholder, color = MaxTimeColors.muted, fontSize = 13.5.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = MaxTimeColors.textStrong, fontSize = 13.5.sp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaxTimeColors.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
