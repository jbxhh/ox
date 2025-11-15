package org.monfluo.wallet.util

object Constants {
    const val PREFERENCES_KEY = "monfluo"

    const val FOREGROUND_SERVICE_NOTIFICATION_CHANNEL = "FOREGROUND_SERVICE_NOTIFICATION_CHANNEL"
    const val TRANSFERS_NOTIFICATION_CHANNEL = "TRANSFERS_NOTIFICATION_CHANNEL"

    const val DEFAULT_WALLET_NAME = "Monero Wallet"
    const val MNEMONIC_LANGUAGE = "English"

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
    const val PREF_SHOW_ICON_ON_QR_CODES = "pref_show_icon_on_qr_codes"
    const val PREF_AUTO_ROTATE_ADDRESSES = "pref_auto_rotate_addresses"
    const val PREF_ENABLE_ENOTE_MANAGEMENT = "pref_enable_enote_management"
    const val PREF_BALANCE_TAP_TOGGLES_STREET_MODE = "pref_balance_tap_toggles_street_mode"
    const val PREF_MAX_BRIGHTNESS_QR_CODES = "pref_max_brightness_qr_codes"
    const val PREF_FORCE_URI_FORMAT_QR_CODES = "pref_force_uri_format_qr_codes"
    const val PREF_POST_NOTIFICATIONS_PERMISSION_REQUESTED = "pref_post_notifications_permission_requested"

    const val URI_ARG_AMOUNT = "tx_amount"
    const val URI_ARG_AMOUNT2 = "amount"

    const val EXTRA_WALLET_PATH = "wallet_path"
    const val EXTRA_WALLET_PASSWORD = "wallet_password"
    const val EXTRA_PREVENT_GOING_BACK = "prevent_going_back"
    const val EXTRA_PAYMENT_URI = "payment_uri"
    const val EXTRA_PAYMENT_ENOTES = "payment_enotes"
    const val EXTRA_TRANSACTION_ID = "transaction_id"
    const val EXTRA_ACCOUNT_INDEX = "account_index"
    const val EXTRA_ADDRESS_INDEX = "address_index"
    const val EXTRA_ADDRESS_VALUE = "address_value"
    const val EXTRA_DAEMON_ADDRESS = "daemon_address"
    const val EXTRA_DAEMON_USERNAME = "daemon_username"
    const val EXTRA_DAEMON_PASSWORD = "daemon_password"
    const val EXTRA_DAEMON_TRUSTED = "daemon_trusted"
    const val EXTRA_ADDRESS_LABEL = "address_label"
    const val EXTRA_ACCOUNT_LABEL = "account_label"
    const val EXTRA_TRANSACTION_DESTINATION = "transaction_destination"
    const val EXTRA_TRANSACTION_DESTINATIONS = "transaction_destinations"
    const val EXTRA_TRANSACTION_PRIORITY = "transaction_priority"
    const val EXTRA_TRANSACTION_SELECTED_ENOTES = "transaction_selected_enotes"
    const val EXTRA_TRANSACTION_SEND_ALL = "transaction_send_all"
    const val EXTRA_PENDING_TRANSACTION = "pending_transaction"
    const val EXTRA_PROXY = "proxy"
    const val EXTRA_ENOTE = "enote"
    const val EXTRA_ENOTES = "enotes"
    const val EXTRA_NODE_NAME = "node_name"

    // Donation address is also specified in strings.xml, it is used as a tooltip in address fields
    const val DONATE_ADDRESS = "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ"

    const val CONFIRMATIONS: Int = 10
}
