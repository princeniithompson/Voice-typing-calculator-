package com.example

import android.app.Application
import com.google.android.material.color.DynamicColors

class QuickCalcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.example.diag.Diag.init(this)
        com.example.diag.Diag.log("LIFECYCLE", "QuickCalcApp onCreate initialized")
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
