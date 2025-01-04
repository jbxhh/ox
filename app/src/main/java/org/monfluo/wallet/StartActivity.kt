package org.monfluo.wallet

import android.content.Intent
import android.os.Bundle
import org.monfluo.wallet.fragment.dialog.PasswordBottomSheetDialog
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.acitivity.WalletOpeningActivity
import timber.log.Timber

class StartActivity : WalletOpeningActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setVisible(false)

        val isMultiWalletMode = PreferenceUtils.isMultiWalletMode(this)
        val walletPaths = org.monfluo.wallet.model.WalletManager.instance.findWallets(Helper.getWalletRoot(this).absolutePath)
        if (walletPaths.isEmpty()) {
            Timber.d("No wallets found, launching onboarding activity")
            val onboardingActivityIntent = Intent(this, OnboardingActivity::class.java)
            onboardingActivityIntent.data = intent.data
            onboardingActivityIntent.putExtra(Constants.EXTRA_PREVENT_GOING_BACK, true)
            startActivity(onboardingActivityIntent)
            finish()
        } else if (isMultiWalletMode) {
            Timber.d("${walletPaths.size} wallets found, launching wallet activity")
            val walletActivityIntent = Intent(this, WalletActivity::class.java)
            walletActivityIntent.data = intent.data
            startActivity(walletActivityIntent)
            finish()
        } else {
            Timber.d("${walletPaths.size} wallets found but multi-wallet mode is not enabled, asking for password to open a wallet")
            val walletPath = walletPaths.find { it.endsWith(Constants.DEFAULT_WALLET_NAME) } ?: walletPaths.first()
            val passwordDialog = PasswordBottomSheetDialog(walletPath, object : PasswordBottomSheetDialog.Listener {
                override fun onCorrectPasswordSubmitted(self: PasswordBottomSheetDialog, password: String) {
                    self.dismiss()
                    openWallet(walletPath, password)
                }
            })
            passwordDialog.show(supportFragmentManager, "password_dialog")
        }
    }
}
