package net.mynero.wallet.service.wallet

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.lifecycle.AtomicReference
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import net.mynero.wallet.model.Balance
import net.mynero.wallet.model.CoinsInfo
import net.mynero.wallet.model.PendingTransaction
import net.mynero.wallet.model.TransactionInfo
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.model.WalletListener
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.MoneroHandlerThread
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.TransactionDestination
import timber.log.Timber
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

private const val EXTRA_WALLET_NAME = "wallet_name"
private const val EXTRA_WALLET_PASSWORD = "wallet_password"
private const val EXTRA_DAEMON_ADDRESS = "daemon_address"
private const val EXTRA_DAEMON_USERNAME = "daemon_username"
private const val EXTRA_DAEMON_PASSWORD = "daemon_password"
private const val EXTRA_DAEMON_TRUSTED = "daemon_trusted"
private const val EXTRA_SUBADDRESS_LABEL = "subaddress_label"
private const val EXTRA_SUBADDRESS_INDEX = "subaddress_index"
private const val EXTRA_TRANSACTION_DESTINATION = "transaction_destination"
private const val EXTRA_TRANSACTION_DESTINATIONS = "transaction_destinations"
private const val EXTRA_TRANSACTION_PRIORITY = "transaction_priority"
private const val EXTRA_TRANSACTION_SELECTED_UTXOS = "transaction_selected_utxos"
private const val EXTRA_PENDING_TRANSACTION = "pending_transaction"
private const val EXTRA_PROXY = "proxy"
private const val EXTRA_ENOTE = "enote"
private const val EXTRA_ENOTES = "enotes"

class WalletService : Service(), WalletListener, DefaultLifecycleObserver {

    private var daemonHeight: AtomicLong = AtomicLong(0)
    // height of the wallet when it was opened
    private var walletBeginSyncHeight: AtomicLong = AtomicLong(0)
    private var proxyRef: AtomicReference<String> = AtomicReference("")
    private var walletRef: AtomicReference<Wallet?> = AtomicReference(null)
    private val observers = mutableSetOf<WalletServiceObserver>()

    inner class WalletServiceBinder : Binder() {
        val service: WalletService = this@WalletService
    }

    inner class WalletHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(message: Message) {
            val commandType = message.arg1
            val data = message.data
            when (WalletServiceCommand.fromInt(commandType)) {
                WalletServiceCommand.UNKNOWN, null -> {
                    Timber.e("WalletHandler received UNKNOWN(%d) command", commandType)
                }
                WalletServiceCommand.OPEN_WALLET -> {
                    val walletName = data.getString(EXTRA_WALLET_NAME)!!
                    val walletFile = File(applicationInfo.dataDir, walletName)
                    val walletPassword = data.getString(EXTRA_WALLET_PASSWORD)!!
                    val daemonAddress = data.getString(EXTRA_DAEMON_ADDRESS)!!
                    val daemonUsername = data.getString(EXTRA_DAEMON_USERNAME)!!
                    val daemonPassword = data.getString(EXTRA_DAEMON_PASSWORD)!!
                    val daemonTrusted = data.getBoolean(EXTRA_DAEMON_TRUSTED, false)
                    val proxy = data.getString(EXTRA_PROXY)!!
                    handleOpenWallet(walletFile, walletPassword, daemonAddress, daemonUsername, daemonPassword, daemonTrusted, proxy)
                }
                WalletServiceCommand.SET_DAEMON_ADDRESS -> {
                    val daemonAddress = data.getString(EXTRA_DAEMON_ADDRESS)!!
                    val daemonUsername = data.getString(EXTRA_DAEMON_USERNAME)!!
                    val daemonPassword = data.getString(EXTRA_DAEMON_PASSWORD)!!
                    val daemonTrusted = data.getBoolean(EXTRA_DAEMON_TRUSTED, false)
                    val proxy = data.getString(EXTRA_PROXY)!!
                    setDaemonAddress(daemonAddress, daemonUsername, daemonPassword, daemonTrusted, proxy)
                }
                WalletServiceCommand.FETCH_BLOCKCHAIN_HEIGHT -> {
                    handleFetchDaemonHeight()
                }
                WalletServiceCommand.REFRESH_WALLET_TRANSACTION_HISTORY -> {
                    handleRefreshTransactionHistory()
                }
                WalletServiceCommand.GENERATE_NEW_SUBADDRESS -> {
                    val subaddressLabel = data.getString(EXTRA_SUBADDRESS_LABEL)!!
                    handleGenerateNewSubaddress(subaddressLabel)
                }
                WalletServiceCommand.SET_SUBADDRESS_LABEL -> {
                    val subaddressIndex = data.getInt(EXTRA_SUBADDRESS_INDEX)
                    val subaddressLabel = data.getString(EXTRA_SUBADDRESS_LABEL)!!
                    handleSetSubaddressLabel(subaddressIndex, subaddressLabel)
                }
                WalletServiceCommand.CREATE_SWEEP_TX -> {
                    val transactionDestination = data.getString(EXTRA_TRANSACTION_DESTINATION)!!
                    val transactionPriority = PendingTransaction.Priority.fromInteger(message.data.getInt(
                        EXTRA_TRANSACTION_PRIORITY
                    ))!!
                    val selectedUtxos = message.data.getStringArrayList(
                        EXTRA_TRANSACTION_SELECTED_UTXOS
                    )!!
                    handleCreateSweepTransaction(transactionDestination, transactionPriority, selectedUtxos)
                }
                WalletServiceCommand.CREATE_TX -> {
                    val transactionDestinations = data.getParcelableArrayList<TransactionDestination>(
                        EXTRA_TRANSACTION_DESTINATIONS
                    )!!
                    val transactionPriority = PendingTransaction.Priority.fromInteger(message.data.getInt(
                        EXTRA_TRANSACTION_PRIORITY
                    ))!!
                    val selectedUtxos = message.data.getStringArrayList(
                        EXTRA_TRANSACTION_SELECTED_UTXOS
                    )!!
                    handleCreateTransaction(transactionDestinations, transactionPriority, selectedUtxos)
                }
                WalletServiceCommand.SEND_TX -> {
                    val pendingTransactionHandle = data.getLong(EXTRA_PENDING_TRANSACTION)
                    val pendingTransaction = PendingTransaction(pendingTransactionHandle)
                    handleSendPendingTransaction(pendingTransaction)
                }
                WalletServiceCommand.SET_PROXY -> {
                    val proxy = data.getString(EXTRA_PROXY)!!
                    handleSetProxy(proxy)
                }
                WalletServiceCommand.REFRESH_ENOTES -> {
                    handleRefreshEnotes()
                }
                WalletServiceCommand.FREEZE_ENOTES -> {
                    val enotes = data.getStringArrayList(EXTRA_ENOTES)!!
                    handleFreezeEnotes(enotes)
                }
                WalletServiceCommand.THAW_ENOTES -> {
                    val enotes = data.getStringArrayList(EXTRA_ENOTES)!!
                    handleThawEnotes(enotes)
                }
            }
        }

        private fun handleOpenWallet(
            walletFile: File,
            walletPassword: String,
            daemonAddress: String,
            daemonUsername: String,
            daemonPassword: String,
            daemonTrusted: Boolean,
            proxy: String
        ) {
            WalletManager.instance.setProxy(proxy)
            WalletManager.instance.setDaemonAddressJ(daemonAddress)
            val wallet = WalletManager.instance.openWallet(walletFile.absolutePath, walletPassword)
            Timber.i("Initializing wallet with daemon address = $daemonAddress, daemon username = $daemonUsername, daemon password = ${"*".repeat(daemonPassword.length)}, proxy = $proxy")
            wallet.initJ(daemonAddress, 0, daemonUsername, daemonPassword, proxy)
            wallet.setTrustedDaemon(daemonTrusted)

            this@WalletService.walletBeginSyncHeight.set(wallet.getBlockChainHeightJ())
            this@WalletService.walletRef.set(wallet)

            forEachObserver {
                it.onWalletOpened(wallet)
            }

            handleRefreshTransactionHistory()
            handleRefreshEnotes()
            wallet.setListener(this@WalletService)
            wallet.startRefresh()
            handleFetchDaemonHeight()
        }

        private fun setDaemonAddress(
            daemonAddress: String,
            daemonUsername: String,
            daemonPassword: String,
            daemonTrusted: Boolean,
            proxy: String
        ) {
            WalletManager.instance.setProxy(proxy)
            WalletManager.instance.setDaemonAddressJ(daemonAddress)
            walletRef.get()?.let { wallet ->
                wallet.initJ(daemonAddress, 0, daemonUsername, daemonPassword, proxy)
                wallet.setTrustedDaemon(daemonTrusted)
                wallet.setListener(this@WalletService)
                wallet.startRefresh()

                forEachObserver {
                    it.onWalletOpened(wallet)
                }

                handleRefreshTransactionHistory()
                handleRefreshEnotes()
            }
            handleFetchDaemonHeight()
        }

        private fun handleFetchDaemonHeight() {
            val newHeight = WalletManager.instance.getBlockchainHeight()
            if (newHeight > 0) {
                daemonHeight.set(newHeight)
            }
            forEachObserver {
                it.onBlockchainHeightFetched(newHeight)
            }
        }

        private fun handleRefreshTransactionHistory() {
            val history = walletRef.get()!!.getTransactionHistory().refreshJ()
            forEachObserver {
                it.onWalletHistoryRefreshed(history)
            }
        }

        private fun handleGenerateNewSubaddress(label: String) {
            walletRef.get()!!.addSubaddress(0, label)
            walletRef.get()!!.store()
            forEachObserver {
                it.onSubaddressesUpdated()
            }
        }

        private fun handleSetSubaddressLabel(index: Int, label: String) {
            walletRef.get()!!.setSubaddressLabel(index, label)
            walletRef.get()!!.store()
            forEachObserver {
                it.onSubaddressesUpdated()
            }
        }

        private fun handleCreateSweepTransaction(
            destination: String,
            feePriority: PendingTransaction.Priority,
            selectedUtxos: ArrayList<String>
        ) {
            val preferredInputs = if (selectedUtxos.isEmpty()) {
                selectUtxos(Long.MAX_VALUE, true, feePriority)
            } else {
                selectedUtxos
            }
            val pendingTransaction = walletRef.get()!!.createSweepTransaction(destination, feePriority, preferredInputs)
            forEachObserver {
                it.onTransactionCreated(pendingTransaction)
            }
        }

        private fun handleCreateTransaction(
            destinations: List<TransactionDestination>,
            feePriority: PendingTransaction.Priority,
            selectedUtxos: ArrayList<String>
        ) {
            val totalAmount = destinations.sumOf { it.amount }
            val preferredInputs = if (selectedUtxos.isEmpty()) {
                selectUtxos(totalAmount, false, feePriority)
            } else {
                selectedUtxos
            }
            val pendingTransaction = walletRef.get()!!.createTransactionMultDest(destinations, feePriority, preferredInputs)
            forEachObserver {
                it.onTransactionCreated(pendingTransaction)
            }
        }

        private fun handleSendPendingTransaction(pendingTransaction: PendingTransaction) {
            val success = pendingTransaction.commit("", false)
            forEachObserver {
                it.onTransactionSent(pendingTransaction, success)
            }
        }

        @Throws(Exception::class)
        fun selectUtxos(
            amount: Long,
            sendAll: Boolean,
            feePriority: PendingTransaction.Priority
        ): ArrayList<String> {
            // this is bugged, throws insufficient balance even when there is enough money (prob coins is not updated?)
            val basicFeeEstimate = calculateBasicFee(amount, feePriority) ?: return arrayListOf()
            val amountWithBasicFee = amount + basicFeeEstimate
            val selectedUtxos = ArrayList<String>()
            val seenTxs = ArrayList<String>()
            val utxos: List<CoinsInfo> = ArrayList(walletRef.get()!!.coins?.all!!)
            var amountSelected: Long = 0
            val sortedUtxos = utxos.sorted()
            //loop through each utxo
            Log.e("WS", "sortedUtxos sz = ${sortedUtxos.size}")
            for (coinsInfo in sortedUtxos) {
                Log.e("WS", "coinsInfo = $coinsInfo")
                if (!coinsInfo.isSpent && coinsInfo.isUnlocked && !coinsInfo.isFrozen
                ) { //filter out spent, locked, and frozen outputs
                    if (sendAll) {
                        // if send all, add all utxos and set amount to send all
                        coinsInfo.keyImage?.let { selectedUtxos.add(it) }
                        amountSelected = Wallet.SWEEP_ALL
                    } else {
                        //if amount selected is still less than amount needed, and the utxos tx hash hasn't already been seen, add utxo
                        if (amountSelected <= amountWithBasicFee && !seenTxs.contains(coinsInfo.hash)) {
                            coinsInfo.keyImage?.let { selectedUtxos.add(it) }
                            // we don't want to spend multiple utxos from the same transaction, so we prevent that from happening here.
                            coinsInfo.hash?.let { seenTxs.add(it) }
                            amountSelected += coinsInfo.amount
                        }
                    }
                }
            }
            if (amountSelected < amountWithBasicFee && !sendAll) {
                Log.e("WS", "amountSelected = $amountSelected, amountWithBasicFee = $amountWithBasicFee")
                throw Exception("insufficient wallet balance")
            }
            return selectedUtxos
        }

        private fun calculateBasicFee(amount: Long, feePriority: PendingTransaction.Priority): Long? {
            val destinations = ArrayList<android.util.Pair<String, Long>>()
            destinations.add(
                android.util.Pair(
                    Constants.DONATE_ADDRESS,
                    amount
                )
            )
            // destination string doesn't actually matter here, so i'm using the donation address. amount also technically doesn't matter
            return walletRef.get()?.estimateTransactionFee(destinations, feePriority)
        }

        private fun handleSetProxy(value: String) {
            val success = WalletManager.instance.setProxy(value)
            // we assume that if setting proxy for daemon was successful,
            // then setting proxy for wallet must have been successful as well
            walletRef.get()?.setProxy(value)
            if (success) {
                this@WalletService.proxyRef.set(value)
            }
            forEachObserver {
                it.onProxyUpdated(value, success)
            }
        }

        private fun calculateBalanceAndNotifyAboutRefreshedEnotes(enotes: List<CoinsInfo>) {
            var total = 0L
            var unlocked = 0L
            var frozen = 0L
            var pending = 0L

            enotes.filter { !it.isSpent }.forEach { enote ->
                total += enote.amount
                if (enote.isFrozen) {
                    frozen += enote.amount
                } else if (enote.isUnlocked) {
                    unlocked += enote.amount
                } else {
                    pending += enote.amount
                }
            }

            forEachObserver {
                it.onEnotesRefreshed(enotes, Balance(total, unlocked, frozen, pending))
            }
        }

        private fun handleRefreshEnotes() {
            val refreshedEnotes = walletRef.get()!!.coins!!.refreshJ()
            calculateBalanceAndNotifyAboutRefreshedEnotes(refreshedEnotes)
        }

        private fun handleFreezeEnotes(enotes: List<String>) {
            enotes.forEach {
                walletRef.get()!!.coins!!.setFrozen(it, true)
            }
            val refreshedEnotes = walletRef.get()!!.coins!!.refreshJ()
            calculateBalanceAndNotifyAboutRefreshedEnotes(refreshedEnotes)
        }

        private fun handleThawEnotes(enotes: List<String>) {
            enotes.forEach {
                walletRef.get()!!.coins!!.setFrozen(it, false)
            }
            val refreshedEnotes = walletRef.get()!!.coins!!.refreshJ()
            calculateBalanceAndNotifyAboutRefreshedEnotes(refreshedEnotes)
        }
    }

    private val mBinder: IBinder = WalletServiceBinder()

    private lateinit var handler: WalletHandler

    override fun onBind(intent: Intent?): IBinder {
        return mBinder
    }

    override fun onCreate() {
        // ArrayBlockingQueue is used as a oneshot channel to receive a Looper from MoneroHandlerThread
        val oneShotQueue = ArrayBlockingQueue<Looper>(1)
        val thread = MoneroHandlerThread { looper ->
            oneShotQueue.add(looper)
        }
        thread.start()

        val looper = oneShotQueue.take()
        handler = WalletHandler(looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            return START_STICKY
        } else {
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        synchronized(observers) {
            observers.remove(owner)
        }
    }

    private fun forEachObserver(f: (WalletServiceObserver) -> Unit) {
        synchronized(observers) {
            for (observer in observers) {
                f(observer)
            }
        }
    }

    override fun moneySpent(txId: String?, amount: Long) {
        Timber.d("Money spent callback")
        refreshTransactionsHistory()
    }

    override fun moneyReceived(txId: String?, amount: Long) {
        Timber.d("Money received callback")
        refreshTransactionsHistory()
    }

    override fun unconfirmedMoneyReceived(txId: String?, amount: Long) {
        Timber.d("Unconfirmed money received callback")
        refreshTransactionsHistory()
    }

    override fun newBlock(height: Long) {
        Timber.d("New block callback at height $height")
        daemonHeight.updateAndGet { if (it > 0 && it < height) height else it }
        forEachObserver {
            it.onNewBlock(height)
        }
        val wallet = getWalletOrThrow()
        refreshTransactionsHistory()
        wallet.coins!!.refresh()
        if (wallet.isSynchronized) {
            wallet.store()
        }
    }

    override fun updated() {
        Timber.d("Updated callback")
        forEachObserver {
            it.onUpdated()
        }
    }

    override fun refreshed() {
        Timber.d("Refreshed callback")
        forEachObserver {
            it.onRefreshed()
        }
        if (daemonHeight.get() <= 1) {
            Timber.d("Fetching blockchain height")
            fetchBlockchainHeight()
        }
        val wallet = getWalletOrThrow()
        val wasSynchronized = wallet.isSynchronized
        if (!wasSynchronized) {
            Timber.d("Storing wallet after synchronization was completed")
            wallet.isSynchronized = true
            wallet.store()
        }
    }

    fun addObserver(observer: WalletServiceObserver) {
        observer.lifecycle.addObserver(this)
        synchronized(observers) {
            observers.add(observer)
        }
    }

    fun getWalletBeginSyncHeight(): Long {
        return walletBeginSyncHeight.get()
    }

    fun getDaemonHeight(): Long {
        return daemonHeight.get()
    }

    fun getProxy(): String {
        return proxyRef.get()
    }

    fun getWallet(): Wallet? {
        return walletRef.get()
    }

    fun getWalletOrThrow(): Wallet {
        val wallet = this.walletRef.get()
        if (wallet != null) {
            return wallet
        } else {
            throw IllegalStateException("Get wallet called on null wallet")
        }
    }

    fun openWallet(
        walletName: String,
        walletPassword: String,
        daemonAddress: String,
        daemonUsername: String,
        daemonPassword: String,
        daemonTrusted: Boolean,
        proxy: String
    ) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.OPEN_WALLET.value()
        message.data.putString(EXTRA_WALLET_NAME, walletName)
        message.data.putString(EXTRA_WALLET_PASSWORD, walletPassword)
        message.data.putString(EXTRA_DAEMON_ADDRESS, daemonAddress)
        message.data.putString(EXTRA_DAEMON_USERNAME, daemonUsername)
        message.data.putString(EXTRA_DAEMON_PASSWORD, daemonPassword)
        message.data.putBoolean(EXTRA_DAEMON_TRUSTED, daemonTrusted)
        message.data.putString(EXTRA_PROXY, proxy)
        handler.sendMessage(message)
    }

    fun setDaemonAddress(
        daemonAddress: String,
        daemonUsername: String,
        daemonPassword: String,
        daemonTrusted: Boolean,
        proxy: String
    ) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SET_DAEMON_ADDRESS.value()
        message.data.putString(EXTRA_DAEMON_ADDRESS, daemonAddress)
        message.data.putString(EXTRA_DAEMON_USERNAME, daemonUsername)
        message.data.putString(EXTRA_DAEMON_PASSWORD, daemonPassword)
        message.data.putBoolean(EXTRA_DAEMON_TRUSTED, daemonTrusted)
        message.data.putString(EXTRA_PROXY, proxy)
        handler.sendMessage(message)
    }

    private fun fetchBlockchainHeight() {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.FETCH_BLOCKCHAIN_HEIGHT.value()
        handler.sendMessage(message)
    }

    fun refreshTransactionsHistory() {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.REFRESH_WALLET_TRANSACTION_HISTORY.value()
        handler.sendMessage(message)
    }

    fun generateNewSubaddress(label: String) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.GENERATE_NEW_SUBADDRESS.value()
        message.data.putString(EXTRA_SUBADDRESS_LABEL, label)
        handler.sendMessage(message)
    }

    fun setSubaddressLabel(index: Int, label: String) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SET_SUBADDRESS_LABEL.value()
        message.data.putInt(EXTRA_SUBADDRESS_INDEX, index)
        message.data.putString(EXTRA_SUBADDRESS_LABEL, label)
        handler.sendMessage(message)
    }

    fun sendTransaction(pendingTransaction: PendingTransaction) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SEND_TX.value()
        message.data.putLong(EXTRA_PENDING_TRANSACTION, pendingTransaction.handle)
        handler.sendMessage(message)
    }

    fun createSweepTransaction(destination: String, priority: PendingTransaction.Priority, selectedUtxos: ArrayList<String>) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.CREATE_SWEEP_TX.value()
        message.data.putString(EXTRA_TRANSACTION_DESTINATION, destination)
        message.data.putInt(EXTRA_TRANSACTION_PRIORITY, priority.value)
        message.data.putStringArrayList(EXTRA_TRANSACTION_SELECTED_UTXOS, selectedUtxos)
        handler.sendMessage(message)
    }

    fun createTransaction(destinations: List<TransactionDestination>, priority: PendingTransaction.Priority, selectedUtxos: ArrayList<String>) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.CREATE_TX.value()
        message.data.putParcelableArrayList(EXTRA_TRANSACTION_DESTINATIONS, ArrayList(destinations))
        message.data.putInt(EXTRA_TRANSACTION_PRIORITY, priority.value)
        message.data.putStringArrayList(EXTRA_TRANSACTION_SELECTED_UTXOS, selectedUtxos)
        handler.sendMessage(message)
    }

    fun setProxy(value: String) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SET_PROXY.value()
        message.data.putString(EXTRA_PROXY, value)
        handler.sendMessage(message)
    }

    fun refreshEnotes() {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.REFRESH_ENOTES.value()
        handler.sendMessage(message)
    }

    fun freezeEnote(enotes: List<String>) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.FREEZE_ENOTES.value()
        message.data.putStringArrayList(EXTRA_ENOTES, ArrayList(enotes))
        handler.sendMessage(message)
    }

    fun thawEnote(enotes: List<String>) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.THAW_ENOTES.value()
        message.data.putStringArrayList(EXTRA_ENOTES, ArrayList(enotes))
        handler.sendMessage(message)
    }
}

interface WalletServiceObserver : LifecycleOwner {
    fun onWalletOpened(wallet: Wallet) {}
    fun onWalletHistoryRefreshed(transactions: List<TransactionInfo>) {}
    fun onBlockchainHeightFetched(height: Long) {}
    fun onEnotesRefreshed(enotes: List<CoinsInfo>, balance: Balance) {}
    fun onSubaddressesUpdated() {}
    fun onTransactionCreated(pendingTransaction: PendingTransaction) {}
    fun onTransactionSent(pendingTransaction: PendingTransaction, success: Boolean) {}
    fun onProxyUpdated(proxy: String, success: Boolean) {}
    fun onUpdated() {}
    fun onRefreshed() {}
    fun onNewBlock(height: Long) {}
}

enum class WalletServiceCommand {
    UNKNOWN,
    OPEN_WALLET,
    SET_DAEMON_ADDRESS,
    FETCH_BLOCKCHAIN_HEIGHT,
    REFRESH_WALLET_TRANSACTION_HISTORY,
    GENERATE_NEW_SUBADDRESS,
    SET_SUBADDRESS_LABEL,
    CREATE_SWEEP_TX,
    CREATE_TX,
    SEND_TX,
    SET_PROXY,
    REFRESH_ENOTES,
    FREEZE_ENOTES,
    THAW_ENOTES;

    fun value() = ordinal

    companion object {
        fun fromInt(value: Int): WalletServiceCommand? = entries.getOrNull(value)
    }
}