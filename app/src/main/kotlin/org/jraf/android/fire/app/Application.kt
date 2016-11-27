package org.jraf.android.fire.app

import org.jraf.android.util.log.Log

class Application : android.app.Application() {
    companion object {
        private const val LOG_TAG = "FIRE"
    }

    override fun onCreate() {
        super.onCreate()

        Log.init(this, LOG_TAG)
    }
}