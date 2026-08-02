package com.norvexa.clearup

import android.app.Application
import com.norvexa.clearup.app.AppContainer

class ClearUpApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
