package org.monfluo.wallet.lib.data

data class MoneroHash(
    val hash: ByteArray,
) {
    override fun toString(): String = hash.toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MoneroHash

        if (!hash.contentEquals(other.hash)) return false

        return true
    }

    override fun hashCode(): Int = hash.contentHashCode()
}
