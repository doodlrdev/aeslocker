package com.doodlr.aeslocker

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
private const val AD_COOLDOWN_MS = 2 * 60 * 1000L //two minutes interval between ads
private const val MIN_OPERATION_COUNT_FOR_AD = 6 //at least 6 successful operations need to be completed before showing the ad

class InterstitialAdHelper(context: Context) {

    // Loading an ad only ever needs a Context, not an Activity. Using the
    // application context here means the load request's lifecycle is tied to
    // the process, not to whichever Activity instance happened to construct
    // this helper — so if that Activity instance is destroyed and recreated
    // (locale change, quick relaunch, etc.) mid-load, the in-flight ad load
    // has nothing stale to reference. show() below still correctly takes the
    // live Activity it's asked to show against.
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
    private var interstitialAd: InterstitialAd? = null

    private val adUnitId = appContext.getString(R.string.admob_interstitial_ad_unit_id)

    init {
        loadAd()
    }

    private fun loadAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(appContext, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                interstitialAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        loadAd()
                    }
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        interstitialAd = null
                    }
                }
            }
            override fun onAdFailedToLoad(adError: LoadAdError) {
                interstitialAd = null
            }
        })
    }

    fun notifyOperationCompleted(activity: Activity) {
        var count = prefs.getInt("interstitial_count", 0) + 1
        val lastAdTime = prefs.getLong("last_ad_timestamp", 0L)
        val currentTime = System.currentTimeMillis()

        val hasReachedCount = count >= MIN_OPERATION_COUNT_FOR_AD
        val hasEnoughTimePassed = (currentTime - lastAdTime) >= AD_COOLDOWN_MS

        if (hasReachedCount && hasEnoughTimePassed) {
            if (interstitialAd != null) {
                interstitialAd?.show(activity)
            } else {
                loadAd()
            }

            // Reset operation count and save the new timestamp ONLY when ad triggers
            prefs.edit()
                .putInt("interstitial_count", 0)
                .putLong("last_ad_timestamp", currentTime)
                .apply()
        } else {
            // Save updated count without resetting timestamp
            prefs.edit().putInt("interstitial_count", count).apply()
        }
    }
}