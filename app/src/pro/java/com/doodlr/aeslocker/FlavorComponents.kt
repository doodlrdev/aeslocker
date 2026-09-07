package com.doodlr.aeslocker

import android.app.Activity
import androidx.compose.runtime.Composable

object FlavorComponents {
    fun initialize(activity: Activity) {
        // No-op: No ads or UMP tracking to initialize in the Pro flavor
    }
}

@Composable
fun BannerAdView() {
    // No-op: Renders nothing, leaving the bottom bar empty
}

@Composable
fun ProUpgradeButton() {
    // No-op: Renders nothing, removing the upgrade button from the top bar
}