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

import org.monfluo.wallet.data.Address
import timber.log.Timber
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.name

class Wallet(private val handle: Long, val info: WalletInfo, private var accountIndex: Int = 0) {

    private var listenerHandle: Long = 0
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

    fun getBalance(accountIndex: Int? = null): Balance = getBalances()[accountIndex ?: this.accountIndex] ?: Balance(0, 0, 0, 0)

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
        return (0 until getNumAccounts()).associateWith { accountIndex ->
            val totalBalance = getBalance(accountIndex)
            val unlockedBalance = getUnlockedBalance(accountIndex)
            val pendingBalance = totalBalance - unlockedBalance
            val addresses = (0 until getNumAddresses(accountIndex)).map { addressIndex -> getAddress(accountIndex, addressIndex) }.toSet()
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
    fun getTransactionHistory(): TransactionHistory {
        return TransactionHistory(getHistoryJ())
    }

    external fun getPassword(): String

    @Synchronized
    external fun setPassword(password: String): Boolean

    fun getAddress(accountIndex: Int, addressIndex: Int): String {
        return getAddressJ(accountIndex, addressIndex)
    }

    private external fun getAddressJ(accountIndex: Int, addressIndex: Int): String

    private fun getAddressObject(accountIndex: Int, addressIndex: Int): Address {
        return Address(
            accountIndex, addressIndex,
            getAddress(accountIndex, addressIndex), getAddressLabel(accountIndex, addressIndex)
        )
    }

    fun getAddressObject(subAddressIndex: Int): Address {
        val subaddress = getAddressObject(accountIndex, subAddressIndex)
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
    fun networkType(): MoneroNetworkType = MoneroNetworkType.fromInteger(nettype())!!

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
        return WalletManager.instance.closeJ(this)
    }

    external fun getFilename(): String

    external fun initJ(
        daemonAddress: String, upperTransactionSizeLimit: Long,
        daemonUsername: String, daemonPassword: String, proxyAddress: String
    ): Boolean

    external fun getRestoreHeight(): Long
    external fun setRestoreHeight(height: Long)

    fun getAdjustedRestoreHeight(): Long {
        return getEnotes()
            .filter { !it.isSpent }
            .map { it.height }
            .min()
            ?: getRestoreHeight()
    }

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

    private external fun estimateTransactionFee(
        destinations: List<Pair<String, Long>>,
        priority: Int
    ): Long

    external fun createTransactionMultDestJ(
        dstAddrs: List<String>, paymentId: String,
        amount: LongArray, mixinCount: Int,
        priority: Int, accountIndex: Int, keyImages: List<String>
    ): Long

     external fun createSweepTransaction(
        dstAddr: String, paymentId: String,
        mixinCount: Int,
        priority: Int, accountIndex: Int, keyImages: List<String>
    ): Long

    private external fun createSweepUnmixableTransactionJ(): Long
    external fun disposeTransaction(pendingTransaction: PendingTransaction)
    private external fun getHistoryJ(): Long

    private external fun setListenerJ(listener: WalletListener?): Long
    fun setListener(listener: WalletListener?) {
        listenerHandle = setListenerJ(listener)
    }

    external fun setUserNote(txid: String, note: String): Boolean
    external fun getUserNote(txid: String): String
    external fun getTxKey(txid: String): String

    external fun addAccount(label: String)

    fun getAccountLabel(accountIndex: Int): String {
        val label = getAddressLabel(accountIndex, 0)
        return label
    }

    fun getAddressLabel(addressIndex: Int): String {
        return getAddressLabel(accountIndex, addressIndex)
    }

    external fun getAddressLabel(accountIndex: Int, addressIndex: Int): String

    fun setAccountLabel(accountIndex: Int, label: String?) {
        setAddressLabel(accountIndex, 0, label)
    }

    fun setAddressLabel(addressIndex: Int, label: String?) {
        setAddressLabel(accountIndex, addressIndex, label)
    }

    external fun setAddressLabel(accountIndex: Int, addressIndex: Int, label: String?)

    external fun getNumAccounts(): Int
    val numAddresses: Int
        get() = getNumAddresses(accountIndex)

    private external fun getNumAddresses(accountIndex: Int): Int

    external fun addAddress(accountIndex: Int, label: String?)

    external fun getWalletStatusWithError(): WalletStatusWithError

    enum class ConnectionStatus {
        ConnectionStatus_Disconnected, ConnectionStatus_Connected, ConnectionStatus_WrongVersion
    }

    companion object {
        const val NEW_ACCOUNT_NAME = "Untitled account" // src/wallet/wallet2.cpp:941

        @JvmStatic
        external fun getDisplayAmount(amount: Long): String

        @JvmStatic
        external fun getAmountFromString(amount: String): Long?

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
        external fun isAddressValid(address: String, networkType: Int): Boolean

        @JvmStatic
        external fun getPaymentIdFromAddress(address: String, networkType: Int): String?

        @JvmStatic
        external fun getMaximumAllowedAmount(): Long
    }
}

data class WalletInfo(val path: String, val name: String, val password: String) {
    constructor(file: File, password: String) : this(file.absolutePath, file.name, password)
}
