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
import android.util.Pair
import net.mynero.wallet.data.Subaddress
import net.mynero.wallet.util.TransactionDestination
import java.io.File

class Wallet(private val handle: Long, val info: WalletInfo, private var accountIndex: Int = 0) {
    var isSynchronized = false
    private var listenerHandle: Long = 0
    private var pendingTransaction: PendingTransaction? = null
    private var enotes: List<Enote>? = null
    private var balances: Map<Int, Balance>? = null
    private var history: Map<Int, List<TransactionInfo>>? = null

    fun update() {
        refreshEnotes()
        refreshTransactionHistory()
    }


    fun getAccountIndex(): Int {
        return accountIndex
    }

    fun setAccountIndex(accountIndex: Int) {
        Log.d("Wallet.kt", "setAccountIndex($accountIndex)")
        this.accountIndex = accountIndex
    }

    private external fun getEnotesJ(): List<Enote>

    fun refreshEnotes() {
        val enotes = getEnotesJ()
        this.enotes = enotes
        this.balances = calculateBalances(enotes)
    }

    fun getEnotes(): List<Enote> {
        var enotes = enotes
        if (enotes == null) {
            enotes = getEnotesJ()
            this.enotes = enotes
            return enotes
        } else {
            return enotes
        }
    }

    fun getBalances(): Map<Int, Balance> {
        if (balances == null) {
            refreshEnotes()
        }
        return balances!!
    }

    fun getBalance(): Balance = getBalances()[accountIndex] ?: Balance(0, 0, 0, 0)

    fun refreshTransactionHistory() {
        val transactionInfos = mutableMapOf<Int, MutableList<TransactionInfo>>()
        getTransactionHistory().refreshJ().forEach {
            transactionInfos.getOrPut(it.accountIndex) { mutableListOf() }.add(it)
        }
        this.history = transactionInfos
    }

    fun getHistories(): Map<Int, List<TransactionInfo>> {
        if (history == null) {
            refreshTransactionHistory()
        }
        return history!!
    }

    fun getHistory(): List<TransactionInfo> = getHistories()[accountIndex] ?: listOf()

    private fun calculateBalances(enotes: List<Enote>): Map<Int, Balance> {
        val addressToAccount = mutableMapOf<String, Int>()
        for (accountIndex in 0 until getNumAccounts()) {
            for (addressIndex in 0 until getNumSubaddresses(accountIndex)) {
                addressToAccount[getSubaddress(accountIndex, addressIndex)] = accountIndex
            }
        }

        val accountToBalance = mutableMapOf<Int, Balance>()

        enotes.filter { !it.isSpent }.forEach { enote ->
            enote.address?.let { address -> addressToAccount[address] }?.let { account ->
                val balance = accountToBalance.getOrDefault(account, Balance(0, 0, 0, 0))
                balance.total += enote.amount
                if (enote.isFrozen) {
                    balance.frozen += enote.amount
                } else if (enote.isUnlocked) {
                    balance.unlocked += enote.amount
                } else {
                    balance.pending += enote.amount
                }
                accountToBalance[account] = balance
            }
        }

        return accountToBalance
    }


    external fun freeze(idx: Long)
    external fun thaw(idx: Long)

    val name: String
        get() = getPath()?.let { File(it).name }.toString()

    external fun getSeed(offset: String): String
    external fun getLegacySeed(offset: String?): String?
    external fun isPolyseedSupported(offset: String?): Boolean
    external fun getSeedLanguage(): String?

    val status: Status
        get() = statusWithErrorString()
    val fullStatus: Status
        get() {
            val walletStatus = statusWithErrorString()
            walletStatus.connectionStatus = connectionStatus
            return walletStatus
        }

    private external fun statusWithErrorString(): Status

    fun getTransactionHistory(): TransactionHistory {
        return TransactionHistory(getHistoryJ())
    }

    @Synchronized
    external fun setPassword(password: String?): Boolean
    val address: String
        get() = getAddress(accountIndex)

    //TODO virtual void hardForkInfo(uint8_t &version, uint64_t &earliest_height) const = 0;
    //TODO virtual bool useForkRules(uint8_t version, int64_t early_blocks) const = 0;
    fun getAddress(accountIndex: Int): String {
        return getAddressJ(accountIndex, 0)
    }

    private fun getSubaddress(addressIndex: Int): String {
        return getAddressJ(accountIndex, addressIndex)
    }

    fun getSubaddress(accountIndex: Int, addressIndex: Int): String {
        return getAddressJ(accountIndex, addressIndex)
    }

    private external fun getAddressJ(accountIndex: Int, addressIndex: Int): String
    private fun getSubaddressObject(accountIndex: Int, subAddressIndex: Int): Subaddress {
        return Subaddress(
            accountIndex, subAddressIndex,
            getSubaddress(subAddressIndex), getSubaddressLabel(subAddressIndex)
        )
    }

    fun getSubaddressObject(subAddressIndex: Int): Subaddress {
        val subaddress = getSubaddressObject(accountIndex, subAddressIndex)
        var amount: Long = 0
//        history?.let { history ->
//            for (info in history.all) {
//                if (info.addressIndex == subAddressIndex && info.direction == TransactionInfo.Direction.Direction_In) {
//                    amount += info.amount
//                }
//            }
//        }
//
        subaddress.amount = amount
        return subaddress
    }

    external fun getPath(): String?
    fun networkType(): NetworkType? = NetworkType.fromInteger(nettype())

    external fun nettype(): Int

    //    virtual bool createWatchOnly(const std::string &path, const std::string &password, const std::string &language) const = 0;
    //    virtual void setRefreshFromBlockHeight(uint64_t refresh_from_block_height) = 0;
    external fun getIntegratedAddress(paymentId: String?): String?
    external fun getSecretViewKey(): String
    external fun getSecretSpendKey(): String

    fun store(): Boolean {
        return store("")
    }

    //TODO virtual void setTrustedDaemon(bool arg) = 0;
    //TODO virtual bool trustedDaemon() const = 0;
    @Synchronized
    external fun store(path: String?): Boolean
    fun close(): Boolean {
        disposePendingTransaction()
        return WalletManager.instance.closeJ(this)
    }

    external fun getFilename(): String

    external fun initJ(
        daemonAddress: String, upperTransactionSizeLimit: Long,
        daemonUsername: String, daemonPassword: String, proxyAddress: String
    ): Boolean

    external fun getRestoreHeight(): Long
    external fun setRestoreHeight(height: Long)

    val connectionStatus: ConnectionStatus
        get() {
            val s = getConnectionStatusJ()
            return ConnectionStatus.values()[s]
        }

    private external fun getConnectionStatusJ(): Int
    fun setTrustedDaemon(trusted: Boolean) {
        setTrustedDaemonJ(trusted)
    }

    private external fun setTrustedDaemonJ(trusted: Boolean)

    fun isTrustedDaemon(): Boolean {
        return isTrustedDaemonJ()
    }

    private external fun isTrustedDaemonJ(): Boolean

    fun setProxy(address: String?): Boolean {
        return setProxyJ(address)
    }

    private external fun setProxyJ(address: String?): Boolean
    val balance: Long
        get() = getBalance(accountIndex)

    private external fun getBalance(accountIndex: Int): Long
    external fun getBalanceAll(): Long
    val unlockedBalance: Long
        get() = getUnlockedBalance(accountIndex)

    external fun getUnlockedBalanceAll(): Long

    external fun getUnlockedBalance(accountIndex: Int): Long
    external fun isWatchOnly(): Boolean

    external fun getBlockChainHeightJ(): Long
    external fun getApproximateBlockChainHeight(): Long

    external fun getDaemonBlockChainHeightJ(): Long
    external fun getDaemonBlockChainTargetHeight(): Long

    fun setSynchronized() {
        isSynchronized = true
    }

    fun startRefresh() {
        startRefreshJ()
    }

    private external fun startRefreshJ()
    external fun pauseRefresh()
    external fun refresh(): Boolean
    external fun refreshAsync()
    private external fun rescanBlockchainAsyncJ()
    fun rescanBlockchainAsync() {
        isSynchronized = false
        rescanBlockchainAsyncJ()
    }

    //TODO virtual void setAutoRefreshInterval(int millis) = 0;
    //TODO virtual int autoRefreshInterval() const = 0;
    private fun disposePendingTransaction() {
        if (pendingTransaction != null) {
            disposeTransaction(pendingTransaction)
            pendingTransaction = null
        }
    }

    fun estimateTransactionFee(
        destinations: List<Pair<String, Long>>,
        priority: PendingTransaction.Priority
    ): Long {
        val _priority = priority.ordinal
        return estimateTransactionFee(destinations, _priority)
    }

    private external fun estimateTransactionFee(
        destinations: List<Pair<String, Long>>,
        priority: Int
    ): Long

    fun createSweepTransaction(
        dstAddr: String,
        priority: PendingTransaction.Priority,
        keyImages: ArrayList<String>
    ): PendingTransaction {
        disposePendingTransaction()
        val _priority = priority.ordinal
        val txHandle = createSweepTransaction(dstAddr, "", 0, _priority, accountIndex, keyImages)
        pendingTransaction = PendingTransaction(txHandle)
        return pendingTransaction!!
    }

    fun createTransactionMultDest(
        outputs: List<TransactionDestination>,
        priority: PendingTransaction.Priority,
        keyImages: ArrayList<String>
    ): PendingTransaction {
        disposePendingTransaction()
        val _priority = priority.ordinal
        val destinations = ArrayList<String>()
        val amounts = LongArray(outputs.size)
        for (i in outputs.indices) {
            val output = outputs[i]
            destinations.add(output.address)
            amounts[i] = output.amount
        }
        val txHandle = createTransactionMultDestJ(
            destinations, "", amounts, 0, _priority,
            accountIndex, keyImages
        )
        pendingTransaction = PendingTransaction(txHandle)
        return pendingTransaction!!
    }

    private external fun createTransactionMultDestJ(
        dstAddrs: ArrayList<String>, paymentId: String,
        amount: LongArray, mixinCount: Int,
        priority: Int, accountIndex: Int, keyImages: ArrayList<String>
    ): Long

    private external fun createTransactionJ(
        dstAddr: String, paymentId: String,
        amount: Long, mixinCount: Int,
        priority: Int, accountIndex: Int, keyImages: ArrayList<String>
    ): Long

    private external fun createSweepTransaction(
        dstAddr: String, paymentId: String,
        mixinCount: Int,
        priority: Int, accountIndex: Int, keyImages: ArrayList<String>
    ): Long

    fun createSweepUnmixableTransaction(): PendingTransaction? {
        disposePendingTransaction()
        val txHandle = createSweepUnmixableTransactionJ()
        pendingTransaction = PendingTransaction(txHandle)
        return pendingTransaction
    }

    private external fun createSweepUnmixableTransactionJ(): Long
    private external fun disposeTransaction(pendingTransaction: PendingTransaction?)
    private external fun getHistoryJ(): Long

    //virtual bool exportKeyImages(const std::string &filename) = 0;
    //virtual bool importKeyImages(const std::string &filename) = 0;
    //virtual TransactionHistory * history() const = 0;


    private external fun setListenerJ(listener: WalletListener?): Long
    fun setListener(listener: WalletListener?) {
        listenerHandle = setListenerJ(listener)
    }

    external fun getDefaultMixin(): Int
    external fun setDefaultMixin(mixin: Int)
    external fun setUserNote(txid: String?, note: String?): Boolean
    external fun getUserNote(txid: String?): String?
    external fun getTxKey(txid: String?): String?

    external fun addAccount(label: String)
    var accountLabel: String
        get() = getAccountLabel(accountIndex)
        //virtual std::string signMessage(const std::string &message) = 0;
        set(label) {
            setAccountLabel(accountIndex, label)
        }

    fun getAccountLabel(accountIndex: Int): String {
        val label = getSubaddressLabel(accountIndex, 0)
        return label
    }

    fun getSubaddressLabel(addressIndex: Int): String {
        return getSubaddressLabel(accountIndex, addressIndex)
    }

    private external fun getSubaddressLabel(accountIndex: Int, addressIndex: Int): String
    fun setAccountLabel(accountIndex: Int, label: String?) {
        setSubaddressLabel(accountIndex, 0, label)
    }

    fun setSubaddressLabel(addressIndex: Int, label: String?) {
        setSubaddressLabel(accountIndex, addressIndex, label)
    }

    private external fun setSubaddressLabel(accountIndex: Int, addressIndex: Int, label: String?)
    external fun getNumAccounts(): Int
    val numSubaddresses: Int
        get() = getNumSubaddresses(accountIndex)

    private external fun getNumSubaddresses(accountIndex: Int): Int

    external fun addSubaddress(accountIndex: Int, label: String?)
    private fun getLastSubaddress(accountIndex: Int): String {
        return getSubaddress(accountIndex, getNumSubaddresses(accountIndex) - 1)
    }

    val deviceType: Device
        get() {
            val device = getDeviceTypeJ()
            return Device.values()[device + 1] // mapping is monero+1=android
        }

    private external fun getDeviceTypeJ(): Int

    enum class Device(val accountLookahead: Int, val subaddressLookahead: Int) {
        Device_Undefined(0, 0), Device_Software(50, 200), Device_Ledger(5, 20)

    }

    enum class StatusEnum {
        Status_Ok, Status_Error, Status_Critical
    }

    enum class ConnectionStatus {
        ConnectionStatus_Disconnected, ConnectionStatus_Connected, ConnectionStatus_WrongVersion
    }

    class Status internal constructor(status: Int, val errorString: String) {
        val status: StatusEnum
        var connectionStatus: ConnectionStatus? = null // optional

        init {
            this.status = StatusEnum.values()[status]
        }

        val isOk: Boolean
            get() = (status == StatusEnum.Status_Ok
                    && (connectionStatus == null || connectionStatus == ConnectionStatus.ConnectionStatus_Connected))

        override fun toString(): String {
            return "Wallet.Status: $status/$errorString/$connectionStatus"
        }
    }

    companion object {
        const val SWEEP_ALL = Long.MAX_VALUE
        const val NEW_ACCOUNT_NAME = "Untitled account" // src/wallet/wallet2.cpp:941

        @JvmStatic
        external fun getDisplayAmount(amount: Long): String

        @JvmStatic
        external fun getAmountFromString(amount: String?): Long

        @JvmStatic
        external fun getAmountFromDouble(amount: Double): Long

        @JvmStatic
        external fun generatePaymentId(): String

        @JvmStatic
        external fun isPaymentIdValid(payment_id: String): Boolean

        fun isAddressValid(address: String): Boolean {
            return WalletManager.instance.networkType.value.let {
                isAddressValid(
                    address,
                    it
                )
            }
        }

        @JvmStatic
        external fun isAddressValid(address: String?, networkType: Int): Boolean

        @JvmStatic
        external fun getPaymentIdFromAddress(address: String?, networkType: Int): String?

        @JvmStatic
        external fun getMaximumAllowedAmount(): Long
    }
}

data class WalletInfo(val path: String, val name: String, val password: String) {
    constructor(file: File, password: String) : this(file.absolutePath, file.name, password)
}