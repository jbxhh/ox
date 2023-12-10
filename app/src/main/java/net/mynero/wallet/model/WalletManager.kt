/*
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.mynero.wallet.model

import android.util.Log
import net.mynero.wallet.data.Node
import net.mynero.wallet.util.RestoreHeight
import java.io.File
import java.util.Calendar
import java.util.Locale

class WalletManager {
    val networkType = NetworkType.NetworkType_Mainnet
    var wallet: Wallet? = null
        private set
    private var daemonAddress: String? = null
    var daemonUsername = ""
        private set
    var daemonPassword = ""
        private set
    var proxy = ""
        private set

    private fun manageWallet(wallet: Wallet) {
        Log.d("WalletManager.kt", "Managing ${wallet.name}")
        this.wallet = wallet
    }

    private fun unmanageWallet(wallet: Wallet?) {
        requireNotNull(wallet) { "Cannot unmanage null!" }
        checkNotNull(this.wallet) { "No wallet under management!" }
        check(this.wallet === wallet) { wallet.name + " not under management!" }
        Log.d("WalletManager.kt", "Unmanaging ${wallet.name}")
        this.wallet = null
    }

    fun createWallet(aFile: File, password: String, language: String, height: Long): Wallet {
        val walletHandle = createWalletJ(aFile.absolutePath, password, language, networkType.value)
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        if (wallet.status.isOk) {
            // (Re-)Estimate restore height based on what we know
            val oldHeight = wallet.getRestoreHeight()
            // Go back 4 days if we don't have a precise restore height
            val restoreDate = Calendar.getInstance()
            restoreDate.add(Calendar.DAY_OF_MONTH, 0)
            val restoreHeight =
                if (height > -1) height else RestoreHeight.instance?.getHeight(restoreDate.time)
            if (restoreHeight != null) {
                wallet.setRestoreHeight(restoreHeight)
            }
            Log.d("WalletManager.kt", "Changed Restore Height from $oldHeight to ${wallet.getRestoreHeight()}")
            wallet.setPassword(password) // this rewrites the keys file (which contains the restore height)
        } else Log.e("WalletManager.kt", wallet.status.toString())
        return wallet
    }

    //public native List<String> findWallets(String path); // this does not work - some error in boost
    private external fun createWalletJ(
        path: String,
        password: String,
        language: String,
        networkType: Int
    ): Long

    fun createWalletPolyseed(
        aFile: File,
        password: String,
        passphrase: String,
        language: String
    ): Wallet {
        val walletHandle = createWalletPolyseedJ(
            aFile.absolutePath,
            password,
            passphrase,
            language,
            networkType.value
        )
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        if (wallet.status.isOk) {
            wallet.setPassword(password) // this rewrites the keys file (which contains the restore height)
        } else Log.e("WalletManager.kt", wallet.status.toString())
        return wallet
    }

    private external fun createWalletPolyseedJ(
        path: String,
        password: String,
        passphrase: String,
        language: String,
        networkType: Int
    ): Long

    fun openAccount(path: String, accountIndex: Int, password: String): Wallet {
        val walletHandle = openWalletJ(path, password, networkType.value)
        val wallet = Wallet(walletHandle, accountIndex)
        manageWallet(wallet)
        return wallet
    }

    //TODO virtual bool checkPayment(const std::string &address, const std::string &txid, const std::string &txkey, const std::string &daemon_address, uint64_t &received, uint64_t &height, std::string &error) const = 0;
    fun openWallet(path: String, password: String): Wallet {
        val walletHandle = openWalletJ(path, password, networkType.value)
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        return wallet
    }

    private external fun openWalletJ(path: String, password: String, networkType: Int): Long
    fun recoveryWallet(
        aFile: File, password: String,
        mnemonic: String, offset: String,
        restoreHeight: Long
    ): Wallet {
        val walletHandle = recoveryWalletJ(
            aFile.absolutePath, password,
            mnemonic, offset,
            networkType.value, restoreHeight
        )
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        return wallet
    }

    private external fun recoveryWalletJ(
        path: String, password: String,
        mnemonic: String, offset: String,
        networkType: Int, restoreHeight: Long
    ): Long

    fun recoveryWalletPolyseed(
        aFile: File, password: String,
        mnemonic: String, offset: String
    ): Wallet {
        val walletHandle = recoveryWalletPolyseedJ(
            aFile.absolutePath, password,
            mnemonic, offset,
            networkType.value
        )
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        return wallet
    }

    private external fun recoveryWalletPolyseedJ(
        path: String, password: String,
        mnemonic: String, offset: String,
        networkType: Int
    ): Long

    fun createWalletWithKeys(
        aFile: File, password: String, language: String, restoreHeight: Long,
        addressString: String, viewKeyString: String, spendKeyString: String
    ): Wallet {
        val walletHandle = createWalletFromKeysJ(
            aFile.absolutePath, password,
            language, networkType.value, restoreHeight,
            addressString, viewKeyString, spendKeyString
        )
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        return wallet
    }

    private external fun createWalletFromKeysJ(
        path: String, password: String,
        language: String,
        networkType: Int,
        restoreHeight: Long,
        addressString: String,
        viewKeyString: String,
        spendKeyString: String
    ): Long

    fun createWalletFromDevice(
        aFile: File, password: String, restoreHeight: Long,
        deviceName: String
    ): Wallet {
        val walletHandle = createWalletFromDeviceJ(
            aFile.absolutePath, password,
            networkType.value, deviceName, restoreHeight,
            "5:20"
        )
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        return wallet
    }

    private external fun createWalletFromDeviceJ(
        path: String, password: String,
        networkType: Int,
        deviceName: String,
        restoreHeight: Long,
        subaddressLookahead: String
    ): Long

    private external fun closeJ(wallet: Wallet?): Boolean
    fun close(wallet: Wallet): Boolean {
        unmanageWallet(wallet)
        val closed = closeJ(wallet)
        if (!closed) {
            // in case we could not close it
            // we manage it again
            manageWallet(wallet)
        }
        return closed
    }

    fun walletExists(aFile: File): Boolean {
        return walletExists(aFile.absolutePath)
    }

    private external fun walletExists(path: String?): Boolean
    external fun verifyWalletPassword(
        keysFileName: String?,
        password: String?,
        watchOnly: Boolean
    ): Boolean

    fun verifyWalletPasswordOnly(keysFileName: String, password: String): Boolean {
        return queryWalletDeviceJ(keysFileName, password) >= 0
    }

    private external fun queryWalletDeviceJ(keysFileName: String, password: String): Int

    // this should not be called on the main thread as it connects to the node (and takes a long time)
    fun setDaemon(node: Node?) {
        if (node != null) {
            daemonAddress = node.address
            require(networkType === node.networkType) { "network type does not match" }
            daemonUsername = node.username
            daemonPassword = node.password
            daemonAddress?.let { addr -> setDaemonAddressJ(addr) }
        } else {
            daemonAddress = null
            daemonUsername = ""
            daemonPassword = ""
            //setDaemonAddressJ(""); // don't disconnect as monero code blocks for many seconds!
            //TODO: need to do something about that later
        }
    }

    fun getDaemonAddress(): String? {
        checkNotNull(daemonAddress) { "use setDaemon() to initialise daemon and net first!" }
        return daemonAddress
    }

    private external fun setDaemonAddressJ(address: String)
    external fun getDaemonVersion(): Int
    external fun getBlockchainHeight(): Long
    external fun getBlockchainTargetHeight(): Long
    external fun getNetworkDifficulty(): Long
    external fun getMiningHashRate(): Double
    external fun getBlockTarget(): Long
    external fun isMining(): Boolean

    external fun startMining(
        address: String?,
        backgroundMining: Boolean,
        ignoreBattery: Boolean
    ): Boolean

    external fun stopMining(): Boolean
    external fun resolveOpenAlias(address: String?, dnssec_valid: Boolean): String?
    fun setProxy(address: String): Boolean {
        proxy = address
        return setProxyJ(address)
    }

    private external fun setProxyJ(address: String?): Boolean
    inner class WalletInfo(wallet: File) : Comparable<WalletInfo> {
        private val path: File
        private val name: String

        init {
            path = wallet.parentFile
            name = wallet.name
        }

        override fun compareTo(other: WalletInfo): Int {
            return name.lowercase(Locale.getDefault())
                .compareTo(other.name.lowercase(Locale.getDefault()))
        }
    }

    companion object {
        //TODO: maybe put these in an enum like in monero core - but why?
        var LOGLEVEL_SILENT = -1
        var LOGLEVEL_WARN = 0
        var LOGLEVEL_INFO = 1
        var LOGLEVEL_DEBUG = 2
        var LOGLEVEL_TRACE = 3
        var LOGLEVEL_MAX = 4

        // no need to keep a reference to the REAL WalletManager (we get it every tvTime we need it)
        @get:Synchronized
        var instance: WalletManager? = null
            get() {
                if (field == null) {
                    field = WalletManager()
                }
                return field
            }
            private set

        init {
            System.loadLibrary("monerujo")
        }

        fun addressPrefix(networkType: NetworkType): String {
            return when (networkType) {
                NetworkType.NetworkType_Testnet -> "9A-"
                NetworkType.NetworkType_Mainnet -> "4-"
                NetworkType.NetworkType_Stagenet -> "5-"
            }
        }

        @JvmStatic
        external fun initLogger(argv0: String?, defaultLogBaseName: String?)

        @JvmStatic
        external fun setLogLevel(level: Int)

        @JvmStatic
        external fun logDebug(category: String?, message: String?)

        @JvmStatic
        external fun logInfo(category: String?, message: String?)

        @JvmStatic
        external fun logWarning(category: String?, message: String?)

        @JvmStatic
        external fun logError(category: String?, message: String?)

        @JvmStatic
        external fun moneroVersion(): String?
    }
}