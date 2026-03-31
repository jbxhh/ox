package org.monfluo.wallet.android.activity

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ncorti.slidetoact.SlideToActView
import com.ncorti.slidetoact.SlideToActView.OnSlideCompleteListener
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.adapter.AdapterTransactionPending
import org.monfluo.wallet.android.model.PendingTransaction
import org.monfluo.wallet.android.service.wallet.WalletService
import org.monfluo.wallet.android.util.component.ActivityMonero
import timber.log.Timber
import kotlin.math.max

class ActivityTransactionPending : ActivityMonero() {
    companion object {
        const val RESULT_OK: Int = 1
        const val RESULT_NO_PENDING_TRANSACTION: Int = 2
    }

    private lateinit var pendingTransaction: PendingTransaction

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var transactionDestinationsRecyclerView: RecyclerView
    private lateinit var sendTransactionSlider: SlideToActView

    private val adapterTransactionPending: AdapterTransactionPending = AdapterTransactionPending()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_pending)

        layoutView = findViewById(R.id.pending_transaction_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        transactionDestinationsRecyclerView = findViewById(R.id.transaction_destinations_recyclerview)
        sendTransactionSlider = findViewById(R.id.send_tx_slider)

        transactionDestinationsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionDestinationsRecyclerView.adapter = adapterTransactionPending

        bindWindowInsetsListener()
        bindListeners()
    }

    private fun bindWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(layoutView) { _, insetsCompat ->
            val insets = insetsCompat.getInsets(systemBars() or displayCutout())
            layoutView.updatePadding(
                left = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.left),
                top = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.top),
                right = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.right),
                bottom = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.bottom),
            )
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindListeners() {
        backImageView.setOnClickListener {
            finish()
        }

        sendTransactionSlider.onSlideCompleteListener =
            object : OnSlideCompleteListener {
                override fun onSlideComplete(view: SlideToActView) {
                    sendTransaction()
                }
            }

        ViewCompat.addAccessibilityAction(
            sendTransactionSlider,
            getString(R.string.approve_the_transaction),
        ) { _, _ ->
            sendTransaction()
            return@addAccessibilityAction true
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        val pendingTransaction = walletService.getPendingTransaction()
        if (pendingTransaction != null) {
            this.pendingTransaction = pendingTransaction
            adapterTransactionPending.setPendingTransaction(pendingTransaction)
        } else {
            Timber.e("Pending transaction activity spawned without a pending transaction")
            setResult(RESULT_NO_PENDING_TRANSACTION)
            finish()
        }
    }

    override fun onTransactionSent(
        pendingTransaction: PendingTransaction,
        success: Boolean,
    ) {
        runOnUiThread {
            if (success) {
                Timber.i("Pending transaction sent successfully, finishing activity")
                Toast.makeText(this, getString(R.string.sent_tx), Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Timber.i("Pending transaction was not sent")
                Toast.makeText(this, getString(R.string.toast_error_sending_tx), Toast.LENGTH_SHORT).show()
                sendTransactionSlider.setCompleted(completed = false, withAnimation = false)
            }
        }
    }

    private fun sendTransaction() {
        Toast.makeText(this, getString(R.string.sending_tx), Toast.LENGTH_SHORT).show()
        walletService?.sendTransaction(pendingTransaction)
    }
}
