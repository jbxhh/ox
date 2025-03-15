package org.monfluo.wallet.data

enum class SortOrder {

    ASCENDING,
    DESCENDING;

    companion object {
        fun fromBoolean(ascending: Boolean): SortOrder = if (ascending) ASCENDING else DESCENDING
    }

    fun toBoolean(): Boolean = this == ASCENDING

    fun reverse(): SortOrder = when (this) {
        ASCENDING -> DESCENDING
        DESCENDING -> ASCENDING
    }

    fun <T> sort(list: List<T>): List<T> = if (toBoolean()) list else list.reversed()
}