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
package org.monfluo.wallet.model

 import org.monfluo.wallet.data.Subaddress
 import timber.log.Timber
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.name

class Wallet(private val handle: Long, val info: WalletInfo, private var accountIndex: Int = 0) {
    private var listenerHandle: Long = 0
    private var pendingTransaction: PendingTransaction? = null
    private var enotes: List<Enote>? = null
    private var balances: Map<Int, Balance>? = null
    private var history: Map<Int, List<TransactionInfo>>? = null
    private var historyByTxId: Map<Int, Map<String, TransactionInfo>>? = null

    fun update() {
        refreshEnotes()
        refreshTransactionHistory()
    }

    fun getAccountIndex(): Int {
        return accountIndex
    }

    fun setAccountIndex(accountIndex: Int) {
        Timber.d("Account changed to index $accountIndex")
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
        this.historyByTxId = transactionInfos.mapValues { it.value.associateBy { it.hash ?: "" } }
    }

    fun getHistories(): Map<Int, List<TransactionInfo>> {
        if (history == null) {
            refreshTransactionHistory()
        }
        return history!!
    }

    fun getHistoriesByTxId(): Map<Int, Map<String, TransactionInfo>> {
        if (historyByTxId == null) {
            refreshTransactionHistory()
        }
        return historyByTxId!!
    }

    fun getHistory(): List<TransactionInfo> = getHistories()[accountIndex] ?: listOf()

    fun getTransaction(txId: String): TransactionInfo? = getHistoriesByTxId()[accountIndex]?.get(txId)

    private fun calculateBalances(enotes: List<Enote>): Map<Int, Balance> {
        return (0 until getNumAccounts()).associateWith { account ->
            val totalBalance = getBalance(account)
            val unlockedBalance = getUnlockedBalance(account)
            val pendingBalance = totalBalance - unlockedBalance
            val addresses = (0 until getNumSubaddresses(account)).map { getSubaddress(it) }.toSet()
            val frozenBalance = enotes.filter { it.isFrozen && it.address?.let { address -> addresses.contains(address) } ?: false }.sumOf { it.amount }
            Balance(totalBalance, unlockedBalance, frozenBalance, pendingBalance)
        }
    }


    external fun freeze(idx: Long)
    external fun thaw(idx: Long)

    fun name(): String = getPath()?.let { Path(it).name }.toString()

    external fun getSeed(offset: String): String
    external fun getLegacySeed(offset: String?): String?
    external fun isPolyseedSupported(): Boolean
    external fun getPolyseedSeedAndPassphrase(): Pair<String, String>?

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
        var used = false
        var totalBalance = 0L
        for (enote in getEnotes()) {
            if (enote.address == subaddress.address) {
                used = true
                if (!enote.isSpent) {
                    totalBalance += enote.amount
                }
            }
        }
        return subaddress.copy(used = used, totalBalance = totalBalance)
    }

    external fun getPath(): String?
    fun networkType(): NetworkType? = NetworkType.fromInteger(nettype())

    external fun nettype(): Int

    external fun getIntegratedAddress(paymentId: String?): String?
    external fun getSecretViewKey(): String
    external fun getPublicViewKey(): String
    external fun getSecretSpendKey(): String
    external fun getPublicSpendKey(): String

    fun store(): Boolean {
        return store("")
    }

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

    private external fun isTrustedDaemonJ(): Boolean

    fun setProxy(address: String?): Boolean {
        return setProxyJ(address)
    }

    private external fun setProxyJ(address: String?): Boolean

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

    fun startRefresh() {
        startRefreshJ()
    }

    private external fun startRefreshJ()
    external fun pauseRefresh()
    external fun refresh(): Boolean
    external fun refreshAsync()
    private external fun rescanBlockchainAsyncJ()

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
        transactionDestination: TransactionDestinationResolved,
        priority: PendingTransaction.Priority,
        keyImages: ArrayList<String>
    ): PendingTransaction {
        disposePendingTransaction()
        val txHandle = createSweepTransaction(transactionDestination.address, "", 0, priority.ordinal, accountIndex, keyImages)
        val pendingTransaction = PendingTransaction(txHandle)
        this.pendingTransaction = pendingTransaction
        return pendingTransaction
    }

    fun createTransactionMultDest(
        outputs: List<TransactionDestinationWithAmountResolved>,
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
        val pendingTransaction = PendingTransaction(txHandle)
        this.pendingTransaction = pendingTransaction
        return pendingTransaction
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

    private external fun createSweepUnmixableTransactionJ(): Long
    private external fun disposeTransaction(pendingTransaction: PendingTransaction?)
    private external fun getHistoryJ(): Long

    private external fun setListenerJ(listener: WalletListener?): Long
    fun setListener(listener: WalletListener?) {
        listenerHandle = setListenerJ(listener)
    }

    external fun setUserNote(txid: String?, note: String?): Boolean
    external fun getUserNote(txid: String?): String?
    external fun getTxKey(txid: String): String

    external fun addAccount(label: String)

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