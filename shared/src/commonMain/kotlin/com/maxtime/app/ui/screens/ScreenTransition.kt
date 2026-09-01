package com.maxtime.app.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun <T> ScreenTransition(key: T, content: @Composable (T) -> Unit) {
    content(key)
}
