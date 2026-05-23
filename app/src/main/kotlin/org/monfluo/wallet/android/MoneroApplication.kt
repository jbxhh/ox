package org.monfluo.wallet.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatDelegate
import org.monfluo.wallet.android.util.Constants.FOREGROUND_SERVICE_NOTIFICATION_CHANNEL
import org.monfluo.wallet.android.util.Constants.TRANSFERS_NOTIFICATION_CHANNEL
import timber.log.Timber

class MoneroApplication : Application() {
    companion object {
        init {
            System.loadLibrary("monfluo_oxide_jni")
        }
    }

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
        setNightMode()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        registerForegroundServiceNotificationChannel(notificationManager)
        registerTransfersNotificationChannel(notificationManager)
    }

    private fun registerForegroundServiceNotificationChannel(notificationManager: NotificationManager) {
        val name = "Monfluo wallet service"
        val importance = NotificationManager.IMPORTANCE_LOW
        val foregroundServiceNotificationChannel = NotificationChannel(FOREGROUND_SERVICE_NOTIFICATION_CHANNEL, name, importance)
        notificationManager.createNotificationChannel(foregroundServiceNotificationChannel)
    }

    private fun registerTransfersNotificationChannel(notificationManager: NotificationManager) {
        val name = "Monfluo transfers"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val transfersNotificationChannel = NotificationChannel(TRANSFERS_NOTIFICATION_CHANNEL, name, importance)
        notificationManager.createNotificationChannel(transfersNotificationChannel)
    }

    private fun setNightMode() = AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
}
