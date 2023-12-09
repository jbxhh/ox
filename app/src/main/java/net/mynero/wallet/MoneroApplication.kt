package net.mynero.wallet

import android.app.Application
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.util.NightmodeHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MoneroApplication : Application() {
    var executor: ExecutorService? = null
        private set

    override fun onCreate() {
        super.onCreate()
        PrefService(this)
        ProxyService(this)
        NightmodeHelper.preferredNightmode
        executor = Executors.newFixedThreadPool(16)
    }
}