package com.maxtime.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.maxtime.app.ui.theme.MaxTimeColors

@Composable
actual fun OtpCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier,
    placeholder: String,
    refocusTrigger: Any
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    // Without this, the field only gets focus (keyboard up) from an explicit user tap — Android's
    // autofill suggestion strip for an SMS code needs the field focused already, so a code that
    // arrives before the user taps in never gets a chance to be offered at all. Keyed on
    // refocusTrigger (not Unit) so a resend — which doesn't remount this composable — still gets
    // its own fresh focus request for the new code.
    LaunchedEffect(refocusTrigger) { focusRequester.requestFocus() }
    // Disables the Select/Select All edit menu — no legitimate reason to select/copy/paste an
    // OTP digit field, and clearing focus programmatically on completion (below) can otherwise
    // surface it as a residual artifact (confirmed happening on the equivalent iOS phone-number
    // field via screen recording).
    val noToolbar = remember {
        object : TextToolbar {
            override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) = Unit
            override fun hide() = Unit
        }
    }
    CompositionLocalProvider(LocalTextToolbar provides noToolbar) {
        Box(modifier) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                // BasicText, not Material's Text — Text merges an ambient LocalTextStyle
                // (including its typography-scale line-height) even with explicit color/fontSize
                // overrides, which shifts it vertically relative to BasicTextField's cursor (whose
                // textStyle below has no such inheritance) even at an identical fontSize.
                BasicText(placeholder, style = TextStyle(color = MaxTimeColors.muted, fontSize = 13.5.sp))
            }
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() }.take(6)
                    onValueChange(filtered)
                    if (filtered.length == 6) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onComplete()
                    }
                },
                textStyle = TextStyle(color = MaxTimeColors.textStrong, fontSize = 13.5.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }),
                cursorBrush = SolidColor(MaxTimeColors.accent),
                // Hooks into Android's Autofill framework (not SMS Retriever — no RECEIVE_SMS
                // permission involved) so a detected SMS code can be offered/inserted here directly.
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).semantics { contentType = ContentType.SmsOtpCode }
            )
        }
    }
}
