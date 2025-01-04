package net.mynero.wallet.service.wallet

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import androidx.lifecycle.AtomicReference
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import net.mynero.wallet.model.Enote
import net.mynero.wallet.model.PendingTransaction
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.model.Wallet.Companion.NEW_ACCOUNT_NAME
import net.mynero.wallet.model.WalletListener
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.TransactionDestination
import timber.log.Timber
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val EXTRA_WALLET_NAME = "wallet_name"
private const val EXTRA_WALLET_PASSWORD = "wallet_password"
private const val EXTRA_DAEMON_ADDRESS = "daemon_address"
private const val EXTRA_DAEMON_USERNAME = "daemon_username"
private const val EXTRA_DAEMON_PASSWORD = "daemon_password"
private const val EXTRA_DAEMON_TRUSTED = "daemon_trusted"
private const val EXTRA_SUBADDRESS_LABEL = "subaddress_label"
private const val EXTRA_SUBADDRESS_INDEX = "subaddress_index"
private const val EXTRA_ACCOUNT_LABEL = "account_label"
private const val EXTRA_ACCOUNT_INDEX = "account_index"
private const val EXTRA_TRANSACTION_DESTINATION = "transaction_destination"
private const val EXTRA_TRANSACTION_DESTINATIONS = "transaction_destinations"
private const val EXTRA_TRANSACTION_PRIORITY = "transaction_priority"
private const val EXTRA_TRANSACTION_SELECTED_UTXOS = "transaction_selected_utxos"
private const val EXTRA_PENDING_TRANSACTION = "pending_transaction"
private const val EXTRA_PROXY = "proxy"
private const val EXTRA_ENOTE = "enote"
private const val EXTRA_ENOTES = "enotes"

class WalletService : Service(), WalletListener, DefaultLifecycleObserver {

    companion object {
        val RUNNING: AtomicBoolean = AtomicBoolean(false)
    }

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
                    val walletPassword = data.getString(EXTRA_WALLET_PASSWORD)!!
                    val daemonAddress = data.getString(EXTRA_DAEMON_ADDRESS)!!
                    val daemonUsername = data.getString(EXTRA_DAEMON_USERNAME)!!
                    val daemonPassword = data.getString(EXTRA_DAEMON_PASSWORD)!!
                    val daemonTrusted = data.getBoolean(EXTRA_DAEMON_TRUSTED, false)
                    val proxy = data.getString(EXTRA_PROXY)!!
                    handleOpenWallet(walletName, walletPassword, daemonAddress, daemonUsername, daemonPassword, daemonTrusted, proxy)
                }
                WalletServiceCommand.CLOSE_WALLET -> {
                    handleCloseWallet()
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
                WalletServiceCommand.GENERATE_NEW_ADDRESS -> {
                    val subaddressLabel = data.getString(EXTRA_SUBADDRESS_LABEL)!!
                    handleGenerateNewSubaddress(subaddressLabel)
                }
                WalletServiceCommand.SET_ADDRESS_LABEL -> {
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
                WalletServiceCommand.FREEZE_ENOTES -> {
                    val enotes = data.getLongArray(EXTRA_ENOTES)!!
                    handleFreezeEnotes(enotes)
                }
                WalletServiceCommand.THAW_ENOTES -> {
                    val enotes = data.getLongArray(EXTRA_ENOTES)!!
                    handleThawEnotes(enotes)
                }
                WalletServiceCommand.CREATE_ACCOUNT -> {
                    val label = data.getString(EXTRA_ACCOUNT_LABEL)
                    handleCreateAccount(label)
                }
                WalletServiceCommand.SET_ACCOUNT -> {
                    val index = data.getInt(EXTRA_ACCOUNT_INDEX)
                    handleSetAccount(index)
                }
                WalletServiceCommand.SET_ACCOUNT_LABEL -> {
                    val index = data.getInt(EXTRA_ACCOUNT_INDEX)
                    val label = data.getString(EXTRA_ACCOUNT_LABEL)!!
                    handleSetAccountLabel(index, label)
                }
            }
        }

        private fun handleOpenWallet(
            walletName: String,
            walletPassword: String,
            daemonAddress: String,
            daemonUsername: String,
            daemonPassword: String,
            daemonTrusted: Boolean,
            proxy: String
        ) {
            WalletManager.instance.setProxyJ(proxy)
            WalletManager.instance.setDaemonAddressJ(daemonAddress)
            val wallet = WalletManager.instance.openWallet(walletName, walletPassword)
            Timber.i("Initializing wallet with daemon address = $daemonAddress, daemon username = $daemonUsername, daemon password = ${"*".repeat(daemonPassword.length)}, proxy = $proxy")
            wallet.initJ(daemonAddress, 0, daemonUsername, daemonPassword, proxy)
            wallet.setTrustedDaemon(daemonTrusted)

            this@WalletService.walletBeginSyncHeight.set(wallet.getBlockChainHeightJ())
            updateWallet(wallet)
            this@WalletService.walletRef.set(wallet)

            handleFetchDaemonHeight() // TODO: investigate why there is a double free in Monero code when this is moved after startRefresh
            wallet.setListener(this@WalletService)
            wallet.startRefresh()
        }

        private fun handleCloseWallet() {
            walletRef.getAndSet(null)?.let { wallet ->
                wallet.setListener(null)
                wallet.close()
            }
        }

        private fun setDaemonAddress(
            daemonAddress: String,
            daemonUsername: String,
            daemonPassword: String,
            daemonTrusted: Boolean,
            proxy: String
        ) {
            WalletManager.instance.setProxyJ(proxy)
            WalletManager.instance.setDaemonAddressJ(daemonAddress)
            walletRef.get()?.let { wallet ->
                Timber.i("Re-initializing wallet with daemon address = $daemonAddress, daemon username = $daemonUsername, daemon password = ${"*".repeat(daemonPassword.length)}, proxy = $proxy")
                wallet.initJ(daemonAddress, 0, daemonUsername, daemonPassword, proxy)
                wallet.setTrustedDaemon(daemonTrusted)
                updateWallet(wallet)
                handleFetchDaemonHeight()
                wallet.setListener(this@WalletService)
                wallet.startRefresh()
            }
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

        private fun handleGenerateNewSubaddress(label: String) {
            val wallet = getWalletOrThrow()
            wallet.addSubaddress(wallet.getAccountIndex(), label)
            wallet.store()
            updateWallet(wallet)
            forEachObserver {
                it.onSubaddressesUpdated()
            }
        }

        private fun handleSetSubaddressLabel(index: Int, label: String) {
            val wallet = getWalletOrThrow()
            wallet.setSubaddressLabel(index, label)
            wallet.store()
            forEachObserver {
                it.onSubaddressesUpdated()
            }
        }

        private fun handleCreateSweepTransaction(
            destination: String,
            feePriority: PendingTransaction.Priority,
            selectedUtxos: ArrayList<String>
        ) {
            val wallet = getWalletOrThrow()
            val pendingTransaction = wallet.createSweepTransaction(destination, feePriority, selectedUtxos)
            forEachObserver {
                it.onTransactionCreated(pendingTransaction)
            }
        }

        private fun handleCreateTransaction(
            destinations: List<TransactionDestination>,
            feePriority: PendingTransaction.Priority,
            selectedUtxos: ArrayList<String>
        ) {
            val wallet = getWalletOrThrow()
            val pendingTransaction = wallet.createTransactionMultDest(destinations, feePriority, selectedUtxos)
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

        private fun handleSetProxy(value: String) {
            val success = WalletManager.instance.setProxyJ(value)
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

        private fun handleFreezeEnotes(enotes: LongArray) {
            val wallet = walletRef.get()!!
            enotes.forEach {
                wallet.freeze(it)
            }
            wallet.store("")
            updateWallet(wallet)
        }

        private fun handleThawEnotes(enotes: LongArray) {
            val wallet = walletRef.get()!!
            enotes.forEach {
                wallet.thaw(it)
            }
            wallet.store("")
            updateWallet(wallet)
        }

        private fun handleCreateAccount(
            label: String?
        ) {
            getWallet()?.let { wallet ->
                wallet.addAccount(label ?: NEW_ACCOUNT_NAME)
                wallet.store("")
                forEachObserver {
                    it.onAccountCreated()
                }
            }
        }

        private fun handleSetAccount(
            index: Int
        ) {
            getWallet()?.let { wallet ->
                wallet.setAccountIndex(index)
                updateWallet(wallet)
                forEachObserver {
                    it.onAccountSet(index)
                }
            }
        }

        private fun handleSetAccountLabel(
            index: Int,
            label: String
        ) {
            getWallet()?.let { wallet ->
                wallet.setAccountLabel(index, label)
                wallet.store("")
                updateWallet(wallet)
                forEachObserver {
                    it.onAccountLabelChanged(index, label)
                }
            }
        }
    }

    private val mBinder: IBinder = WalletServiceBinder()

    private lateinit var handler: WalletHandler

    override fun onBind(intent: Intent?): IBinder {
        Timber.d("A component is binding")
        return mBinder
    }

    override fun onCreate() {
        Timber.d("Wallet Service is being created")
        // ArrayBlockingQueue is used as a oneshot channel to receive a Looper from MoneroHandlerThread
        val oneShotQueue = ArrayBlockingQueue<Looper>(1)
        val thread = MoneroHandlerThread { looper ->
            oneShotQueue.add(looper)
        }
        thread.start()

        val looper = oneShotQueue.take()
        handler = WalletHandler(looper)
        RUNNING.set(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("Wallet Service is being started")
        if (intent != null) {
            return START_STICKY
        } else {
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        Timber.d("Wallet Service is being destroyed")
        closeWallet()
        RUNNING.set(false)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Timber.d("An observer ${owner.javaClass.simpleName} is being destroyed")
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

    private fun updateWallet(wallet: Wallet) {
        wallet.update()
        forEachObserver {
            it.onWalletUpdated(wallet)
        }
    }

    override fun moneySpent(txId: String?, amount: Long) {
        Timber.d("Money spent callback")
    }

    override fun moneyReceived(txId: String?, amount: Long) {
        Timber.d("Money received callback")
    }

    override fun unconfirmedMoneyReceived(txId: String?, amount: Long) {
        Timber.d("Unconfirmed money received callback")
    }

    override fun newBlock(height: Long, last: Boolean) {
        Timber.d("New block callback at height $height, is last: $last")
        // Monero heights are fucked up. Wallet and blockchain heights are always +1 to real height, but this callback receives real height
        daemonHeight.updateAndGet { if (it > 0 && it < (height + 1)) (height + 1) else it }
        val wallet = getWalletOrThrow()
        if (last) {
            Timber.d("Storing and updating wallet because the block is the last one of the batch")
            wallet.store()
            updateWallet(wallet)
        }
    }

    override fun updated() {
        Timber.d("Updated callback")
        updateWallet(getWalletOrThrow())
    }

    override fun refreshed() {
        Timber.d("Refreshed callback")
        if (daemonHeight.get() <= 1) {
            Timber.d("Fetching blockchain height")
            fetchBlockchainHeight()
        }
    }

    fun addObserver(observer: WalletServiceObserver) {
        Timber.d("An observer ${observer.javaClass.simpleName} is being added")
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
        closeWallet()
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

    fun closeWallet() {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.CLOSE_WALLET.value()
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

    fun generateNewSubaddress(label: String) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.GENERATE_NEW_ADDRESS.value()
        message.data.putString(EXTRA_SUBADDRESS_LABEL, label)
        handler.sendMessage(message)
    }

    fun setSubaddressLabel(index: Int, label: String) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SET_ADDRESS_LABEL.value()
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

    fun freezeEnote(enotes: List<Long>) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.FREEZE_ENOTES.value()
        message.data.putLongArray(EXTRA_ENOTES, enotes.toLongArray())
        handler.sendMessage(message)
    }

    fun thawEnote(enotes: List<Long>) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.THAW_ENOTES.value()
        message.data.putLongArray(EXTRA_ENOTES, enotes.toLongArray())
        handler.sendMessage(message)
    }

    fun createAccount(label: String? = null) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.CREATE_ACCOUNT.value()
        message.data.putString(EXTRA_ACCOUNT_LABEL, label)
        handler.sendMessage(message)
    }

    fun setAccount(index: Int) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SET_ACCOUNT.value()
        message.data.putInt(EXTRA_ACCOUNT_INDEX, index)
        handler.sendMessage(message)
    }

    fun setAccountLabel(index: Int, label: String) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SET_ACCOUNT_LABEL.value()
        message.data.putInt(EXTRA_ACCOUNT_INDEX, index)
        message.data.putString(EXTRA_ACCOUNT_LABEL, label)
        handler.sendMessage(message)
    }
}

interface WalletServiceObserver : LifecycleOwner {
    fun onWalletUpdated(wallet: Wallet) {}
    fun onBlockchainHeightFetched(height: Long) {}
    fun onSubaddressesUpdated() {}
    fun onTransactionCreated(pendingTransaction: PendingTransaction) {}
    fun onTransactionSent(pendingTransaction: PendingTransaction, success: Boolean) {}
    fun onProxyUpdated(proxy: String, success: Boolean) {}
    fun onAccountCreated() {}
    fun onAccountSet(index: Int) {}
    fun onAccountLabelChanged(index: Int, label: String) {}
}

enum class WalletServiceCommand {
    UNKNOWN, // to catch bugs when command is set to 0 by accident
    OPEN_WALLET,
    CLOSE_WALLET,
    SET_DAEMON_ADDRESS,
    FETCH_BLOCKCHAIN_HEIGHT,
    GENERATE_NEW_ADDRESS,
    SET_ADDRESS_LABEL,
    CREATE_SWEEP_TX,
    CREATE_TX,
    SEND_TX,
    SET_PROXY,
    FREEZE_ENOTES,
    THAW_ENOTES,
    CREATE_ACCOUNT,
    SET_ACCOUNT,
    SET_ACCOUNT_LABEL;

    fun value() = ordinal

    companion object {
        fun fromInt(value: Int): WalletServiceCommand? = entries.getOrNull(value)
    }
}