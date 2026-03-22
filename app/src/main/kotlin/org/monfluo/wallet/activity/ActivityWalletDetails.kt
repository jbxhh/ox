package org.monfluo.wallet.activity

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.R
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.component.ActivityLogging
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.math.max

class ActivityWalletDetails : ActivityLogging() {
    private val viewModel: ActivityWalletDetailsViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var walletNameEditText: EditText
    private lateinit var deleteButton: Button
    private lateinit var doneButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_details)

        layoutView = findViewById(R.id.activity_wallet_details_layout)
        backImageView = findViewById(R.id.back_imageview)
        walletNameEditText = findViewById(R.id.wallet_name_edittext)
        doneButton = findViewById(R.id.done_editing_button)
        deleteButton = findViewById(R.id.delete_node_button)

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
        init()
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
        doneButton.setOnClickListener {
            val walletPath = viewModel.walletPath.value!!
            val newWalletName = walletNameEditText.text.toString()
            if (Path(walletPath).name != newWalletName) {
                Path(walletPath).moveTo(Path(walletPath).resolveSibling(newWalletName))
                Path("$walletPath.keys").moveTo(Path(walletPath).resolveSibling("$newWalletName.keys"))
                PreferenceUtils.renameWalletNetworkType(this@ActivityWalletDetails, Path(walletPath).name, newWalletName)
            }
            finish()
        }
        deleteButton.setOnClickListener {
            val walletPath = viewModel.walletPath.value!!

            AlertDialog
                .Builder(this)
                .setTitle(getString(R.string.dialog_delete_wallet_title))
                .setMessage(getString(R.string.dialog_delete_wallet_message))
                .setPositiveButton(getString(R.string.dialog_delete_wallet_action_yes)) { _, _ ->
                    Path(walletPath).deleteIfExists()
                    Path("$walletPath.keys").deleteIfExists()
                    PreferenceUtils.deleteWalletNetworkType(this@ActivityWalletDetails, Path(walletPath).name)
                    finish()
                }.setNegativeButton(getString(R.string.dialog_delete_wallet_action_no), null)
                .create()
                .show()
        }
    }

    private fun bindObservers() {
        viewModel.walletPath.observe(this) { walletPath ->
            if (walletPath != null && walletNameEditText.text.isEmpty()) {
                walletNameEditText.setText(Path(walletPath).name)
            }
        }
    }

    private fun init() {
        val walletPath = intent.getStringExtra(Constants.EXTRA_WALLET_PATH)
        if (viewModel.walletPath.value == null) {
            viewModel.walletPath.value = walletPath
        }
    }
}

internal class ActivityWalletDetailsViewModel : ViewModel() {
    val walletPath: MutableLiveData<String?> = MutableLiveData(null)
}
