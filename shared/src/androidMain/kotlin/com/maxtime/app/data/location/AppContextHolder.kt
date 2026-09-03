package com.maxtime.app.data.location

import android.content.Context

/** Holds the process-lifetime Application context for platform code that needs a `Context` but
 *  isn't reachable from a Composable — [reverseGeocode]'s commonMain signature takes no platform
 *  handle at all, unlike [LocationContext]/[rememberLocationContext], which go through an
 *  Activity. Set once from `Application.onCreate()`; safe to hold indefinitely since it's the
 *  Application context, not an Activity — it can't leak a destroyed screen. */
object AppContextHolder {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}
