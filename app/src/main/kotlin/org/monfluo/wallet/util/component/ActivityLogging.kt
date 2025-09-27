package org.monfluo.wallet.util.component

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

abstract class ActivityLogging : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("Activity ${javaClass.simpleName} is being created")
    }

    override fun onStart() {
        super.onStart()
        Timber.d("Activity ${javaClass.simpleName} is being started")
    }

    override fun onResume() {
        super.onResume()
        Timber.d("Activity ${javaClass.simpleName} is being resumed")
    }

    override fun onPause() {
        super.onPause()
        Timber.d("Activity ${javaClass.simpleName} is being paused")
    }

    override fun onStop() {
        super.onStop()
        Timber.d("Activity ${javaClass.simpleName} is being stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("Activity ${javaClass.simpleName} is being destroyed")
    }
}
