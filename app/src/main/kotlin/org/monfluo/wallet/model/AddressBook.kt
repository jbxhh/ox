package org.monfluo.wallet.model

class AddressBook(
    private val handle: Long,
) {
    external fun getContacts(): List<Contact>

    external fun addContact(contact: Contact): Boolean

    external fun setName(
        index: Int,
        name: String,
    ): Boolean

    external fun delete(index: Int): Boolean
}
