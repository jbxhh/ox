package org.monfluo.wallet.model

import android.os.Parcel
import android.os.Parcelable

data class TransactionDestinationRaw(
    var value: String = "",
    var amount: String = "",
    var recipientName: String = ""
) : Parcelable {

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(value)
        parcel.writeString(amount)
        parcel.writeString(recipientName)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TransactionDestinationRaw> {

        override fun createFromParcel(parcel: Parcel): TransactionDestinationRaw {
            return TransactionDestinationRaw(
                parcel.readString()!!,
                parcel.readString()!!,
                parcel.readString()!!
            )
        }

        override fun newArray(size: Int): Array<TransactionDestinationRaw?> {
            return arrayOfNulls(size)
        }
    }
}
