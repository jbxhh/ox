package org.monfluo.wallet.model

import android.os.Parcel
import android.os.Parcelable

data class TransactionDestination(val value: String) : Parcelable {

    fun toResolved(resolvedAddress: String? = null, dnssec: Boolean = false): TransactionDestinationResolved {
        return if (resolvedAddress.isNullOrEmpty()) {
            TransactionDestinationResolved(value)
        } else {
            TransactionDestinationResolved(resolvedAddress, TransactionDestinationResolvedFrom(value, dnssec))
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(value)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TransactionDestination> {
        override fun createFromParcel(parcel: Parcel): TransactionDestination {
            return TransactionDestination(parcel.readString()!!)
        }

        override fun newArray(size: Int): Array<TransactionDestination?> {
            return arrayOfNulls(size)
        }
    }
}
