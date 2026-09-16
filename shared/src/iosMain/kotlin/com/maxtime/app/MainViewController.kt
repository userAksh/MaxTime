package com.maxtime.app

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

// Compose Multiplatform's iOS default (OnFocusBehavior.FocusableAboveKeyboard) shrinks/scales the
// *entire* view to keep the focused field above the keyboard — screens end up visibly squished,
// clipping content like the "Continue as guest"/"Email" buttons on the auth screen. Every screen
// already handles the keyboard itself via imePadding() (see ScreenScaffold/BottomCta and the auth
// flow), so disable this platform-level behavior instead of fighting it.
fun MainViewController(): UIViewController = ComposeUIViewController(configure = {
    onFocusBehavior = OnFocusBehavior.DoNothing
}) {
    MaxTimeApp()
}
