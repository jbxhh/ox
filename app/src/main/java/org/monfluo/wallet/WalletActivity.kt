package org.monfluo.wallet

import org.monfluo.wallet.adapter.WalletAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.fragment.dialog.EditWalletBottomSheetDialog
import org.monfluo.wallet.fragment.dialog.PasswordBottomSheetDialog
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.acitivity.LoggingActivity
import timber.log.Timber
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.moveTo
import kotlin.io.path.name

class WalletActivity : LoggingActivity() {

    private val startOnboardingActivityToOpenWallet = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == 0) {
            Timber.i("Onboarding activity returned result code 0")
        } else {
            Timber.i("Onboarding activity returned non-zero result code, finishing current activity")
            setResult(1, result.data)
            finish()
        }
    }

    private lateinit var createWalletButton: Button
    private lateinit var walletRecyclerView: RecyclerView
    private lateinit var adapter: WalletAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        createWalletButton = findViewById(R.id.create_or_import_wallet)
        walletRecyclerView = findViewById(R.id.wallet_list_recyclerview)

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
                    Toast.makeText(this@WalletActivity, getText(R.string.toast_wallet_not_closed), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@WalletActivity, getText(R.string.toast_wallet_deleted), Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(this@WalletActivity, getText(R.string.toast_wallet_renamed), Toast.LENGTH_SHORT).show()
                        }
                        self.dismiss()
                    }
                })
                editWalletDialog.show(supportFragmentManager, "edit_wallet_dialog")
            }
        })
        walletRecyclerView.layoutManager = LinearLayoutManager(this)
        walletRecyclerView.adapter = adapter

        createWalletButton.setOnClickListener {
            val onboardingActivityIntent = Intent(this, OnboardingActivity::class.java)
            startOnboardingActivityToOpenWallet.launch(onboardingActivityIntent)
        }
    }

    private fun findWallets(): List<String> {
        return Helper.findWallets(Helper.getWalletRoot(this))
    }
}