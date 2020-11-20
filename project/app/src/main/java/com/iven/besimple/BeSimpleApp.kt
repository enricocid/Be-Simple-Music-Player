package com.iven.besimple

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.iven.besimple.helpers.ThemeHelper

val beSimplePreferences: BeSimplePreferences by lazy {
    GoApp.prefs
}

class GoApp : Application() {

    companion object {
        lateinit var prefs: BeSimplePreferences
    }

    override fun onCreate() {
        super.onCreate()
        prefs = BeSimplePreferences(applicationContext)
        AppCompatDelegate.setDefaultNightMode(ThemeHelper.getDefaultNightMode(applicationContext))
    }
}
