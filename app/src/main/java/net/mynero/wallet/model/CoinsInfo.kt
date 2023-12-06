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
package net.mynero.wallet.model

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator

class CoinsInfo : Parcelable, Comparable<CoinsInfo> {
    var globalOutputIndex: Long
    var isSpent = false
    var keyImage: String? = null
    var amount: Long = 0
    var hash: String? = null
    var pubKey: String? = null
    var isUnlocked = false
    var localOutputIndex: Long = 0
    var isFrozen = false
    var address: String? = null

    constructor(
        globalOutputIndex: Long,
        spent: Boolean,
        keyImage: String?,
        amount: Long,
        hash: String?,
        pubKey: String?,
        unlocked: Boolean,
        localOutputIndex: Long,
        frozen: Boolean,
        address: String?
    ) {
        this.globalOutputIndex = globalOutputIndex
        isSpent = spent
        this.keyImage = keyImage
        this.amount = amount
        this.hash = hash
        this.pubKey = pubKey
        isUnlocked = unlocked
        this.localOutputIndex = localOutputIndex
        isFrozen = frozen
        this.address = address
    }

    private constructor(`in`: Parcel) {
        globalOutputIndex = `in`.readLong()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, i: Int) {
        parcel.writeLong(globalOutputIndex)
    }

    override fun compareTo(other: CoinsInfo): Int {
        val b1 = amount
        val b2 = other.amount
        return if (b1 > b2) {
            -1
        } else if (b1 < b2) {
            1
        } else {
            other.hash?.let { hash?.compareTo(it) } ?: 0
        }
    }

    companion object {
        @JvmField
        val CREATOR: Creator<CoinsInfo?> = object : Creator<CoinsInfo?> {
            override fun createFromParcel(`in`: Parcel): CoinsInfo {
                return CoinsInfo(`in`)
            }

            override fun newArray(size: Int): Array<CoinsInfo?> {
                return arrayOfNulls(size)
            }
        }

        init {
            System.loadLibrary("monerujo")
        }
    }
}