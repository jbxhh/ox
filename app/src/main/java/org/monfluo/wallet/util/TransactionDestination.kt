package org.monfluo.wallet.util

import android.os.Parcel
import android.os.Parcelable

data class TransactionDestination(val address: String, val amount: Long) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(address)
        parcel.writeLong(amount)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TransactionDestination> {
        override fun createFromParcel(parcel: Parcel): TransactionDestination {
            return TransactionDestination(parcel)
        }

        override fun newArray(size: Int): Array<TransactionDestination?> {
            return arrayOfNulls(size)
        }
    }
}