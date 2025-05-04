package org.monfluo.wallet.model

import android.os.Parcel
import android.os.Parcelable

data class TransactionDestinationWithAmount(val value: String, val amount: Long) : Parcelable {

    fun withoutAmount(): TransactionDestination {
        return TransactionDestination(value)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(value)
        parcel.writeLong(amount)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TransactionDestinationWithAmount> {
        override fun createFromParcel(parcel: Parcel): TransactionDestinationWithAmount {
            return TransactionDestinationWithAmount(
                parcel.readString()!!,
                parcel.readLong()
            )
        }

        override fun newArray(size: Int): Array<TransactionDestinationWithAmount?> {
            return arrayOfNulls(size)
        }
    }
}
