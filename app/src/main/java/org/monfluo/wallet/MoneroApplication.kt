package org.monfluo.wallet

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.monfluo.wallet.util.Constants.FOREGROUND_SERVICE_NOTIFICATION_CHANNEL
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
        NightmodeHelper.setPreferredNightmode()
        registerForegroundServiceNotificationChannel()
    }

    private fun registerForegroundServiceNotificationChannel() {
        val name = "Monfluo wallet service"
        val importance = NotificationManager.IMPORTANCE_LOW
        val foregroundServiceNotificationChannel = NotificationChannel(FOREGROUND_SERVICE_NOTIFICATION_CHANNEL, name, importance)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(foregroundServiceNotificationChannel)
    }
}
