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
package net.mynero.wallet.data

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import net.mynero.wallet.model.PendingTransaction
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.util.Helper

// https://stackoverflow.com/questions/2139134/how-to-send-an-object-from-one-android-activity-to-another-using-intents
class TxData : Parcelable {
    var destinationAddress: String? = null
    var amount: Long = 0
    var mixin = 0
    var priority: PendingTransaction.Priority? = null
    var userNotes: UserNotes? = null
    var preferredInputs: ArrayList<String>? = null
        private set

    constructor()
    constructor(txData: TxData) {
        destinationAddress = txData.destinationAddress
        amount = txData.amount
        mixin = txData.mixin
        priority = txData.priority
        preferredInputs = txData.preferredInputs
    }

    constructor(
        dstAddr: String?,
        amount: Long,
        mixin: Int,
        priority: PendingTransaction.Priority?,
        preferredInputs: ArrayList<String>?
    ) {
        destinationAddress = dstAddr
        this.amount = amount
        this.mixin = mixin
        this.priority = priority
        this.preferredInputs = preferredInputs
    }

    protected constructor(`in`: Parcel) {
        destinationAddress = `in`.readString()
        amount = `in`.readLong()
        mixin = `in`.readInt()
        priority = PendingTransaction.Priority.fromInteger(`in`.readInt())
        `in`.readStringList(preferredInputs!!)
    }

    fun setAmount(amount: Double) {
        this.amount = Wallet.getAmountFromDouble(amount)
    }

    val amountAsDouble: Double
        get() = 1.0 * amount / Helper.ONE_XMR

    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeString(destinationAddress)
        out.writeLong(amount)
        out.writeInt(mixin)
        out.writeInt(priority!!.value)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun toString(): String {
        val sb = StringBuffer()
        sb.append("dstAddr:")
        sb.append(destinationAddress)
        sb.append(",amount:")
        sb.append(amount)
        sb.append(",mixin:")
        sb.append(mixin)
        sb.append(",priority:")
        sb.append(priority)
        return sb.toString()
    }

    companion object CREATOR : Creator<TxData> {
        override fun createFromParcel(parcel: Parcel): TxData {
            return TxData(parcel)
        }

        override fun newArray(size: Int): Array<TxData?> {
            return arrayOfNulls(size)
        }
    }
}