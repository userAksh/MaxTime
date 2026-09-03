package com.maxtime.android

import android.app.Application
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maxtime.app.data.location.AppContextHolder
import com.maxtime.app.MaxTimeApp as MaxTimeAppRoot

class MaxTimeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This app's background is always dark (MaxTimeColors), regardless of the device's
        // system light/dark mode setting — SystemBarStyle.dark() pins status/nav bar icons to
        // light/white unconditionally, instead of enableEdgeToEdge()'s default of following
        // the system theme (which would render invisible dark icons in system light mode).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            MaxTimeAppRoot()
        }
    }
}
