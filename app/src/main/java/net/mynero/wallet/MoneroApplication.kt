package net.mynero.wallet

import android.app.Application
import net.mynero.wallet.util.NightmodeHelper
import timber.log.Timber

class MoneroApplication : Application() {

    companion object {
        init {
            System.loadLibrary("monerujo")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        NightmodeHelper.preferredNightmode
    }
}