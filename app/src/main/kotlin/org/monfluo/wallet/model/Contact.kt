package org.monfluo.wallet.model

import android.os.Parcel
import android.os.Parcelable

const val NOT_SAVED: Int = -1

data class Contact(
    val index: Int,
    val name: String,
    val address: String,
) : Parcelable {
    constructor(name: String, address: String) : this(NOT_SAVED, name, address)

    override fun describeContents(): Int = 0

    override fun writeToParcel(
        dest: Parcel,
        flags: Int,
    ) {
        dest.writeInt(index)
        dest.writeString(name)
        dest.writeString(address)
    }

    companion object CREATOR : Parcelable.Creator<Contact> {
        override fun createFromParcel(parcel: Parcel): Contact = Contact(parcel.readInt()!!, parcel.readString()!!, parcel.readString()!!)

        override fun newArray(size: Int): Array<Contact?> = arrayOfNulls(size)
    }
}
