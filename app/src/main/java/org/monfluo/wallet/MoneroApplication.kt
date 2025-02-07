package org.monfluo.wallet

import android.app.Application
import org.monfluo.wallet.util.NightmodeHelper
import timber.log.Timber

class MoneroApplication : Application() {

    companion object {
        init {
            System.loadLibrary("monfluo")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        NightmodeHelper.preferredNightmode
    }
}