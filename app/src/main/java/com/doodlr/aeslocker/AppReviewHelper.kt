package com.doodlr.aeslocker

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

class AppReviewHelper(private val activity: Activity) {
    private val reviewManager = ReviewManagerFactory.create(activity)
    private val prefs = activity.getSharedPreferences("app_review_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_OP_COUNT = "operation_count"
        private const val KEY_HAS_REVIEWED = "has_reviewed_app"
        private const val TRIGGER_INTERVAL = 50 // Trigger every 50 successful operations
    }

    fun notifyOperationCompleted() {
        // Stop if the user has already completed a review session
        if (prefs.getBoolean(KEY_HAS_REVIEWED, false)) return

        val currentCount = prefs.getInt(KEY_OP_COUNT, 0) + 1
        prefs.edit().putInt(KEY_OP_COUNT, currentCount).apply()

        // Prompt only on every 50th successful operation
        if (currentCount % TRIGGER_INTERVAL == 0) {
            requestReview()
        }
    }

    private fun requestReview() {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    // Mark as reviewed so the app stops asking on future 15x intervals
                    prefs.edit().putBoolean(KEY_HAS_REVIEWED, true).apply()
                }
            }
        }
    }
}