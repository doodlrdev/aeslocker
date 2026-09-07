package com.doodlr.aeslocker

import android.app.Activity
import android.content.Context

class InterstitialAdHelper(private val context: Context) {

    // Pro version does not load or show ads.
    // The method exists solely so MainActivity compiles successfully.
    fun notifyOperationCompleted(activity: Activity) {
        // Do nothing
    }
}