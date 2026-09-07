package com.doodlr.aeslocker

import android.app.Activity
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class ConsentAndAgeManager(private val activity: Activity) {

    fun gatherConsentAndAgeSignals(onConsentGathered: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // By the time this network callback returns, the activity that kicked it
                // off may already be finishing or destroyed (e.g. the user switched
                // language before consent info finished loading). Showing a consent form
                // against a torn-down window is a plausible cause of window/decor crashes
                // on whatever activity instance is being created next, so bail out here.
                if (activity.isFinishing || activity.isDestroyed) {
                    return@requestConsentInfoUpdate
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (activity.isFinishing || activity.isDestroyed) {
                        return@loadAndShowConsentFormIfRequired
                    }
                    if (formError == null) {
                        checkAgeSignals()
                        onConsentGathered()
                    }
                }
            },
            { error ->
                onConsentGathered()
            }
        )
    }

    private fun checkAgeSignals() {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            val ageSignalsManager: AgeSignalsManager = AgeSignalsManagerFactory.create(activity)
            val request = AgeSignalsRequest.builder().build()

            ageSignalsManager.checkAgeSignals(request)
                .addOnSuccessListener { ageSignalsResult ->
                    // Age signals retrieved successfully
                }
                .addOnFailureListener { e ->
                    // Silently fail if unavailable in current region or device
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}