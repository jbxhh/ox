package org.monfluo.wallet.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import org.monfluo.wallet.adapter.AdapterWallet
import org.monfluo.wallet.fragment.DialogFragmentPassword
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.component.ActivityLogging
import timber.log.Timber
import kotlin.math.max

class ActivityWalletList : ActivityLogging() {
    private val startActivityToCreateWallet =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == 0) {
                Timber.i("Activity for creating wallet returned result code 0")
            } else {
                Timber.i("Activity for creating wallet returned non-zero result code, finishing current activity")
                setResult(1, result.data)
                finish()
            }
        }

    private lateinit var layoutView: View
    private lateinit var applicationSettingsImageView: ImageView
    private lateinit var walletRecyclerView: RecyclerView
    private lateinit var createWalletButton: Button

    private lateinit var adapter: AdapterWallet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_list)

        layoutView = findViewById(R.id.activity_wallet_list)
        applicationSettingsImageView = findViewById(R.id.application_settings_imageview)
        walletRecyclerView = findViewById(R.id.wallet_list_recyclerview)
        createWalletButton = findViewById(R.id.create_or_import_wallet)

        adapter =
            AdapterWallet(
                object : AdapterWallet.AdapterWalletListener {
                    override fun onWalletSelected(
                        self: AdapterWallet,
                        walletPath: String,
                    ) {
                        val passwordDialog =
                            DialogFragmentPassword(
                                walletPath,
                                false,
                                object : DialogFragmentPassword.Listener {
                                    override fun onCorrectPasswordSubmitted(
                                        self: DialogFragmentPassword,
                                        password: String,
                                    ) {
                                        Timber.i("Correct password submitted for wallet, finishing current activity")
                                        self.dismiss()
                                        setResult(
                                            1,
                                            Intent().apply {
                                                putExtra(Constants.EXTRA_WALLET_PATH, walletPath)
                                                putExtra(Constants.EXTRA_WALLET_PASSWORD, password)
                                            },
                                        )
                                        finish()
                                    }
                                },
                            )
                        passwordDialog.show(supportFragmentManager, "password_dialog")
                    }

                    override fun onWalletEdit(
                        self: AdapterWallet,
                        walletPath: String,
                    ) {
                        if (WalletService.RUNNING.get()) {
                            Toast.makeText(this@ActivityWalletList, getText(R.string.toast_wallet_not_closed), Toast.LENGTH_SHORT).show()
                            return
                        }
                        startActivity(
                            Intent(this@ActivityWalletList, ActivityWalletDetails::class.java).apply {
                                putExtra(Constants.EXTRA_WALLET_PATH, walletPath)
                            },
                        )
                    }
                },
            )
        walletRecyclerView.layoutManager = LinearLayoutManager(this@ActivityWalletList)
        walletRecyclerView.adapter = adapter

        bindWindowInsetsListener()
        bindListeners()
    }

    override fun onResume() {
        super.onResume()

        val walletPaths = findWallets()
        adapter.setWalletPaths(walletPaths)
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
        applicationSettingsImageView.setOnClickListener {
            startActivity(Intent(this@ActivityWalletList, ActivityApplicationSettings::class.java))
        }

        createWalletButton.setOnClickListener {
            val activityWalletCreateIntent = Intent(this@ActivityWalletList, ActivityWalletCreate::class.java)
            startActivityToCreateWallet.launch(activityWalletCreateIntent)
        }
    }

    private fun findWallets(): List<String> = Helper.findWallets(Helper.getWalletRoot(this@ActivityWalletList))
}
