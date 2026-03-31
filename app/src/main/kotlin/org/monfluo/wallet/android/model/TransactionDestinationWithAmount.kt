package org.monfluo.wallet.android.model

import android.os.Parcel
import android.os.Parcelable

data class TransactionDestinationWithAmount(
    val value: String,
    val amount: Long,
) : Parcelable {
    fun withoutAmount(): TransactionDestination = TransactionDestination(value)

    override fun writeToParcel(
        parcel: Parcel,
        flags: Int,
    ) {
        parcel.writeString(value)
        parcel.writeLong(amount)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TransactionDestinationWithAmount> {
        override fun createFromParcel(parcel: Parcel): TransactionDestinationWithAmount =
            TransactionDestinationWithAmount(
                parcel.readString()!!,
                parcel.readLong(),
            )

        override fun newArray(size: Int): Array<TransactionDestinationWithAmount?> = arrayOfNulls(size)
    }
}
