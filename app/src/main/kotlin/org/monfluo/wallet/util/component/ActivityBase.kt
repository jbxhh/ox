package org.monfluo.wallet.util.component

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import org.monfluo.wallet.util.PreferenceUtils
import timber.log.Timber

abstract class ActivityBase : AppCompatActivity() {
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
        if (PreferenceUtils.isSecureFlagEnabled(applicationContext)) {
            Timber.d("Adding FLAG_SECURE to window")
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            Timber.d("Clearing FLAG_SECURE from window")
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
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
