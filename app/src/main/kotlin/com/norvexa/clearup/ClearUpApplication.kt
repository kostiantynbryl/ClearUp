package com.norvexa.clearup

import android.app.Application
import com.norvexa.clearup.app.AppContainer
import com.norvexa.clearup.automation.ClearUpNotifications

class ClearUpApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        ClearUpNotifications.createChannels(this)
    }
}
