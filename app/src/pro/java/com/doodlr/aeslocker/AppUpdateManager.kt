package com.doodlr.aeslocker

import android.app.Activity
import android.content.Context

// Pro flavor intentionally ships no in-app update mechanism — this is a
// no-op stand-in so shared code (MainActivity) can call the same API
// regardless of flavor, without pulling in the Play Core App Update library.
class AppUpdateHelper(private val context: Context) {

    fun checkForUpdate(activity: Activity) {
        // No-op — Pro flavor does not check for or offer in-app updates.
    }
}