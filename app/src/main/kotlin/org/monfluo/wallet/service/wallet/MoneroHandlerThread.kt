package org.monfluo.wallet.service.wallet

import android.os.Looper

class MoneroHandlerThread(
    private val onLooperCreated: ((Looper) -> Unit),
) : Thread(null, null, "Monero", THREAD_STACK_SIZE) {
    companion object {
        // from src/cryptonote_config.h
        const val THREAD_STACK_SIZE = (5 * 1024 * 1024).toLong()
    }

    override fun run() {
        Looper.prepare()
        val looper = Looper.myLooper()!!
        onLooperCreated(looper)
        Looper.loop()
    }
}
