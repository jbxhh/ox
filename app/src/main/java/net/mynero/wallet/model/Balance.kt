package net.mynero.wallet.model

data class Balance(var total: Long, var unlocked: Long, var frozen: Long, var pending: Long)
