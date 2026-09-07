package com.doodlr.aeslocker

import android.app.Activity
import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class AppUpdateHelper(private val context: Context) {
    private val appUpdateManager = AppUpdateManagerFactory.create(context)

    fun checkForUpdate(activity: Activity) {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                appUpdateManager.startUpdateFlow(
                    appUpdateInfo,
                    activity,
                    AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)
                )
            }
        }
    }
}