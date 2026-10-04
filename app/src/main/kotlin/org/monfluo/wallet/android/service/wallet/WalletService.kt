package org.monfluo.wallet.android.service.wallet

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
import org.monfluo.wallet.android.BuildConfig
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.activity.ActivityHome
import org.monfluo.wallet.android.activity.ActivityTransactionDetails
import org.monfluo.wallet.android.data.Node
import org.monfluo.wallet.android.data.NodeStore
import org.monfluo.wallet.android.model.MoneroUnit
import org.monfluo.wallet.android.model.TransactionDestination
import org.monfluo.wallet.android.model.TransactionDestinationResolved
import org.monfluo.wallet.android.model.TransactionDestinationWithAmount
import org.monfluo.wallet.android.model.TransactionInfo
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.Constants.FOREGROUND_SERVICE_NOTIFICATION_CHANNEL
import org.monfluo.wallet.android.util.Constants.TRANSFERS_NOTIFICATION_CHANNEL
import org.monfluo.wallet.android.util.Helper
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.TextHelper
import org.monfluo.wallet.lib.Monfluo
import org.monfluo.wallet.lib.data.Address
import org.monfluo.wallet.lib.data.FeePriority
import org.monfluo.wallet.lib.data.NodeInfo
import org.monfluo.wallet.lib.data.Payment
import org.monfluo.wallet.lib.data.TransactionView
import org.monfluo.wallet.lib.data.WalletView
import org.monfluo.wallet.lib.wallet.Daemon
import org.monfluo.wallet.lib.wallet.Wallet
import org.monfluo.wallet.lib.wallet.sync.WalletSyncObserver
import org.monfluo.wallet.lib.wallet.sync.WalletSyncState
import timber.log.Timber
import java.text.NumberFormat
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class WalletService :
    Service(),
    DefaultLifecycleObserver {
    companion object {
        val RUNNING: AtomicBoolean = AtomicBoolean(false)
        private const val SERVICE_NOTIFICATION_ID: Int = 1
        private val NEXT_NOTIFICATION_ID: AtomicInteger = AtomicInteger(2)
        private const val SERVICE_NOTIFICATION_GROUP = "Wallet Service"
        private const val CLOSE_WALLET_ACTION = "${BuildConfig.APPLICATION_ID}.CLOSE_WALLET"
        private const val DELETE_NOTIFICATION_ACTION = "${BuildConfig.APPLICATION_ID}.DELETE_NOTIFICATION"
    }

    private val broadcastReceiver = WalletServiceBroadcastReceiver()

    private val transactionNotifications = mutableMapOf<String, TransactionNotificationData>()

    private var walletRef: AtomicReference<WalletWithData?> = AtomicReference(null)
    private var pendingTransactionRef: AtomicReference<TransactionView?> = AtomicReference(null)
    private val observers = mutableSetOf<WalletServiceObserver>()

    // Executor for the wallet's sync thread
    private val syncExecutor = Executors.newSingleThreadScheduledExecutor()
    private var syncScheduledFuture: ScheduledFuture<*>? = null

    // Executor for the wallet's sync callbacks
    // Callbacks may call Monfluo's native methods, which use tokio's block_on function.
    // The wallet sync itself is executing inside block_on on the sync thread, so the callbacks
    // MUST run on a different thread, otherwise the nested block_on would never get a runtime worker
    private val syncCallbackExecutor = Executors.newSingleThreadScheduledExecutor()

    inner class WalletServiceBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context?,
            intent: Intent?,
        ) {
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

    inner class WalletHandler(
        looper: Looper,
    ) : Handler(looper) {
        override fun handleMessage(message: Message) {
            val commandType = message.arg1
            val data = message.data
            when (WalletServiceCommand.fromInt(commandType)) {
                WalletServiceCommand.UNKNOWN, null -> {
                    Timber.e("WalletHandler received UNKNOWN(%d) command", commandType)
                }
                WalletServiceCommand.OPEN_WALLET -> {
                    val wallet = data.getString(Constants.EXTRA_WALLET_NAME)!!
                    val walletPassword = data.getString(Constants.EXTRA_WALLET_PASSWORD)!!
                    handleOpenWallet(wallet, walletPassword)
                }
                WalletServiceCommand.RECONNECT_DAEMON -> {
                    handleReconnectDaemon()
                }
                WalletServiceCommand.SET_PASSWORD -> {
                    val newPassword = data.getString(Constants.EXTRA_NEW_PASSWORD)!!
                    handleSetPassword(newPassword)
                }
                WalletServiceCommand.SET_ADDRESS_LABEL -> {
                    val accountIndex = data.getInt(Constants.EXTRA_ACCOUNT_INDEX)
                    val addressIndex = data.getInt(Constants.EXTRA_ADDRESS_INDEX)
                    val addressLabel = data.getString(Constants.EXTRA_ADDRESS_LABEL)!!
                    handleSetAddressLabel(accountIndex, addressIndex, addressLabel)
                }
                WalletServiceCommand.GENERATE_NEW_ADDRESS -> {
                    val accountIndex = data.getInt(Constants.EXTRA_ACCOUNT_INDEX)
                    handleGenerateNewAddress(accountIndex)
                }
                WalletServiceCommand.CREATE_SWEEP_TX -> {
                    Timber.d("Received message to create a sweep transaction")
                    val transactionDestination = data.getString(Constants.EXTRA_TRANSACTION_DESTINATION)!!
                    val transactionPriority =
                        FeePriority.fromInt(
                            message.data.getInt(
                                Constants.EXTRA_TRANSACTION_PRIORITY,
                            ),
                        )
                    val selectedEnotes =
                        message.data.getStringArrayList(
                            Constants.EXTRA_TRANSACTION_SELECTED_ENOTES,
                        )
                    val transactionDescription = data.getString(Constants.EXTRA_TRANSACTION_DESCRIPTION)
                    handleCreateSweepTransaction(transactionDestination, transactionPriority, selectedEnotes, transactionDescription)
                }
                WalletServiceCommand.CREATE_TX -> {
                    Timber.d("Received message to create a transaction")
                    val transactionDestinations =
                        data.getParcelableArrayList<TransactionDestinationWithAmount>(
                            Constants.EXTRA_TRANSACTION_DESTINATIONS,
                        )!!
                    val transactionPriority =
                        FeePriority.fromInt(
                            message.data.getInt(
                                Constants.EXTRA_TRANSACTION_PRIORITY,
                            ),
                        )
                    val selectedEnotes =
                        message.data.getStringArrayList(
                            Constants.EXTRA_TRANSACTION_SELECTED_ENOTES,
                        )
                    val transactionDescription = data.getString(Constants.EXTRA_TRANSACTION_DESCRIPTION)
                    handleCreateTransaction(transactionDestinations, transactionPriority, selectedEnotes, transactionDescription)
                }
                WalletServiceCommand.SEND_TX -> {
                    val pendingTransaction = this@WalletService.pendingTransactionRef.getAndSet(null)
                    if (pendingTransaction != null) {
                        handleSendPendingTransaction(pendingTransaction)
                    } else {
                        Timber.e("Attempted to send non-existent transaction")
                    }
                }
                WalletServiceCommand.FREEZE_ENOTES -> {
                    val enotes = data.getLongArray(Constants.EXTRA_ENOTES)!!
                    handleFreezeEnotes(enotes)
                }
                WalletServiceCommand.THAW_ENOTES -> {
                    val enotes = data.getLongArray(Constants.EXTRA_ENOTES)!!
                    handleThawEnotes(enotes)
                }
                WalletServiceCommand.SET_ACCOUNT_LABEL -> {
                    val index = data.getInt(Constants.EXTRA_ACCOUNT_INDEX)
                    val label = data.getString(Constants.EXTRA_ACCOUNT_LABEL)!!
                    handleSetAccountLabel(index, label)
                }
                WalletServiceCommand.CREATE_ACCOUNT -> {
                    handleCreateAccount()
                }
                WalletServiceCommand.SET_ACCOUNT -> {
                    val index = data.getInt(Constants.EXTRA_ACCOUNT_INDEX)
                    handleSetAccount(index)
                }
            }
        }

        private fun handleOpenWallet(
            wallet: String,
            walletPassword: String,
        ) {
            val wallet = Monfluo.openWallet(Helper.getWalletRoot(this@WalletService).absolutePath, wallet, walletPassword)
            val walletView = wallet.getWalletView()
            val walletSyncData = WalletSyncData(DaemonState.NotSelected, walletView.currentHeight ?: 0)
            val walletWithData = WalletWithData(wallet, walletSyncData, walletView)
            applyDaemon(walletWithData, NodeStore.selected(this@WalletService, wallet.network()))
            walletRef.set(walletWithData)
            forEachObserver {
                it.onWalletOpened(walletWithData)
            }

            syncScheduledFuture =
                syncExecutor.scheduleWithFixedDelay(
                    { sync(walletWithData) },
                    0,
                    // TODO: should be a wallet setting
                    15,
                    TimeUnit.SECONDS,
                )
        }

        private fun sync(
            walletWithData: WalletWithData,
        ) {
            val daemon = walletWithData.data.connectedDaemon()
            if (daemon == null) {
                Timber.w("Cannot sync because there is no daemon")
                return
            }

            Timber.i("Starting wallet sync")
            walletWithData.wallet.sync(
                daemon,
                object : WalletSyncObserver {
                    override fun observe(state: WalletSyncState) {
                        Timber.i("Observed wallet sync state = $state")
                        walletWithData.data.lastSyncState = state
                        if (state.isTerminal) {
                            walletWithData.data.lastTerminalSyncState = state
                        }
                        when (state) {
                            is WalletSyncState.Starting -> walletWithData.data.lastNodeInfo = SyncNodeInfo(state.timestamp, state.nodeInfo)
                            is WalletSyncState.Syncing -> walletWithData.data.latestHeight = state.currentHeight
                            is WalletSyncState.Finished -> walletWithData.data.syncCompleted = true
                            else -> {}
                        }
                        refreshWalletView(walletWithData)
                        updateSynchronizationStatusNotification(walletWithData)
                        forEachObserver {
                            it.onWalletUpdated(walletWithData)
                        }
                    }
                },
            )
            Timber.i("Wallet sync finished")
        }

        private fun handleReconnectDaemon() {
            val walletWithData = getWalletOrLog() ?: return
            applyDaemon(walletWithData, NodeStore.selected(this@WalletService, walletWithData.wallet.network()))
            forEachObserver {
                it.onWalletUpdated(walletWithData)
            }
        }

        private fun resolveDestination(destination: TransactionDestination): TransactionDestinationResolved {
            if (Helper.isOpenaliasLike(destination.value)) {
                Timber.w("Not resolving destination ${destination.value} because openalias is not implemented yet")
            }
            return destination.toResolved()
        }

        private fun handleGenerateNewAddress(accountIndex: Int) {
            val walletWithData = getWalletOrLog() ?: return
            walletWithData.wallet.registerNewAddress(accountIndex)
            walletWithData.wallet.store()
            refreshWalletView(walletWithData)
            forEachObserver {
                it.onAddressGenerated()
            }
        }

        private fun handleCreateSweepTransaction(
            destination: String,
            priority: FeePriority,
            _selectedEnotes: ArrayList<String>?,
            _transactionDescription: String?,
        ) {
            publishTransactionCreation(runCatching { createSweepTransaction(destination, priority) })
        }

        private fun handleCreateTransaction(
            destinationAndAmounts: List<TransactionDestinationWithAmount>,
            priority: FeePriority,
            _selectedEnotes: ArrayList<String>?,
            _transactionDescription: String?,
        ) {
            publishTransactionCreation(runCatching { createTransaction(destinationAndAmounts, priority) })
        }

        private fun publishTransactionCreation(result: Result<TransactionView>) {
            result.fold(
                onSuccess = { transaction ->
                    this@WalletService.pendingTransactionRef.set(transaction)
                    forEachObserver {
                        it.onTransactionCreated(transaction)
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to create transaction")
                    forEachObserver {
                        it.onTransactionCreateFailed(error)
                    }
                },
            )
        }

        private fun createSweepTransaction(
            destination: String,
            priority: FeePriority,
        ): TransactionView {
            val (wallet, data) = getWalletOrLog() ?: throw IllegalStateException("No wallet is open")
            val daemon = data.connectedDaemon() ?: throw IllegalStateException("No node is selected")
            val address = Address.fromString(destination) ?: throw IllegalArgumentException("Invalid destination address")

            return wallet.createAndSignSweepTransaction(daemon, wallet.currentAccountIndex, address, priority)
        }

        private fun createTransaction(
            destinations: List<TransactionDestinationWithAmount>,
            priority: FeePriority,
        ): TransactionView {
            val (wallet, data) = getWalletOrLog() ?: throw IllegalStateException("No wallet is open")
            val daemon = data.connectedDaemon() ?: throw IllegalStateException("No node is selected")
            val payments =
                destinations.map { destination ->
                    val address = Address.fromString(destination.value) ?: throw IllegalArgumentException("Invalid destination address")
                    Payment(address, destination.amount)
                }

            return wallet.createAndSignTransaction(daemon, wallet.currentAccountIndex, payments, priority)
        }

        private fun handleSendPendingTransaction(transaction: TransactionView) {
            val walletWithData = getWalletOrLog()!!
            val daemon = walletWithData.data.connectedDaemon() ?: return Timber.e("Cannot send a transaction because there is no daemon")
            walletWithData.wallet.sendTransaction(daemon, transaction.transactionHash)
//            wallet.setUserNote(txid, description)
//            Timber.i("Set note for txid $txid to $description")
            walletWithData.wallet.store()
            refreshWalletView(walletWithData)
            forEachObserver {
                it.onTransactionSent(transaction, true)
                it.onWalletUpdated(walletWithData)
            }
        }


        private fun handleSetPassword(_newPassword: String) {
            Timber.w("Changing the wallet password is not implemented yet")
        }

        private fun handleFreezeEnotes(_enotes: LongArray) {
            Timber.w("Freezing enotes is not implemented yet")
        }

        private fun handleThawEnotes(_enotes: LongArray) {
            Timber.w("Thawing enotes is not implemented yet")
        }

        private fun handleSetAddressLabel(
            _accountIndex: Int,
            _addressIndex: Int,
            _label: String,
        ) {
            Timber.w("Setting address labels is not implemented yet")
        }

        private fun handleSetAccountLabel(
            _index: Int,
            _label: String,
        ) {
            Timber.w("Setting account labels is not implemented yet")
        }

        private fun handleCreateAccount() {
            val walletWithData = getWalletOrLog() ?: return
            walletWithData.wallet.registerNewAccount()
            walletWithData.wallet.store()
            refreshWalletView(walletWithData)
            forEachObserver {
                it.onAccountCreated()
            }
        }

        private fun handleSetAccount(
            index: Int,
        ) {
            val walletWithData = getWalletOrLog() ?: return
            walletWithData.wallet.currentAccountIndex = index
            forEachObserver {
                it.onAccountSet(index)
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
        val thread =
            MoneroHandlerThread { looper ->
                oneShotQueue.add(looper)
            }
        thread.start()

        val looper = oneShotQueue.take()
        handler = WalletHandler(looper)
        RUNNING.set(true)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
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
                },
            )
            val intentFilter =
                IntentFilter().apply {
                    addAction(CLOSE_WALLET_ACTION)
                    addAction(DELETE_NOTIFICATION_ACTION)
                }
            ContextCompat.registerReceiver(
                applicationContext,
                broadcastReceiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            return START_STICKY
        } else {
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        Timber.d("Wallet Service is being destroyed")
        closeWallet()
        syncScheduledFuture?.cancel(false)
        syncExecutor.shutdownNow()
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
            val future =
                syncCallbackExecutor.submit {
                    for (observer in observers) {
                        f(observer)
                    }
                }
            future.get()
        }
    }

    private fun refreshWalletView(walletWithData: WalletWithData) {
        val future =
            syncCallbackExecutor.submit(Callable { walletWithData.wallet.getWalletView() })
        walletWithData.walletView = future.get()
    }



    private fun updateSynchronizationStatusNotification(walletWithData: WalletWithData) {
        val broadcastIntent = Intent(CLOSE_WALLET_ACTION)
        val broadcastPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(application, 0, broadcastIntent, PendingIntent.FLAG_IMMUTABLE)

        val nf = NumberFormat.getInstance()
        val text: CharSequence =
            when (val state = walletWithData.data.getAdjustedLastSyncState()) {
                null ->
                    if (walletWithData.data.daemon is DaemonState.Failed) {
                        application.getString(R.string.wallet_node_unavailable)
                    } else {
                        application.getString(R.string.wallet_not_connected)
                    }
                is WalletSyncState.Preparing -> application.getString(R.string.starting_wallet_synchronization)
                is WalletSyncState.Syncing, is WalletSyncState.Starting -> {
                    val currentHeight = state.currentHeight!!
                    val targetHeight =
                        max(
                            walletWithData.data.lastNodeInfo!!
                                .nodeInfo.height - 1,
                            currentHeight,
                        )
                    application.getString(R.string.wallet_synchronization_blocks_remaining, nf.format(state.currentHeight), nf.format(targetHeight), nf.format(targetHeight - currentHeight))
                }
                is WalletSyncState.Finished -> application.getString(R.string.synchronized_at_label, nf.format(state.height))
                is WalletSyncState.Cancelled -> application.getString(R.string.sync_cancelled)
                is WalletSyncState.Failed -> application.getString(R.string.wallet_error, state.error.toString())
            }

        showServiceNotification(
            createServiceNotificationBuilder()
                .setContentText(text)
                .addAction(NotificationCompat.Action.Builder(R.drawable.monfluo, getText(R.string.service_notification_close_wallet_action), broadcastPendingIntent).build())
                .build(),
        )
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
        val openActivityHomeIntent =
            Intent(this, ActivityHome::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        val openActivityHomePendingIntent = PendingIntent.getActivity(application, 1, openActivityHomeIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat
            .Builder(this, FOREGROUND_SERVICE_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.monfluo_notification_icon)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setGroup(SERVICE_NOTIFICATION_GROUP)
            .setContentTitle(getText(R.string.service_notification_header))
            .setContentIntent(openActivityHomePendingIntent)
    }

    private fun createAndShowTransferNotification(
        amount: Long,
        txId: String,
        firstSeenAt: Long,
        progress: Int,
        existingId: Int? = null,
    ): Int {
        val id: Int = existingId ?: NEXT_NOTIFICATION_ID.getAndIncrement()

        val deleteNotificationBroadcastIntent =
            Intent(DELETE_NOTIFICATION_ACTION).apply {
                setPackage(packageName)
                putExtra(Constants.EXTRA_TRANSACTION_ID, txId)
            }
        val deleteNotificationBroadcastPendingIntent = PendingIntent.getBroadcast(application, id, deleteNotificationBroadcastIntent, PendingIntent.FLAG_IMMUTABLE)

        val openTransactionActivityIntent =
            Intent(this, ActivityTransactionDetails::class.java).apply {
                putExtra(Constants.EXTRA_TRANSACTION_ID, txId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val openTransactionActivityPendingIntent = PendingIntent.getActivity(application, id, openTransactionActivityIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification =
            NotificationCompat
                .Builder(this, TRANSFERS_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.monfluo_notification_icon)
                .setOnlyAlertOnce(true)
                .setWhen(firstSeenAt)
                .setContentTitle(
                    getString(
                        R.string.transfer_notification_header,
                        TextHelper.getDisplayAmount(amount, MoneroUnit.MONERO, PreferenceUtils.isHideNotificationContent(this)),
                        getString(R.string.unit_xmr),
                    ),
                ).setContentText(
                    getString(
                        R.string.transfer_notification_description_transaction_id,
                        if (PreferenceUtils.isHideNotificationContent(this)) "#".repeat(txId.length) else txId,
                    ),
                ).setProgress(Constants.CONFIRMATIONS, progress, false)
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

    fun getWalletOrLog(): WalletWithData? {
        val wallet = this.walletRef.get()
        if (wallet != null) {
            return wallet
        } else {
            val e = IllegalStateException("Get wallet called on null wallet")
            Timber.e(e, "Get wallet called on null wallet (log only)")
            return null
        }
    }

    fun getPendingTransaction(): TransactionView? = pendingTransactionRef.get()

    // used by activities that [want to] OPEN wallets (but do not have one opened yet)
    fun openWallet(
        wallet: String,
        walletPassword: String,
    ) {
        closeWallet()
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.OPEN_WALLET.value()
        message.data.putString(Constants.EXTRA_WALLET_NAME, wallet)
        message.data.putString(Constants.EXTRA_WALLET_PASSWORD, walletPassword)
        handler.sendMessage(message)
    }

    fun closeWallet() {
        val walletWithData = walletRef.getAndSet(null)
        walletWithData?.wallet?.close()
    }

    private fun connectDaemon(node: Node?): DaemonState {
        if (node == null) {
            return DaemonState.NotSelected
        }

        return try {
            DaemonState.Connected(
                Monfluo.createDaemon(
                    node.name,
                    node.address,
                    node.credentials?.username,
                    node.credentials?.password,
                    PreferenceUtils.getProxyIfEnabled(this),
                ),
            )
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to create daemon for node ${node.name}")
            DaemonState.Failed(e)
        }
    }

    private fun applyDaemon(
        walletWithData: WalletWithData,
        node: Node?,
    ) {
        walletWithData.data.daemon = connectDaemon(node)
        walletWithData.data.lastSyncState = null
        walletWithData.data.lastTerminalSyncState = null
        walletWithData.data.lastNodeInfo = null
        walletWithData.data.syncCompleted = false
        updateSynchronizationStatusNotification(walletWithData)
    }

    fun reconnectDaemon() {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.RECONNECT_DAEMON.value()
        handler.sendMessage(message)
    }

    fun setPassword(newPassword: String) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SET_PASSWORD.value()
        message.data.putString(Constants.EXTRA_NEW_PASSWORD, newPassword)
        handler.sendMessage(message)
    }

    fun generateNewAddress(
        accountIndex: Int,
        label: String,
    ) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.GENERATE_NEW_ADDRESS.value()
        message.data.putInt(Constants.EXTRA_ACCOUNT_INDEX, accountIndex)
        handler.sendMessage(message)
    }

    fun setAddressLabel(
        accountIndex: Int,
        addressIndex: Int,
        label: String,
    ) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SET_ADDRESS_LABEL.value()
        message.data.putInt(Constants.EXTRA_ACCOUNT_INDEX, accountIndex)
        message.data.putInt(Constants.EXTRA_ADDRESS_INDEX, addressIndex)
        message.data.putString(Constants.EXTRA_ADDRESS_LABEL, label)
        handler.sendMessage(message)
    }

    fun sendTransaction(pendingTransaction: TransactionView) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SEND_TX.value()
        message.data.putString(Constants.EXTRA_TRANSACTION_ID, pendingTransaction.transactionHash.toString())
        handler.sendMessage(message)
    }

    fun createSweepTransaction(
        destination: String,
        priority: FeePriority,
        selectedEnotes: List<String>?,
        description: String?,
    ) {
        Timber.d("Sending message to create a sweep transaction")
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.CREATE_SWEEP_TX.value()
        message.data.putString(Constants.EXTRA_TRANSACTION_DESTINATION, destination)
        message.data.putInt(Constants.EXTRA_TRANSACTION_PRIORITY, priority.ordinal)
        selectedEnotes?.let {
            message.data.putStringArrayList(Constants.EXTRA_TRANSACTION_SELECTED_ENOTES, ArrayList(it))
        }
        description?.let {
            message.data.putString(Constants.EXTRA_TRANSACTION_DESCRIPTION, description)
        }
        handler.sendMessage(message)
    }

    fun createTransaction(
        destinations: List<TransactionDestinationWithAmount>,
        priority: FeePriority,
        selectedEnotes: List<String>?,
        description: String?,
    ) {
        Timber.d("Sending message to create a transaction")
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.CREATE_TX.value()
        message.data.putParcelableArrayList(Constants.EXTRA_TRANSACTION_DESTINATIONS, ArrayList(destinations))
        message.data.putInt(Constants.EXTRA_TRANSACTION_PRIORITY, priority.ordinal)
        selectedEnotes?.let {
            message.data.putStringArrayList(Constants.EXTRA_TRANSACTION_SELECTED_ENOTES, ArrayList(it))
        }
        description?.let {
            message.data.putString(Constants.EXTRA_TRANSACTION_DESCRIPTION, description)
        }
        handler.sendMessage(message)
    }

    fun freezeEnote(enotes: List<Long>) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.FREEZE_ENOTES.value()
        message.data.putLongArray(Constants.EXTRA_ENOTES, enotes.toLongArray())
        handler.sendMessage(message)
    }

    fun thawEnote(enotes: List<Long>) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.THAW_ENOTES.value()
        message.data.putLongArray(Constants.EXTRA_ENOTES, enotes.toLongArray())
        handler.sendMessage(message)
    }

    fun createAccount(label: String? = null) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.CREATE_ACCOUNT.value()
        message.data.putString(Constants.EXTRA_ACCOUNT_LABEL, label)
        handler.sendMessage(message)
    }

    fun setAccount(index: Int) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SET_ACCOUNT.value()
        message.data.putInt(Constants.EXTRA_ACCOUNT_INDEX, index)
        handler.sendMessage(message)
    }

    fun setAccountLabel(
        index: Int,
        label: String,
    ) {
        val message = handler.obtainMessage()
        message.arg1 = WalletServiceCommand.SET_ACCOUNT_LABEL.value()
        message.data.putInt(Constants.EXTRA_ACCOUNT_INDEX, index)
        message.data.putString(Constants.EXTRA_ACCOUNT_LABEL, label)
        handler.sendMessage(message)
    }
}

data class SyncNodeInfo(
    val timestamp: Long,
    val nodeInfo: NodeInfo,
)

sealed interface DaemonState {
    data class Connected(
        val daemon: Daemon,
    ) : DaemonState

    data class Failed(
        val error: Throwable,
    ) : DaemonState

    data object NotSelected : DaemonState
}

data class WalletSyncData(
    var daemon: DaemonState,
    var initialSyncHeight: Int,
    var latestHeight: Int? = null,
    var lastTerminalSyncState: WalletSyncState? = null,
    var lastSyncState: WalletSyncState? = null,
    var lastNodeInfo: SyncNodeInfo? = null,
    var syncCompleted: Boolean = false,
) {
    fun connectedDaemon(): Daemon? = (daemon as? DaemonState.Connected)?.daemon

    fun getAdjustedLastSyncState(): WalletSyncState? {
        val lastSyncState = this.lastSyncState ?: return null
        if (lastSyncState !is WalletSyncState.Preparing) return lastSyncState
        val lastTerminalSyncState = this.lastTerminalSyncState ?: return lastSyncState
        return if (lastTerminalSyncState is WalletSyncState.Finished || lastTerminalSyncState is WalletSyncState.Failed) lastTerminalSyncState else lastSyncState
    }
}

data class WalletWithData(
    val wallet: Wallet,
    val data: WalletSyncData,
    var walletView: WalletView,
)

interface WalletServiceObserver : LifecycleOwner {
    fun onWalletOpened(wallet: WalletWithData) {}

    fun onWalletUpdated(
        walletWithData: WalletWithData,
    ) {}

    fun onWalletPasswordSet() {}

    fun onAddressGenerated() {}

    fun onAddressLabelChanged(
        accountIndex: Int,
        addressIndex: Int,
        label: String,
    ) {}

    fun onTransactionCreated(transaction: TransactionView) {}

    fun onTransactionCreateFailed(error: Throwable) {}

    fun onTransactionSent(
        pendingTransaction: TransactionView,
        success: Boolean,
    ) {}


    fun onAccountCreated() {}

    fun onAccountSet(index: Int) {}

    fun onAccountLabelChanged(
        index: Int,
        label: String,
    ) {}
}

private enum class WalletServiceCommand {
    UNKNOWN, // to catch bugs when command is set to 0 by accident
    OPEN_WALLET,
    RECONNECT_DAEMON,
    SET_PASSWORD,
    SET_ADDRESS_LABEL,
    GENERATE_NEW_ADDRESS,
    CREATE_SWEEP_TX,
    CREATE_TX,
    SEND_TX,
    FREEZE_ENOTES,
    THAW_ENOTES,
    CREATE_ACCOUNT,
    SET_ACCOUNT,
    SET_ACCOUNT_LABEL,
    ;

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
