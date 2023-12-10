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

import net.mynero.wallet.model.PendingTransaction
import net.mynero.wallet.model.TransactionOutput
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.model.Wallet.Companion.getAmountFromString
import net.mynero.wallet.model.Wallet.Companion.getPaymentIdFromAddress
import net.mynero.wallet.model.Wallet.ConnectionStatus
import net.mynero.wallet.model.WalletListener
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.util.Constants
import timber.log.Timber
import java.security.SecureRandom

/**
 * Handy class for starting a new thread that has a looper. The looper can then be
 * used to create handler classes. Note that start() must still be called.
 * The started Thread has a stck size of STACK_SIZE (=5MB)
 */
class MoneroHandlerThread(name: String, val listener: Listener?, wallet: Wallet) :
    Thread(null, null, name, THREAD_STACK_SIZE), WalletListener {
    private val wallet: Wallet

    init {
        this.wallet = wallet
    }

    @Synchronized
    override fun start() {
        super.start()
        listener?.onRefresh(false)
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
                Timber.d("refreshed() Synchronized")
                wallet.setSynchronized()
                wallet.store()
                refresh(true)
            }

        }
    }

    private fun tryRestartConnection() {
        Timber.d("refreshed() Starting connection retry")
        listener?.onConnectionFail()
        wallet.init(0)
        wallet.startRefresh()
    }

    private fun refresh(walletSynced: Boolean) {
        wallet.refreshHistory()
        if (walletSynced) {
            wallet.refreshCoins()
        }
        listener?.onRefresh(walletSynced)
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
        val finalOutputs = maybeAddDonationOutputs(totalAmount, outputs, preferredInputs)
        return wallet.createTransactionMultDest(finalOutputs, feePriority, preferredInputs)
    }

    @Throws(Exception::class)
    private fun maybeAddDonationOutputs(
        amount: Long,
        outputs: List<TransactionOutput>,
        preferredInputs: List<String>
    ): List<TransactionOutput> {
        val newOutputs = ArrayList(outputs)
        val networkType = WalletManager.instance?.networkType ?: return newOutputs
        val mainDestination =
            outputs[0] // at this point, for now, we should only have one item in the list. TODO: add multi-dest/pay-to-many feature in the UI
        val paymentId =
            getPaymentIdFromAddress(mainDestination.destination, networkType.value)
        val donatePerTx = PrefService.instance?.getBoolean(Constants.PREF_DONATE_PER_TX, false)
        if (donatePerTx == true && paymentId?.isEmpty() == true) { // only attach donation when no payment id is needed (i.e. integrated address)
            val rand = SecureRandom()
            val randomDonatePct = getRandomDonateAmount(
                0.005f,
                0.03f
            ) // occasionally attaches a 0.5% to 3% donation. It is random so that not even I know how much exactly you are sending.
            /*
            It's also not entirely "per tx". It won't always attach it so as to not have a consistently uncommon fingerprint on-chain. When it does attach a donation,
            it will periodically split it up into multiple outputs instead of one.
             */
            val attachDonationRoll = rand.nextInt(100)
            if (attachDonationRoll > 90) { // 10% chance of being added
                val splitDonationRoll = rand.nextInt(100)
                val donateAmount = (amount * randomDonatePct).toLong()
                if (splitDonationRoll > 50) { // 50% chance of being split
                    // split
                    val split = genRandomDonationSplit(
                        1,
                        4
                    ) // splits into at most 4 outputs, for a total of 6 outputs in the transaction (real dest + change. we don't add donations to send-all/sweep transactions)
                    val splitAmount = donateAmount / split
                    for (i in 0 until split) {
                        // TODO this can be expanded upon into the future to perform an auto-splitting/auto-churning for the user if their wallet is fresh and has few utxos.
                        // randomly split between multiple wallets
                        val randomDonationAddress = rand.nextInt(Constants.DONATION_ADDRESSES.size)
                        val donationAddress = Constants.DONATION_ADDRESSES[randomDonationAddress]
                        newOutputs.add(TransactionOutput(donationAddress, splitAmount))
                    }
                } else {
                    // just add one output, for a total of 3 (real dest + change)
                    newOutputs.add(TransactionOutput(Constants.DONATE_ADDRESS, donateAmount))
                }
                val total = amount + donateAmount
                checkSelectedAmounts(
                    preferredInputs,
                    total,
                    false
                ) // check that the selected UTXOs satisfy the new amount total
            }
        }
        newOutputs.shuffle() // shuffle the outputs just in case. i think the monero library handles this for us anyway
        return newOutputs
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

    private fun getRandomDonateAmount(min: Float, max: Float): Float {
        val rand = SecureRandom()
        return rand.nextFloat() * (max - min) + min
    }

    private fun genRandomDonationSplit(min: Int, max: Int): Int {
        val rand = SecureRandom()
        return rand.nextInt(max) + min
    }

    interface Listener {
        fun onRefresh(walletSynced: Boolean)
        fun onConnectionFail()
    }

    companion object {
        // from src/cryptonote_config.h
        const val THREAD_STACK_SIZE = (5 * 1024 * 1024).toLong()
    }
}