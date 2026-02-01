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
package org.monfluo.wallet.model

import org.monfluo.wallet.util.Constants

data class TransactionInfo(
    val direction: Direction,
    val isPending: Boolean,
    val isFailed: Boolean,
    val isCoinbase: Boolean,
    val amount: Long,
    val fee: Long,
    val blockheight: Long,
    val hash: String,
    val timestamp: Long,
    val paymentId: String,
    val accountIndex: Int,
    val addressIndex: Int,
    val confirmations: Long,
    val transfers: List<Transfer>,
    val addressValue: String? = null,
    val key: String? = null,
    val description: String? = null,
) {

    constructor(
        direction: Int,
        isPending: Boolean,
        isFailed: Boolean,
        isCoinbase: Boolean,
        amount: Long,
        fee: Long,
        blockheight: Long,
        hash: String,
        timestamp: Long,
        paymentId: String,
        accountIndex: Int,
        addressIndex: Int,
        confirmations: Long,
        transfers: List<Transfer>
    ) : this(
        Direction.fromInteger(direction),
        isPending,
        isFailed,
        isCoinbase,
        amount,
        fee,
        blockheight,
        hash,
        timestamp,
        paymentId,
        accountIndex,
        addressIndex,
        confirmations,
        transfers,
    )

    val isConfirmed: Boolean
        get() = confirmations >= Constants.CONFIRMATIONS

    override fun toString(): String {
        return "$direction@$blockheight $amount"
    }

    enum class Direction(val value: Int) {

        In(0),
        Out(1);

        companion object {
            fun fromInteger(n: Int): Direction {
                return when (n) {
                    0 -> In
                    else -> Out
                }
            }
        }
    }
}
