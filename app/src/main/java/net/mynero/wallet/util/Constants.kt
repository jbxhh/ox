package net.mynero.wallet.util

object Constants {
    const val WALLET_NAME = "xmr_wallet"
    const val MNEMONIC_LANGUAGE = "English"
    const val PREF_USES_PASSWORD = "pref_uses_password"
    const val PREF_USES_PROXY = "pref_uses_tor"
    const val PREF_PROXY = "pref_proxy"
    const val PREF_NODE_2 = "pref_node_2"
    const val PREF_CUSTOM_NODES = "pref_custom_nodes"
    const val PREF_USES_OFFSET = "pref_uses_offset"
    const val PREF_STREET_MODE = "pref_street_mode"
    const val PREF_MONEROCHAN = "pref_monerochan"
    const val PREF_DONATE_PER_TX = "pref_donate_per_tx"
    const val PREF_FROZEN_COINS = "pref_frozen_coins"
    const val PREF_USE_BUNDLED_TOR = "pref_use_bundled_tor"

    const val URI_PREFIX = "monero:"
    const val URI_ARG_AMOUNT = "tx_amount"
    const val URI_ARG_AMOUNT2 = "amount"
    const val NAV_ARG_TXINFO = "nav_arg_txinfo"
    const val STREET_MODE_BALANCE = "#.############"

    const val DEFAULT_PREF_MONEROCHAN = false

    val DONATION_ADDRESSES = arrayOf(
        "87BqQYkugEzh6Tuyotm2uc3DzJzKM6MuZaC161e6u1TsQxxPmXVPHpdNRyK47JY4d1hhbe25YVz4e9vTXCLDxvHkRXEAeBC",  // primary Mysu Donation address
        "89QoPxs4cQSGbJrJddwzV3Ca7s2gVYHE1Xd1hGZafuVJVyNKt2LCQhxUdBF57PemxQiX3dmGUZLRRAzfeYyh9pq3GiWsDVo",  // second Mysu Donation address
        "855acibBehTQJdC1f41BHWGq1FiQ5ztiAU7LiJgDUmmyJfDtRpJoo6Mc1en73duUScdeUYjLirACnZpv2C6pPbcZKgumdCS" // third Mysu Donation address
    )
    const val DONATE_ADDRESS =
        "87BqQYkugEzh6Tuyotm2uc3DzJzKM6MuZaC161e6u1TsQxxPmXVPHpdNRyK47JY4d1hhbe25YVz4e9vTXCLDxvHkRXEAeBC"
}