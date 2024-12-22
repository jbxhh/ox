/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.mynero.wallet.service

import android.os.Looper

class MoneroHandlerThread(private var onLooperCreated: ((Looper) -> Unit)?) : Thread(null, null, "Monero", THREAD_STACK_SIZE) {

    companion object {
        // from src/cryptonote_config.h
        const val THREAD_STACK_SIZE = (5 * 1024 * 1024).toLong()
    }

    override fun run() {
        Looper.prepare();
        val looper = Looper.myLooper()!!

        onLooperCreated?.let { callback -> callback(looper) }
        onLooperCreated = null

        Looper.loop()
    }
}