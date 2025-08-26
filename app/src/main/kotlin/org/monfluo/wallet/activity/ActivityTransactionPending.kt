package org.monfluo.wallet.activity

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import com.ncorti.slidetoact.SlideToActView
import com.ncorti.slidetoact.SlideToActView.OnSlideCompleteListener
import org.monfluo.wallet.model.PendingTransaction
import org.monfluo.wallet.model.MoneroUnit
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.acitivity.MoneroActivity
import org.monfluo.wallet.util.TextHelper
import org.monfluo.wallet.R
import timber.log.Timber
import kotlin.math.max

class ActivityTransactionPending : MoneroActivity() {

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var addressTextView: TextView
    private lateinit var addressResolvedFromLabelTextView: TextView
    private lateinit var addressResolvedFromTextView: TextView
    private lateinit var amountTextView: TextView
    private lateinit var feeTextView: TextView
    private lateinit var totalTextView: TextView

    private lateinit var sendTransactionSlider: SlideToActView

    private lateinit var pendingTransaction: PendingTransaction

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_pending)

        layoutView = findViewById(R.id.pending_transaction_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        addressTextView = findViewById(R.id.address_textview)
        addressResolvedFromLabelTextView = findViewById(R.id.address_resolved_from_label_textview)
        addressResolvedFromTextView = findViewById(R.id.address_resolved_from_textview)
        amountTextView = findViewById(R.id.amount_textview)
        feeTextView = findViewById(R.id.fee_textview)
        sendTransactionSlider = findViewById(R.id.send_tx_slider)
        totalTextView = findViewById(R.id.total_textview)

        bindWindowInsetsListener()
        bindListeners()
    }

    private fun bindWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(layoutView) { _, insetsCompat ->
            val insets = insetsCompat.getInsets(systemBars() or displayCutout())
            layoutView.updatePadding(
                left = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.left),
                top = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.top),
                right = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.right),
                bottom = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.bottom)
            )
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindListeners() {
        backImageView.setOnClickListener {
            finish()
        }

        sendTransactionSlider.onSlideCompleteListener = object : OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                sendTransaction()
            }
        }

        sendTransactionSlider.let { slideToActView ->
            ViewCompat.addAccessibilityAction(
                slideToActView,
                getString(R.string.approve_the_transaction)
            ) { _, _ ->
                sendTransaction()
                return@addAccessibilityAction true
            }
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        val pendingTransaction = walletService.getPendingTransaction()
        if (pendingTransaction != null) {
            this.pendingTransaction = pendingTransaction
            amountTextView.setText(TextHelper.formatUnlockedBalance(this, pendingTransaction.getAmount(), MoneroUnit.MONERO, false, 0))
            feeTextView.setText(TextHelper.formatUnlockedBalance(this, pendingTransaction.getFee(), MoneroUnit.MONERO, false, 0))
            totalTextView.setText(TextHelper.formatUnlockedBalance(this, pendingTransaction.getAmount() + pendingTransaction.getFee(), MoneroUnit.MONERO, false, 0))
            val resolvedDestinations = pendingTransaction.destination.getResolvedDestinations()
            if (resolvedDestinations.size > 1) {
                addressTextView.setText(getString(R.string.multiple_addresses))
                addressResolvedFromLabelTextView.visibility = View.GONE
                addressResolvedFromTextView.visibility = View.GONE
            } else {
                val resolvedDestination = resolvedDestinations.get(0)
                addressTextView.setText(resolvedDestination.address)
                val resolvedFrom = resolvedDestination.resolvedFrom
                if (resolvedFrom != null) {
                    addressResolvedFromTextView.setText(resolvedFrom.value)
                addressResolvedFromLabelTextView.visibility = View.VISIBLE
                    addressResolvedFromTextView.visibility = View.VISIBLE
                } else {
                addressResolvedFromLabelTextView.visibility = View.GONE
                    addressResolvedFromTextView.visibility = View.GONE
                }
            }
        } else {
            Timber.e("Pending transaction activity spawned without a pending transaction")
            setResult(1)
            finish()
        }
    }

    override fun onTransactionSent(pendingTransaction: PendingTransaction, success: Boolean) {
        runOnUiThread {
            if (success) {
                Timber.i("Pending transaction sent successfully, finishing activity")
                Toast.makeText(this, getString(R.string.sent_tx), Toast.LENGTH_SHORT).show()
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