package org.monfluo.wallet.android.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object PreferenceUtils {
    fun getSharedPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(
            Constants.PREFERENCES_KEY,
            Context.MODE_PRIVATE,
        )

    fun getBoolean(
        context: Context,
        key: String,
        default: Boolean,
    ): Boolean = getSharedPreferences(context).getBoolean(key, default)

    fun setBoolean(
        context: Context,
        key: String,
        value: Boolean,
    ) = getSharedPreferences(context).edit {
        putBoolean(
            key,
            value,
        )
    }

    fun getString(
        context: Context,
        key: String,
        default: String? = null,
    ): String? = getSharedPreferences(context).getString(key, default)

    fun setString(
        context: Context,
        key: String,
        value: String,
    ) = getSharedPreferences(context).edit {
        putString(
            key,
            value,
        )
    }

    fun getInt(
        context: Context,
        key: String,
        default: Int,
    ): Int? = getSharedPreferences(context).getInt(key, default)

    fun setInt(
        context: Context,
        key: String,
        value: Int,
    ) = getSharedPreferences(context).edit {
        putInt(
            key,
            value,
        )
    }

    fun isPostNotificationsPermissionRequested(context: Context) = getBoolean(context, Constants.PREF_POST_NOTIFICATIONS_PERMISSION_REQUESTED, false)

    fun setPostNotificationsPermissionRequested(
        context: Context,
        value: Boolean = true,
    ) = setBoolean(context, Constants.PREF_POST_NOTIFICATIONS_PERMISSION_REQUESTED, value)

    fun isUseProxy(context: Context): Boolean = getBoolean(context, Constants.PREF_USE_PROXY, false)

    fun setUseProxy(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_USE_PROXY, value)

    fun isMultiWalletMode(context: Context): Boolean = getBoolean(context, Constants.PREF_ENABLE_MULTIPLE_WALLETS, false)

    fun setMultiWalletMode(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_ENABLE_MULTIPLE_WALLETS, value)

    fun isMultiAccountMode(context: Context): Boolean = getBoolean(context, Constants.PREF_ENABLE_MULTIPLE_ACCOUNTS, false)

    fun setMultiAccountMode(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_ENABLE_MULTIPLE_ACCOUNTS, value)

    fun isStreetMode(context: Context): Boolean = getBoolean(context, Constants.PREF_STREET_MODE, false)

    fun setStreetMode(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_STREET_MODE, value)

    fun isAllowFeeOverride(context: Context): Boolean = getBoolean(context, Constants.PREF_ALLOW_FEE_OVERRIDE, false)

    fun setAllowFeeOverride(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_ALLOW_FEE_OVERRIDE, value)

    fun isAllowCopyingSecrets(context: Context): Boolean = getBoolean(context, Constants.PREF_ALLOW_COPYING_SECRETS, false)

    fun setAllowCopyingSecrets(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_ALLOW_COPYING_SECRETS, value)

    fun isSortNodesAscending(context: Context): Boolean = getBoolean(context, Constants.PREF_SORT_NODES_ASCENDING, true)

    fun setSortNodesAscending(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_SORT_NODES_ASCENDING, value)

    fun isShowTransferNotifications(context: Context): Boolean = getBoolean(context, Constants.PREF_SHOW_TRANSFER_NOTIFICATIONS, false)

    fun setShowTransferNotifications(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_SHOW_TRANSFER_NOTIFICATIONS, value)

    fun isShowIconOnQrCodes(context: Context): Boolean = getBoolean(context, Constants.PREF_SHOW_ICON_ON_QR_CODES, false)

    fun setShowIconOnQrCodes(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_SHOW_ICON_ON_QR_CODES, value)

    fun isAutoRotateAddresses(context: Context): Boolean = getBoolean(context, Constants.PREF_AUTO_ROTATE_ADDRESSES, true)

    fun setAutoRotateAddresses(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_AUTO_ROTATE_ADDRESSES, value)

    fun isEnableEnoteManagement(context: Context): Boolean = getBoolean(context, Constants.PREF_ENABLE_ENOTE_MANAGEMENT, false)

    fun setEnableEnoteManagement(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_ENABLE_ENOTE_MANAGEMENT, value)

    fun isBalanceTapTogglesStreetMode(context: Context): Boolean = getBoolean(context, Constants.PREF_BALANCE_TAP_TOGGLES_STREET_MODE, false)

    fun setBalanceTapTogglesStreetMode(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_BALANCE_TAP_TOGGLES_STREET_MODE, value)

    fun isMaxBrightnessQrCodes(context: Context): Boolean = getBoolean(context, Constants.PREF_MAX_BRIGHTNESS_QR_CODES, true)

    fun setMaxBrightnessQrCodes(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_MAX_BRIGHTNESS_QR_CODES, value)

    fun isForceUriFormatQrCodes(context: Context): Boolean = getBoolean(context, Constants.PREF_FORCE_URI_FORMAT_QR_CODES, true)

    fun setForceUriFormatQrCodes(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_FORCE_URI_FORMAT_QR_CODES, value)

    fun isEnableAddressBook(context: Context): Boolean = getBoolean(context, Constants.PREF_ENABLE_ADDRESS_BOOK, false)

    fun setEnableAddressBook(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_ENABLE_ADDRESS_BOOK, value)

    fun isDeveloperModeEnabled(context: Context): Boolean = getBoolean(context, Constants.PREF_ENABLE_DEVELOPER_MODE, false)

    fun setDeveloperModeEnabled(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_ENABLE_DEVELOPER_MODE, value)

    fun isSecureFlagEnabled(context: Context): Boolean = getBoolean(context, Constants.PREF_SECURE_FLAG_ENABLED, false)

    fun setSecureFlagEnabled(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_SECURE_FLAG_ENABLED, value)

    fun isHideNotificationContent(context: Context): Boolean = getBoolean(context, Constants.PREF_HIDE_NOTIFICATION_CONTENT, false)

    fun setHideNotificationContent(
        context: Context,
        value: Boolean,
    ) = setBoolean(context, Constants.PREF_HIDE_NOTIFICATION_CONTENT, value)

    fun getProxy(context: Context): String? = getString(context, Constants.PREF_PROXY)

    fun setProxy(
        context: Context,
        value: String,
    ) = setString(context, Constants.PREF_PROXY, value)

    fun getProxyIfEnabled(context: Context): String? = if (isUseProxy(context)) getProxy(context)?.takeIf { it.isNotBlank() } else null
}
