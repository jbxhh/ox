package org.monfluo.wallet.service.wallet

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.monfluo.wallet.BuildConfig
import org.monfluo.wallet.activity.ActivityHome
import org.monfluo.wallet.R
import org.monfluo.wallet.activity.ActivityTransactionDetails
import org.monfluo.wallet.data.Heights
import org.monfluo.wallet.data.SynchronizationStatus
import org.monfluo.wallet.model.PendingTransaction
import org.monfluo.wallet.model.PendingTransactionDestination
import org.monfluo.wallet.model.TransactionDestination
import org.monfluo.wallet.model.TransactionDestinationResolved
import org.monfluo.wallet.model.TransactionDestinationWithAmount
import org.monfluo.wallet.model.TransactionInfo
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.model.Wallet.Companion.NEW_ACCOUNT_NAME
import org.monfluo.wallet.model.WalletListener
import org.monfluo.wallet.model.WalletManager
import org.monfluo.wallet.model.WalletStatusWithError
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Constants.FOREGROUND_SERVICE_NOTIFICATION_CHANNEL
import org.monfluo.wallet.util.Constants.TRANSFERS_NOTIFICATION_CHANNEL
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import timber.log.Timber
import java.time.Clock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.io.path.name

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
        private const val SERVICE_NOTIFICATION_ID: Int = 1
        private val NEXT_NOTIFICATION_ID: AtomicInteger = AtomicInteger(2)
        private const val SERVICE_NOTIFICATION_GROUP = "Wallet Service"
        private const val CLOSE_WALLET_ACTION = "${BuildConfig.APPLICATION_ID}.CLOSE_WALLET"
        private const val DELETE_NOTIFICATION_ACTION = "${BuildConfig.APPLICATION_ID}.DELETE_NOTIFICATION"
    }

    private val clock = Clock.systemUTC()

    private val broadcastReceiver = WalletServiceBroadcastReceiver()

    private val transactionNotifications = mutableMapOf<String, TransactionNotificationData>()

    private var stateRef: AtomicReference<State> = AtomicReference(State())
    private var proxyRef: AtomicReference<String> = AtomicReference("")
    private var walletNameRef: AtomicReference<String> = AtomicReference("")
    private var uninitWalletRef: AtomicReference<Wallet?> = AtomicReference(null)
    private var walletRef: AtomicReference<Wallet?> = AtomicReference(null)
    private var pendingTransactionRef: AtomicReference<PendingTransaction?> = AtomicReference(null)
    private val observers = mutableSetOf<WalletServiceObserver>()

    inner class WalletServiceBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (val action = intent?.action) {
                CLOSE_WALLET_ACTION -> {
                    Timber.i("Received intent with close wallet action")
                    stopSelf()
                }
                DELETE_NOTIFICATION_ACTION -> {
                    val transactionId = intent.getStringExtra(Constants.EXTRA_TRANSACTION_ID)!!
                    Timber.i("Received intent with delete notification action for transaction id $transactionId")
                    val data = transactionNotifications.remove(transactionId)
                    if (data != null) {
                        val notificationId = data.notificationId
                        removeNotification(notificationId)
                        Timber.d("Removed notification for transaction id $transactionId and notification id $notificationId")
                    } else {
                        Timber.w("There is no notification data for transaction $transactionId")
                    }
                }
                null -> {
                    Timber.e("Received intent with no action")
                }
                else -> {
                    Timber.e("Received intent with unknown action: $action")
                }
            }
        }
    }

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
                WalletServiceCommand.INIT_WALLET -> {
                    val daemonAddress = data.getString(EXTRA_DAEMON_ADDRESS)!!
                    val daemonUsername = data.getString(EXTRA_DAEMON_USERNAME)!!
                    val daemonPassword = data.getString(EXTRA_DAEMON_PASSWORD)!!
                    val daemonTrusted = data.getBoolean(EXTRA_DAEMON_TRUSTED, false)
                    val proxy = data.getString(EXTRA_PROXY)!!
                    handleInitWallet(daemonAddress, daemonUsername, daemonPassword, daemonTrusted, proxy)
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
                WalletServiceCommand.FETCH_BLOCKCHAIN_AND_TARGET_HEIGHTS -> {
                    handleFetchBlockchainAndTargetHeights()
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
                Timber.d("Received message to create a transaction")
                    val transactionDestinations = data.getParcelableArrayList<TransactionDestinationWithAmount>(
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
                    val pendingTransaction = this@WalletService.pendingTransactionRef.getAndSet(null)
                    if (pendingTransaction != null) {
                        handleSendPendingTransaction(pendingTransaction)
                    } else {
                        Timber.e("Attempted to send non-existent transaction")
                    }
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

        private fun handleInitWallet(
            daemonAddress: String,
            daemonUsername: String,
            daemonPassword: String,
            daemonTrusted: Boolean,
            proxy: String
        ) {
            val wallet = this@WalletService.uninitWalletRef.getAndSet(null)
            if (wallet == null) {
                Timber.e("Init wallet was called, but uninit wallet ref is null")
                return
            }
            this@WalletService.walletNameRef.set(wallet.info.name)
            preOpenWallet(daemonAddress, proxy)
            postOpenWallet(wallet, daemonAddress, daemonUsername, daemonPassword, daemonTrusted, proxy)
        }

        private fun handleOpenWallet(
            walletPath: String,
            walletPassword: String,
            daemonAddress: String,
            daemonUsername: String,
            daemonPassword: String,
            daemonTrusted: Boolean,
            proxy: String
        ) {
            this@WalletService.walletNameRef.set(runCatching { Path(walletPath).name }.getOrNull())
            preOpenWallet(daemonAddress, proxy)
            val wallet = WalletManager.instance.openWallet(walletPath, walletPassword)
            postOpenWallet(wallet, daemonAddress, daemonUsername, daemonPassword, daemonTrusted, proxy)
        }

        private fun preOpenWallet(
            daemonAddress: String,
            proxy: String
        ) {
            showServiceNotification(createServiceNotificationBuilder().setContentText(getText(R.string.service_notification_description_opening_wallet)).build())
            WalletManager.instance.setProxyJ(proxy)
            WalletManager.instance.setDaemonAddressJ(daemonAddress)
        }

        private fun postOpenWallet(
            wallet: Wallet,
            daemonAddress: String,
            daemonUsername: String,
            daemonPassword: String,
            daemonTrusted: Boolean,
            proxy: String
        ) {
            Timber.i("Initializing wallet with daemon address = $daemonAddress, daemon username = $daemonUsername, daemon password = ${"*".repeat(daemonPassword.length)}, proxy = $proxy")
            wallet.initJ(daemonAddress, 0, daemonUsername, daemonPassword, proxy)
            wallet.setTrustedDaemon(daemonTrusted)

            this@WalletService.stateRef.updateAndGet { state ->
                val walletHeight = wallet.getBlockChainHeightJ()
                Timber.i("Wallet height when opened = $walletHeight")
                val heights = state.heights.copy(walletBeginSyncHeight = walletHeight, walletHeight = walletHeight)
                val status = wallet.getWalletStatusWithError()
                return@updateAndGet state.copy(heights = heights, initialWalletStatus = status)
            }
            updateWallet(wallet)
            this@WalletService.walletRef.set(wallet)

            showServiceNotification(createServiceNotificationBuilder().setContentText(getText(R.string.service_notification_description_fetching_blockchain_height)).build())
            handleFetchBlockchainAndTargetHeights() // TODO: investigate why there is a double free in Monero code when this is moved after startRefresh
            updateSynchronizationStatusNotification(wallet)
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
                showServiceNotification(createServiceNotificationBuilder().setContentText(getText(R.string.service_notification_description_changing_node)).build())
                wallet.initJ(daemonAddress, 0, daemonUsername, daemonPassword, proxy)
                wallet.setTrustedDaemon(daemonTrusted)
                updateWallet(wallet)
                showServiceNotification(createServiceNotificationBuilder().setContentText(getText(R.string.service_notification_description_fetching_blockchain_height)).build())
                handleFetchBlockchainAndTargetHeights()
                updateSynchronizationStatusNotification(wallet)
                wallet.setListener(this@WalletService)
                wallet.startRefresh()
            }
        }

        private fun handleFetchBlockchainAndTargetHeights() {
            val newHeights = WalletManager.instance.getBlockchainAndTargetHeight()
            if (newHeights.first > 0) {
                Timber.i("Fetched blockchain height is ${newHeights.first} and target height is ${newHeights.second}")
                stateRef.updateAndGet { state ->
                    val heights = state.heights.copy(blockchainHeight = newHeights.first, blockchainTargetHeight = newHeights.second)
                    return@updateAndGet state.copy(heights = heights, nodeLastRefreshedAt = clock.millis())
                }
            } else {
                Timber.w("Fetched blockchain height is ${newHeights.first} and target height is ${newHeights.second}")
            }
            forEachObserver {
                it.onBlockchainAndTargetHeightsFetched(newHeights.first, newHeights.second)
            }
        }

        private fun resolveDestination(destination: TransactionDestination): TransactionDestinationResolved {
            val value = destination.value
            if (Wallet.isAddressValid(value)) {
                return destination.toResolved()
            } else if (Helper.isOpenaliasLike(destination.value)) {
                Timber.d("Trying to resolve destination $value because it looks like an openalias")
                val (resolvedAddress, dnssec) = WalletManager.instance.resolveOpenAlias(value)
                Timber.d("Destination $value resolved as $resolvedAddress (dnssec = $dnssec)")
                return destination.toResolved(resolvedAddress, dnssec)
            } else {
                Timber.w("Destination $value is neither a valid address nor looks like an openalias")
                return destination.toResolved()
            }
        }

        private fun handleGenerateNewSubaddress(label: String) {
            val wallet = getWalletOrThrow()
            wallet.addSubaddress(wallet.getAccountIndex(), label)
            wallet.store()
            updateWallet(wallet)
            forEachObserver {
                it.onSubaddressesUpdated(SubaddressUpdateReason.GENERATED_NEW)
            }
        }

        private fun handleSetSubaddressLabel(index: Int, label: String) {
            val wallet = getWalletOrThrow()
            wallet.setSubaddressLabel(index, label)
            wallet.store()
            forEachObserver {
                it.onSubaddressesUpdated(SubaddressUpdateReason.LABEL_CHANGED)
            }
        }

        private fun handleCreateSweepTransaction(
            destination: String,
            priority: PendingTransaction.Priority,
            selectedEnotes: ArrayList<String>
        ) {
            val wallet = getWalletOrThrow()
            val destinationResolved = resolveDestination(TransactionDestination(destination))
            val pendingTransactionHandle = wallet.createSweepTransaction(destinationResolved.address, "", 0, priority.ordinal, wallet.getAccountIndex(), selectedEnotes)
            val pendingTransaction = PendingTransaction(pendingTransactionHandle, PendingTransactionDestination.Sweep(destinationResolved))
            disposePendingTransaction(newPendingTransaction = pendingTransaction)
            forEachObserver {
                it.onTransactionCreated(pendingTransaction)
            }
        }

        private fun handleCreateTransaction(
            destinationAndAmounts: List<TransactionDestinationWithAmount>,
            priority: PendingTransaction.Priority,
            selectedEnotes: ArrayList<String>
        ) {
            val wallet = getWalletOrThrow()
            val destinationsResolved = destinationAndAmounts.map { resolveDestination(it.withoutAmount()).withAmount(it.amount) }
            val destinations = destinationsResolved.map { it.address }
            val amounts = destinationsResolved.map { it.amount }.toLongArray()
            val pendingTransactionHandle = wallet.createTransactionMultDestJ(
                destinations, "", amounts, 0, priority.ordinal,
                wallet.getAccountIndex(), selectedEnotes
            )
            val pendingTransaction = PendingTransaction(pendingTransactionHandle, PendingTransactionDestination.Default(destinationsResolved))
            disposePendingTransaction(newPendingTransaction = pendingTransaction)
            forEachObserver {
                it.onTransactionCreated(pendingTransaction)
            }
        }

        private fun handleSendPendingTransaction(pendingTransaction: PendingTransaction) {
            val success = pendingTransaction.commit("", false)
            if (!success) {
                Timber.e("Failed to send transaction: ${pendingTransaction.getErrorString()}")
            }
            getWalletOrLog()?.store()
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
                wallet.addAccount(label?.ifBlank { null } ?: NEW_ACCOUNT_NAME)
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
            val notification = createServiceNotificationBuilder().build()
            ServiceCompat.startForeground(
                this,
                SERVICE_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )
            val intentFilter = IntentFilter().apply {
                addAction(CLOSE_WALLET_ACTION)
                addAction(DELETE_NOTIFICATION_ACTION)
            }
            ContextCompat.registerReceiver(
                applicationContext,
                broadcastReceiver,
                intentFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            return START_STICKY
        } else {
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        Timber.d("Wallet Service is being destroyed")
        disposePendingTransaction()
        closeWallet()
        removeNotification(SERVICE_NOTIFICATION_ID)
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
        updateSynchronizationStatusNotification(wallet)
        forEachObserver {
            it.onWalletUpdated(wallet)
        }
    }

    override fun moneySpent(txId: String, amount: Long) {
        Timber.d("Money spent callback with txId $txId and amount $amount")
    }

    override fun moneyReceived(txId: String, amount: Long) {
        Timber.d("Money received callback txId $txId and amount $amount")
        handleNewMoneyReceived(txId, amount, confirmed = true)
    }

    override fun unconfirmedMoneyReceived(txId: String, amount: Long) {
        Timber.d("Unconfirmed money received callback txId $txId and amount $amount")
        handleNewMoneyReceived(txId, amount, confirmed = false)
    }

    override fun updated() {
        Timber.d("Updated callback")
    }

    override fun newBlock(height: Long, last: Boolean) {
        Timber.d("New block callback at height $height, is last: $last")
        // Monero heights are fucked up. Wallet and blockchain heights are always +1 to real height, but this callback receives real height
        val prevState = stateRef.getAndUpdate { state ->
            val (_, _, blockchainHeight, blockchainTargetHeight) = state.heights

            val newWalletHeight = height + 1
            val newBlockchainHeight = if (blockchainHeight > 0 && blockchainHeight < (height + 1)) (height + 1) else blockchainHeight
            val newTargetHeight = if (blockchainTargetHeight > newBlockchainHeight) blockchainTargetHeight else newBlockchainHeight

            val heights = state.heights.copy(
                walletHeight = newWalletHeight,
                blockchainHeight = newBlockchainHeight,
                blockchainTargetHeight = newTargetHeight,
            )

            return@getAndUpdate state.copy(heights = heights, walletLastRefreshedAt = clock.millis())
        }
        getWalletOrLog()?.let { wallet ->
            forEachObserver {
                it.onNewBlock(wallet)
            }
            if (last || prevState.heights.walletHeight <= 1L) {
                Timber.d("Storing and updating wallet")
                updateWallet(wallet)
                wallet.store()
            } else if (height % 1000L == 0L) {
                // wallet is probably fast syncing
                Timber.d("Updating synchronization status notification")
                updateSynchronizationStatusNotification(wallet)
            }
            // we do not check here whether user has enabled notifications for transfers
            // because we checked it before adding it to the map
            transactionNotifications.entries.removeIf { entry ->
                val (txId, firstSeenAt, notificationId) = entry.value
                val transaction = wallet.getTransaction(txId)
                if (transaction != null) {
                    Timber.d("Updating a notification on new block for transaction id $txId and notification id $notificationId")
                    createAndShowTransferNotification(
                        transaction.amount,
                        txId,
                        firstSeenAt,
                        transaction.confirmations.coerceAtMost(Constants.CONFIRMATIONS.toLong()).toInt(),
                        notificationId
                    )
                    return@removeIf transaction.confirmations >= Constants.CONFIRMATIONS
                } else {
                    Timber.w("Transaction with id $txId not found, but there was a notification created for it with id $notificationId")
                    removeNotification(notificationId)
                    return@removeIf true
                }
            }
        }
    }

    override fun refreshed() {
        Timber.d("Refreshed callback")
        val wallet = getWallet()
        val newWalletHeight = wallet?.getBlockChainHeightJ()
        val oldState = stateRef.updateAndGet { state ->
            state.copy(walletLastRefreshedAt = clock.millis(), heights = state.heights.copy(walletHeight = newWalletHeight ?: state.heights.walletHeight))
        }
        if (wallet != null && newWalletHeight != null && oldState.heights.walletHeight < newWalletHeight) {
            forEachObserver {
                it.onWalletUpdated(wallet)
            }
        }
        if (oldState.heights.blockchainHeight == 0L) {
            Timber.d("Fetching blockchain height")
            fetchBlockchainAndTargetHeights()
        }
        getWalletOrLog()?.let { wallet ->
            updateWallet(wallet)
        }
    }

    private fun handleNewMoneyReceived(txId: String, amount: Long, confirmed: Boolean) {
        if (!PreferenceUtils.isShowTransferNotifications(this)) {
            Timber.d("Not spawning transfer notification because transfer notifications are disabled")
            return
        }

        val wallet = walletRef.get()

        if (wallet == null) {
            val confirmedText = if (confirmed) "confirmed" else "unconfirmed"
            Timber.w("Wallet is null in new $confirmedText money received callback")
            return
        }

        Timber.d("Refreshing wallet transaction history")
        wallet.refreshTransactionHistory()
        val transaction = wallet.getTransaction(txId)
        if (transaction == null) {
            Timber.w("Transaction $txId not found despite just getting a callback about it")
            return
        }

        if (transaction.direction == TransactionInfo.Direction.Out) {
            // we are getting back our change, not new money
            Timber.d("Not spawning a notification for incoming money because it is an outgoing transaction")
            return
        }

        val confirmations = transaction.confirmations

        val time = System.currentTimeMillis()
        val notificationId = createAndShowTransferNotification(amount, txId, time, confirmations.toInt())
        transactionNotifications[txId] = TransactionNotificationData(txId, time, notificationId)
        Timber.d("Spawned a notification for new money received with transaction id $txId and notification id $notificationId")
    }

    private fun updateSynchronizationStatusNotification(wallet: Wallet) {
        val broadcastIntent = Intent(CLOSE_WALLET_ACTION)
        val broadcastPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(application, 0, broadcastIntent, PendingIntent.FLAG_IMMUTABLE)

        showServiceNotification(
            createServiceNotificationBuilder()
                .setContentText(
                    SynchronizationStatus.from(stateRef.get()).getText(applicationContext)
                )
                .addAction(NotificationCompat.Action.Builder(R.drawable.monfluo, getText(R.string.service_notification_close_wallet_action), broadcastPendingIntent).build())
                .build())
    }

    private fun removeNotification(notificationId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    private fun showServiceNotification(notification: Notification) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
    }

    private fun createServiceNotificationBuilder(): NotificationCompat.Builder {
        val openActivityHomeIntent = Intent(this, ActivityHome::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openActivityHomePendingIntent = PendingIntent.getActivity(application, 1, openActivityHomeIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, FOREGROUND_SERVICE_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.monfluo_notification_icon)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setGroup(SERVICE_NOTIFICATION_GROUP)
            .setContentTitle(getText(R.string.service_notification_header))
            .setContentIntent(openActivityHomePendingIntent)
    }

    private fun createAndShowTransferNotification(amount: Long, txId: String, firstSeenAt: Long, progress: Int, existingId: Int? = null): Int {
        val id: Int = existingId ?: NEXT_NOTIFICATION_ID.getAndIncrement()

        val deleteNotificationBroadcastIntent = Intent(DELETE_NOTIFICATION_ACTION).apply {
            putExtra(Constants.EXTRA_TRANSACTION_ID, txId)
        }
        val deleteNotificationBroadcastPendingIntent = PendingIntent.getBroadcast(application, id, deleteNotificationBroadcastIntent, PendingIntent.FLAG_IMMUTABLE)

        val openTransactionActivityIntent = Intent(this, ActivityTransactionDetails::class.java).apply {
            putExtra(Constants.EXTRA_TRANSACTION_ID, txId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openTransactionActivityPendingIntent = PendingIntent.getActivity(application, id, openTransactionActivityIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, TRANSFERS_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.monfluo_notification_icon)
            .setOnlyAlertOnce(true)
            .setWhen(firstSeenAt)
            .setContentTitle(getString(R.string.transfer_notification_header, Wallet.getDisplayAmount(amount), getString(R.string.unit_xmr)))
            .setContentText(getString(R.string.transfer_notification_description_transaction_id, txId))
            .setProgress(Constants.CONFIRMATIONS, progress, false)
            .setContentIntent(openTransactionActivityPendingIntent)
            .setDeleteIntent(deleteNotificationBroadcastPendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, notification)
        return id
    }

    fun addObserver(observer: WalletServiceObserver) {
        Timber.d("An observer ${observer.javaClass.simpleName} is being added")
        observer.lifecycle.addObserver(this)
        synchronized(observers) {
            observers.add(observer)
        }
    }

    fun getState(): State {
        return stateRef.get()
    }

    fun getProxy(): String {
        return proxyRef.get()
    }

    fun getWallet(): Wallet? {
        return walletRef.get()
    }

    fun getWalletName(): String? {
        return walletNameRef.get()
    }

    fun getWalletOrThrow(): Wallet {
        val wallet = this.walletRef.get()
        if (wallet != null) {
            return wallet
        } else {
            throw IllegalStateException("Get wallet called on null wallet")
        }
    }

    fun getWalletOrLog(): Wallet? {
        val wallet = this.walletRef.get()
        if (wallet != null) {
            return wallet
        } else {
            val e = IllegalStateException("Get wallet called on null wallet")
            Timber.e(e, "Get wallet called on null wallet (log only)")
            return null
        }
    }

    private fun disposePendingTransaction(newPendingTransaction: PendingTransaction? = null) {
        val pendingTransaction = pendingTransactionRef.getAndSet(newPendingTransaction)
        if (pendingTransaction != null) {
            val wallet = getWallet()
            if (wallet != null) {
                wallet.disposeTransaction(pendingTransaction)
            } else {
                Timber.e("Could not dispose of a transaction because there is no wallet")
            }
        }
    }

    fun getPendingTransaction(): PendingTransaction? {
        return pendingTransactionRef.get();
    }

    fun setUninitWallet(
        wallet: Wallet,
    ) {
        uninitWalletRef.set(wallet)
    }

    // used by activities that CREATE wallets (and therefore have a wallet already opened but not initialized)
    // setUninitWallet must be called before that method
    fun initWallet(
        daemonAddress: String,
        daemonUsername: String,
        daemonPassword: String,
        daemonTrusted: Boolean,
        proxy: String
    ) {
        closeWallet()
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.INIT_WALLET.value()
        message.data.putString(EXTRA_DAEMON_ADDRESS, daemonAddress)
        message.data.putString(EXTRA_DAEMON_USERNAME, daemonUsername)
        message.data.putString(EXTRA_DAEMON_PASSWORD, daemonPassword)
        message.data.putBoolean(EXTRA_DAEMON_TRUSTED, daemonTrusted)
        message.data.putString(EXTRA_PROXY, proxy)
        handler.sendMessage(message)
    }

    // used by activities that [want to] OPEN wallets (but do not have one opened yet)
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

    private fun fetchBlockchainAndTargetHeights() {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.FETCH_BLOCKCHAIN_AND_TARGET_HEIGHTS.value()
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

    fun createTransaction(destinations: List<TransactionDestinationWithAmount>, priority: PendingTransaction.Priority, selectedUtxos: ArrayList<String>) {
        Timber.d("Sending message to create a transaction")
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
    fun onNewBlock(wallet: Wallet) {}
    fun onBlockchainAndTargetHeightsFetched(blockchainHeight: Long, targetHeight: Long) {}
    fun onSubaddressesUpdated(reason: SubaddressUpdateReason) {}
    fun onTransactionCreated(pendingTransaction: PendingTransaction) {}
    fun onTransactionSent(pendingTransaction: PendingTransaction, success: Boolean) {}
    fun onProxyUpdated(proxy: String, success: Boolean) {}
    fun onAccountCreated() {}
    fun onAccountSet(index: Int) {}
    fun onAccountLabelChanged(index: Int, label: String) {}
}

enum class SubaddressUpdateReason {
    LABEL_CHANGED,
    GENERATED_NEW,
}

data class State(
    val heights: Heights = Heights(),
    val initialWalletStatus: WalletStatusWithError? = null,
    val walletLastRefreshedAt: Long? = null,
    val nodeLastRefreshedAt: Long? = null,
)

private enum class WalletServiceCommand {
    UNKNOWN, // to catch bugs when command is set to 0 by accident
    INIT_WALLET,
    OPEN_WALLET,
    CLOSE_WALLET,
    SET_DAEMON_ADDRESS,
    FETCH_BLOCKCHAIN_AND_TARGET_HEIGHTS,
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

private data class TransactionNotificationData(
    val transactionId: String,
    val firstSeenAt: Long,
    val notificationId: Int,
)