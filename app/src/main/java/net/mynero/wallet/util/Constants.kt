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
    const val PREF_ALLOW_FEE_OVERRIDE = "pref_allow_fee_override"
    const val PREF_FROZEN_COINS = "pref_frozen_coins"
    const val PREF_USE_BUNDLED_TOR = "pref_use_bundled_tor"

    const val URI_PREFIX = "monero:"
    const val URI_ARG_AMOUNT = "tx_amount"
    const val URI_ARG_AMOUNT2 = "amount"
    const val NAV_ARG_TXINFO = "nav_arg_txinfo"
    const val STREET_MODE_BALANCE = "#.############"

    const val EXTRA_PREVENT_GOING_BACK = "prevent_going_back"
    const val EXTRA_WALLET_NAME = "wallet_name"
    const val EXTRA_WALLET_PASSWORD = "wallet_password"
    const val EXTRA_SEND_ADDRESS = "send_address"
    const val EXTRA_SEND_AMOUNT = "send_amount"
    const val EXTRA_SEND_MAX = "send_max"
    const val EXTRA_SEND_UTXOS = "send_utxos"

    const val DEFAULT_PREF_MONEROCHAN = false

    // Donation address is also specified in strings.xml, it is used as a tooltip in address fields
    const val DONATE_ADDRESS =
        "87BqQYkugEzh6Tuyotm2uc3DzJzKM6MuZaC161e6u1TsQxxPmXVPHpdNRyK47JY4d1hhbe25YVz4e9vTXCLDxvHkRXEAeBC"
}