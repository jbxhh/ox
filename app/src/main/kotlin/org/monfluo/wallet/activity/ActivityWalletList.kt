package org.monfluo.wallet.activity

import org.monfluo.wallet.adapter.WalletAdapter
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
import org.monfluo.wallet.fragment.dialog.EditWalletBottomSheetDialog
import org.monfluo.wallet.fragment.dialog.PasswordBottomSheetDialog
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.acitivity.LoggingActivity
import org.monfluo.wallet.R
import timber.log.Timber
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.math.max

class ActivityWalletList : LoggingActivity() {

    private val startActivityToCreateWallet = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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

    private lateinit var adapter: WalletAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_list)

        layoutView = findViewById(R.id.activity_wallet_list)
        applicationSettingsImageView = findViewById(R.id.application_settings_imageview)
        walletRecyclerView = findViewById(R.id.wallet_list_recyclerview)
        createWalletButton = findViewById(R.id.create_or_import_wallet)

        val walletPaths = findWallets()
        adapter = WalletAdapter(walletPaths, object : WalletAdapter.WalletAdapterListener {
            override fun onWalletSelected(self: WalletAdapter, walletPath: String) {
                val passwordDialog = PasswordBottomSheetDialog(walletPath, false, object : PasswordBottomSheetDialog.Listener {
                    override fun onCorrectPasswordSubmitted(self: PasswordBottomSheetDialog, password: String) {
                        Timber.i("Correct password submitted for wallet, finishing current activity")
                        self.dismiss()
                        setResult(1, Intent().apply {
                            putExtra(Constants.EXTRA_WALLET_PATH, walletPath)
                            putExtra(Constants.EXTRA_WALLET_PASSWORD, password)
                        })
                        finish()
                    }
                })
                passwordDialog.show(supportFragmentManager, "password_dialog")
            }

            override fun onWalletEdit(self: WalletAdapter, walletPath: String) {
                if (WalletService.RUNNING.get()) {
                    Toast.makeText(this@ActivityWalletList, getText(R.string.toast_wallet_not_closed), Toast.LENGTH_SHORT).show()
                    return
                }
                val walletAdapter = self
                val editWalletDialog = EditWalletBottomSheetDialog(Path(walletPath).name, object : EditWalletBottomSheetDialog.EditWalletListener {
                    override fun onDeleteWallet(
                        self: EditWalletBottomSheetDialog,
                    ) {
                        Path(walletPath).deleteIfExists()
                        Path("$walletPath.keys").deleteIfExists()
                        walletAdapter.setWalletPaths(findWallets())
                        Toast.makeText(this@ActivityWalletList, getText(R.string.toast_wallet_deleted), Toast.LENGTH_SHORT).show()
                        self.dismiss()
                    }

                    override fun onDone(
                        self: EditWalletBottomSheetDialog,
                        newWalletName: String
                    ) {
                        if (Path(walletPath).name != newWalletName) {
                            Path(walletPath).moveTo(Path(walletPath).resolveSibling(newWalletName))
                            Path("$walletPath.keys").moveTo(Path(walletPath).resolveSibling("$newWalletName.keys"))
                            walletAdapter.setWalletPaths(findWallets())
                            Toast.makeText(this@ActivityWalletList, getText(R.string.toast_wallet_renamed), Toast.LENGTH_SHORT).show()
                        }
                        self.dismiss()
                    }
                })
                editWalletDialog.show(supportFragmentManager, "edit_wallet_dialog")
            }
        })
        walletRecyclerView.layoutManager = LinearLayoutManager(this@ActivityWalletList)
        walletRecyclerView.adapter = adapter

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
        applicationSettingsImageView.setOnClickListener {
            startActivity(Intent(this@ActivityWalletList, ActivitySettingsApplication::class.java))
        }

        createWalletButton.setOnClickListener {
            val activityWalletCreateIntent = Intent(this@ActivityWalletList, ActivityWalletCreate::class.java)
            startActivityToCreateWallet.launch(activityWalletCreateIntent)
        }
    }

    private fun findWallets(): List<String> {
        return Helper.findWallets(Helper.getWalletRoot(this@ActivityWalletList))
    }
}