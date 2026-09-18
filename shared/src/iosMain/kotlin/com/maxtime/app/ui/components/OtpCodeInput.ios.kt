package com.maxtime.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.maxtime.app.ui.theme.MaxTimeColors
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIColor
import platform.UIKit.UIKeyboardTypeNumberPad
import platform.UIKit.UITextContentTypeOneTimeCode
import platform.UIKit.UITextField
import platform.UIKit.UITextFieldTextDidChangeNotification
import platform.UIKit.UIFont

/**
 * A genuinely native `UITextField` (not Compose's `BasicTextField`, which doesn't expose
 * `UITextContentType` on iOS) so `textContentType = .oneTimeCode` actually offers the SMS code
 * Apple's system detects, in the QuickType bar. Text changes are observed via
 * `UITextFieldTextDidChangeNotification` rather than a delegate's `shouldChangeCharacters...`
 * callback — simpler than reimplementing NSRange-based string splicing, since filtering the
 * already-applied text and writing it back is equivalent for pure-digit input.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun OtpCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier,
    placeholder: String,
    refocusTrigger: Any
) {
    // Holds the notification observer token so it can be torn down in onRelease — without
    // removing it, NSNotificationCenter would keep the closure (and the UITextField it
    // captures) alive indefinitely.
    var observerToken by remember { mutableStateOf<Any?>(null) }
    var fieldRef by remember { mutableStateOf<UITextField?>(null) }
    var hasFocused by remember { mutableStateOf(false) }

    // iOS only ever offers the one-time-code AutoFill suggestion for a field that is the
    // current first responder (keyboard up) at/around the moment the SMS arrives — nothing
    // was ever calling becomeFirstResponder() here, so the field could only get focus from an
    // explicit user tap, and the code almost always arrives before that tap happens. Retried
    // rather than called once: right after UIKitView inserts this view, it isn't necessarily
    // attached to a window yet, so the very first becomeFirstResponder() call can spuriously
    // return false — this keeps trying every 50ms (bounded, so a genuinely-never-attached view
    // can't spin forever) until it actually succeeds. Keyed on refocusTrigger (not Unit) and
    // resets hasFocused back to false each time, so a resend — which doesn't remount this
    // composable, since it's the same field on the same screen — still gets its own fresh
    // attempt at grabbing the keyboard for the new code, not just once ever.
    LaunchedEffect(refocusTrigger) {
        hasFocused = false
        var attempts = 0
        while (!hasFocused && attempts < 20) {
            fieldRef?.let { hasFocused = it.becomeFirstResponder() }
            attempts += 1
            delay(50)
        }
    }

    UIKitView(
        factory = {
            val field = UITextField().apply {
                text = value
                textColor = maxTimeUIColor(MaxTimeColors.textStrong)
                this.placeholder = placeholder
                font = UIFont.systemFontOfSize(13.5)
                keyboardType = UIKeyboardTypeNumberPad
                textContentType = UITextContentTypeOneTimeCode
                // A solid, opaque background matching the Compose-drawn card behind it — NOT
                // clear/transparent. This field is sized (via `modifier`, fillMaxSize from the
                // caller) to fill the visible bordered box entirely, and the default (non-overlay)
                // UIKitView compositing mode punches a transparent cut-out through Compose's own
                // canvas at exactly this view's bounds, revealing whatever's behind it in the real
                // window — which is plain black, not this screen's card background. Making the
                // native view itself opaque and correctly colored means the cut-out reveals the
                // right color directly, with no need to hand compositing over to overlay mode.
                // Overlay mode was tried first and technically fixed that black patch, but it also
                // documented-ly lets the interop view ignore normal Compose clipping/z-order —
                // which is exactly what caused this field to visually escape its scrollable
                // container and collide with the top bar's back button while scrolled, and to
                // swallow taps that should have fallen through to dismiss the keyboard. Reverting
                // to the default (properly clipped/z-ordered) mode fixes both of those too.
                backgroundColor = maxTimeUIColor(MaxTimeColors.cardBg)
                opaque = true
            }
            fieldRef = field
            observerToken = NSNotificationCenter.defaultCenter.addObserverForName(
                name = UITextFieldTextDidChangeNotification,
                `object` = field,
                queue = NSOperationQueue.mainQueue,
                usingBlock = {
                    val raw = field.text ?: ""
                    val filtered = raw.filter { it.isDigit() }.take(6)
                    if (filtered != raw) field.text = filtered
                    onValueChange(filtered)
                    if (filtered.length == 6) onComplete()
                }
            )
            field
        },
        modifier = modifier,
        update = { field -> if (field.text != value) field.text = value },
        onRelease = {
            observerToken?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun maxTimeUIColor(color: androidx.compose.ui.graphics.Color): UIColor = UIColor(
    red = color.red.toDouble(),
    green = color.green.toDouble(),
    blue = color.blue.toDouble(),
    alpha = color.alpha.toDouble()
)
