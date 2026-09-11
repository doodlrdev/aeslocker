package com.doodlr.aeslocker

import android.app.Activity

// Pro flavor intentionally ships no in-app review prompt — this is a
// no-op stand-in so shared code (MainActivity) can call the same API
// regardless of flavor, without pulling in the Play Core Review library.
class AppReviewHelper(private val activity: Activity) {

    fun notifyOperationCompleted() {
        // No-op — Pro flavor does not request in-app reviews.
    }
}