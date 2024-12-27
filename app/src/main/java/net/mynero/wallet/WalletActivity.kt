package net.mynero.wallet

import WalletAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.fragment.dialog.PasswordBottomSheetDialog
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.util.Helper
import net.mynero.wallet.util.acitivity.WalletOpeningActivity

class WalletActivity : WalletOpeningActivity() {

    private lateinit var createWalletButton: Button
    private lateinit var walletRecyclerView: RecyclerView
    private lateinit var adapter: WalletAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        createWalletButton = findViewById(R.id.create_or_import_wallet)
        walletRecyclerView = findViewById(R.id.wallet_list_recyclerview)

        val walletPaths = WalletManager.instance.findWallets(Helper.getWalletRoot(this).absolutePath)
        adapter = WalletAdapter(walletPaths, object : WalletAdapter.AccountAdapterListener {
            override fun onWalletSelected(walletPath: String) {
                val passwordDialog = PasswordBottomSheetDialog(walletPath, object : PasswordBottomSheetDialog.Listener {
                    override fun onCorrectPasswordSubmitted(self: PasswordBottomSheetDialog, password: String) {
                        self.dismiss()
                        openWallet(walletPath, password)
                    }
                })
                passwordDialog.show(supportFragmentManager, "password_dialog")
            }
        })
        walletRecyclerView.layoutManager = LinearLayoutManager(this)
        walletRecyclerView.adapter = adapter

        createWalletButton.setOnClickListener {
            val onboardingActivityIntent = Intent(this, OnboardingActivity::class.java)
            onboardingActivityIntent.data = intent.data
            startActivity(onboardingActivityIntent)
        }
    }
}