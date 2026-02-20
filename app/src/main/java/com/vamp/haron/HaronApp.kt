package com.vamp.haron

import android.app.Application
import com.vamp.core.db.EcosystemDatabase
import com.vamp.core.db.EcosystemPreferences
import com.vamp.core.logger.EcosystemLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HaronApp : Application() {

    override fun onCreate() {
        super.onCreate()
        EcosystemPreferences.init(this)
        EcosystemLogger.init(this)
        EcosystemDatabase.getInstance(this)
    }
}
