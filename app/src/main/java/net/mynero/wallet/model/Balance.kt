package net.mynero.wallet.model

data class Balance(val total: Long, val unlocked: Long, val frozen: Long, val pending: Long)
