package org.monfluo.wallet.util

object Constants {
    const val PREFERENCES_KEY = "monfluo"

    const val FOREGROUND_SERVICE_NOTIFICATION_CHANNEL = "FOREGROUND_SERVICE_NOTIFICATION_CHANNEL"
    const val TRANSFERS_NOTIFICATION_CHANNEL = "TRANSFERS_NOTIFICATION_CHANNEL"

    const val DEFAULT_WALLET_NAME = "xmr_wallet"
    const val MNEMONIC_LANGUAGE = "English"
    const val STREET_MODE_BALANCE = "#.############"

    const val PREF_USE_PROXY = "pref_use_proxy"
    const val PREF_PROXY = "pref_proxy"
    const val PREF_NODE = "pref_node"
    const val PREF_NODES = "pref_nodes"
    const val PREF_STREET_MODE = "pref_street_mode"
    const val PREF_ALLOW_FEE_OVERRIDE = "pref_allow_fee_override"
    const val PREF_ENABLE_MULTIPLE_WALLETS = "pref_enable_multiple_wallets"
    const val PREF_ENABLE_MULTIPLE_ACCOUNTS = "pref_enable_multiple_accounts"
    const val PREF_ALLOW_COPYING_SECRETS = "pref_allow_copying_secrets"
    const val PREF_SORT_NODES_ASCENDING = "pref_sort_nodes_ascending"
    const val PREF_SHOW_TRANSFER_NOTIFICATIONS = "pref_show_transfer_notifications"

    const val URI_PREFIX = "monero:"
    const val URI_ARG_AMOUNT = "tx_amount"
    const val URI_ARG_AMOUNT2 = "amount"
    const val NAV_ARG_TXINFO = "nav_arg_txinfo"

    const val EXTRA_PREVENT_GOING_BACK = "prevent_going_back"
    const val EXTRA_SEND_ADDRESS = "send_address"
    const val EXTRA_SEND_AMOUNT = "send_amount"
    const val EXTRA_SEND_MAX = "send_max"
    const val EXTRA_SEND_ENOTES = "send_enotes"
    const val EXTRA_TRANSACTION_ID = "transaction_id"

    // Donation address is also specified in strings.xml, it is used as a tooltip in address fields
    const val DONATE_ADDRESS = "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ"

    const val CONFIRMATIONS: Int = 10
}