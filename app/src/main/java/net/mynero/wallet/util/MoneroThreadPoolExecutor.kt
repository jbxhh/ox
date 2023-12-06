/*
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
package net.mynero.wallet.util

import net.mynero.wallet.service.MoneroHandlerThread
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object MoneroThreadPoolExecutor {
    @JvmField
    var MONERO_THREAD_POOL_EXECUTOR: Executor? = null
    private val CPU_COUNT = Runtime.getRuntime().availableProcessors()
    private val CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4))
    private val MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1
    private const val KEEP_ALIVE_SECONDS = 30
    private val sThreadFactory: ThreadFactory = object : ThreadFactory {
        private val mCount = AtomicInteger(1)
        override fun newThread(r: Runnable): Thread {
            return Thread(
                null,
                r,
                "MoneroTask #" + mCount.getAndIncrement(),
                MoneroHandlerThread.THREAD_STACK_SIZE
            )
        }
    }
    private val sPoolWorkQueue: BlockingQueue<Runnable> = LinkedBlockingQueue(128)

    init {
        val threadPoolExecutor = ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS.toLong(), TimeUnit.SECONDS,
            sPoolWorkQueue, sThreadFactory
        )
        threadPoolExecutor.allowCoreThreadTimeOut(true)
        MONERO_THREAD_POOL_EXECUTOR = threadPoolExecutor
    }
}