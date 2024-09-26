/*
 * Copyright (C) 2006 The Android Open Source Project
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
package net.mynero.wallet.service

import android.content.Context
import android.os.Handler
import android.util.Log
import android.widget.Toast
import net.mynero.wallet.R
import net.mynero.wallet.model.PendingTransaction
import net.mynero.wallet.model.TransactionOutput
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.model.Wallet.Companion.getAmountFromString
import net.mynero.wallet.model.Wallet.ConnectionStatus
import net.mynero.wallet.model.WalletListener
import net.mynero.wallet.model.WalletManager
import java.io.File

/**
 * Handy class for starting a new thread that has a looper. The looper can then be
 * used to create handler classes. Note that start() must still be called.
 * The started Thread has a stck size of STACK_SIZE (=5MB)
 */
class MoneroHandlerThread(val wallet: Wallet, val context: Context) : Thread(null, null, "WalletService", THREAD_STACK_SIZE), WalletListener {

    private val handler = Handler(context.mainLooper)

    companion object {
        // from src/cryptonote_config.h
        const val THREAD_STACK_SIZE = (5 * 1024 * 1024).toLong()

        fun init(walletFile: File, password: String?, context: Context) {
            val wallet = WalletManager.instance!!.openWallet(walletFile.absolutePath, password ?: "")
            val thread = MoneroHandlerThread(wallet, context)

            TxService(thread)
            BalanceService(thread)
            AddressService(thread)
            HistoryService(thread)
            BlockchainService(thread)
            DaemonService(thread)
            UTXOService(thread)
            thread.start()
        }
    }

    @Synchronized
    override fun start() {
        println("HERE1")
        super.start()
        println("HERE2")
        onRefresh(false)
    }

    override fun run() {
        val prefService = PrefService.instance ?: return
        val usesTor = ProxyService.instance?.usingProxy == true
        val currentNode = prefService.node
        val isLocalIp =
            currentNode?.address?.startsWith("10.") == true ||
                    currentNode?.address?.startsWith("192.168.") == true ||
                    currentNode?.address == "localhost" ||
                    currentNode?.address == "127.0.0.1"
        if (usesTor && !isLocalIp) {
            val proxy = ProxyService.instance?.proxy
            proxy?.let { WalletManager.instance?.setProxy(it) }
            wallet.setProxy(proxy)
        }
        WalletManager.instance?.setDaemon(currentNode)
        wallet.init(0)
        currentNode?.trusted?.let { wallet.setTrustedDaemon(it) }
        wallet.setListener(this)
        wallet.startRefresh()
    }

    override fun moneySpent(txId: String?, amount: Long) {}
    override fun moneyReceived(txId: String?, amount: Long) {}
    override fun unconfirmedMoneyReceived(txId: String?, amount: Long) {}
    override fun newBlock(height: Long) {
        refresh(false)
        BlockchainService.instance?.daemonHeight =
            if (wallet.isSynchronized) height else 0 // when 0 it fetches from C++
    }

    override fun updated() {
        refresh(false)
    }

    override fun refreshed() {
        val status = wallet.fullStatus.connectionStatus
        val daemonHeight = wallet.getDaemonBlockChainHeight()
        val chainHeight = wallet.getBlockChainHeight()
        BlockchainService.instance?.daemonHeight = daemonHeight
        if (status === ConnectionStatus.ConnectionStatus_Disconnected || status == null) {
            tryRestartConnection()
        } else {
            val heightDiff = daemonHeight - chainHeight
            if (heightDiff >= 2) {
                tryRestartConnection()
            } else {
                Log.d("MoneroHandlerThread.kt", "refreshed() Synchronized")
                wallet.setSynchronized()
                wallet.store()
                refresh(true)
            }

        }
    }

    private fun tryRestartConnection() {
        Log.d("MoneroHandlerThread.kt", "refreshed() Starting connection retry")
        onConnectionFail()
        wallet.init(0)
        wallet.startRefresh()
    }

    private fun refresh(walletSynced: Boolean) {
        wallet.refreshHistory()
        if (walletSynced) {
            wallet.refreshCoins()
        }
        onRefresh(walletSynced)
    }

    @Throws(Exception::class)
    fun createTx(
        address: String,
        amountStr: String,
        sendAll: Boolean,
        feePriority: PendingTransaction.Priority,
        selectedUtxos: ArrayList<String>
    ): PendingTransaction? {
        val dests = ArrayList<Pair<String, String>>()
        dests.add(Pair(address, amountStr))
        return createTx(dests, sendAll, feePriority, selectedUtxos)
    }

    @Throws(Exception::class)
    fun createTx(
        dests: List<Pair<String, String>>,
        sendAll: Boolean,
        feePriority: PendingTransaction.Priority,
        selectedUtxos: ArrayList<String>
    ): PendingTransaction? {
        var totalAmount: Long = 0
        val outputs = ArrayList<TransactionOutput>()
        for (dest in dests) {
            val amount = getAmountFromString(dest.component2())
            totalAmount += amount
            outputs.add(TransactionOutput(dest.component1(), amount))
        }
        val preferredInputs: ArrayList<String>
        if (selectedUtxos.isEmpty()) {
            // no inputs manually selected, we are sending from home screen most likely, or user somehow broke the app
            preferredInputs =
                UTXOService.instance?.selectUtxos(totalAmount, sendAll, feePriority) ?: ArrayList()
        } else {
            preferredInputs = selectedUtxos
            checkSelectedAmounts(preferredInputs, totalAmount, sendAll)
        }
        if (sendAll) {
            val dest = dests[0]
            val address = dest.component1()
            return wallet.createSweepTransaction(address, feePriority, preferredInputs)
        }
        return wallet.createTransactionMultDest(outputs, feePriority, preferredInputs)
    }

    @Throws(Exception::class)
    private fun checkSelectedAmounts(selectedUtxos: List<String?>, amount: Long, sendAll: Boolean) {
        if (!sendAll) {
            var amountSelected: Long = 0
            val utxos = UTXOService.instance?.getUtxos() ?: emptyList()
            for (coinsInfo in utxos) {
                if (selectedUtxos.contains(coinsInfo.keyImage)) {
                    amountSelected += coinsInfo.amount
                }
            }

            if (amountSelected <= amount) {
                throw Exception("insufficient wallet balance")
            }
        }
    }

    fun sendTx(pendingTx: PendingTransaction): Boolean {
        return pendingTx.commit("", true)
    }

    fun onRefresh(walletSynced: Boolean) {
        println("onRefresh")
        if (walletSynced) {
            UTXOService.instance?.refreshUtxos()
        }
        HistoryService.instance?.refreshHistory()
        BalanceService.instance?.refreshBalance()
        BlockchainService.instance?.refreshBlockchain()
        AddressService.instance?.refreshAddresses()
    }

    fun onConnectionFail() {
        handler.post {
            Toast.makeText(context, R.string.connection_failed, Toast.LENGTH_SHORT).show()
        }
    }
}