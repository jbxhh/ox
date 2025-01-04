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

data class TransactionInfo(
    var direction: Direction,
    var isPending: Boolean,
    var isFailed: Boolean,
    var amount: Long,
    var fee: Long,
    var blockheight: Long,
    var hash: String?,
    var timestamp: Long,
    var paymentId: String?,
    var accountIndex: Int,
    var addressIndex: Int,
    var confirmations: Long,
    var subaddressLabel: String?,
    var transfers: List<Transfer>? = listOf(),
    var txKey: String? = null,
    var notes: String? = null,
    var address: String? = null
) {

    constructor(
        direction: Int,
        isPending: Boolean,
        isFailed: Boolean,
        amount: Long,
        fee: Long,
        blockheight: Long,
        hash: String?,
        timestamp: Long,
        paymentId: String?,
        accountIndex: Int,
        addressIndex: Int,
        confirmations: Long,
        subaddressLabel: String?,
        transfers: List<Transfer>?
    ) : this(
        Direction.values()[direction],
        isPending,
        isFailed,
        amount,
        fee,
        blockheight,
        hash,
        timestamp,
        paymentId,
        accountIndex,
        addressIndex,
        confirmations,
        subaddressLabel,
        transfers
    )

    val isConfirmed: Boolean
        get() = confirmations >= CONFIRMATION

    override fun toString(): String {
        return "$direction@$blockheight $amount"
    }

    enum class Direction(val value: Int) {
        Direction_In(0), Direction_Out(1);

        companion object {
            fun fromInteger(n: Int): Direction {
                return when (n) {
                    0 -> Direction_In
                    else -> Direction_Out
                }
            }
        }
    }

    companion object {
        const val CONFIRMATION = 10 // blocks
    }
}