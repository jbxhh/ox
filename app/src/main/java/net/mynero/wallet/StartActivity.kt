package net.mynero.wallet

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.UriData
import timber.log.Timber
import java.io.File

class StartActivity : AppCompatActivity() {

    private var uriData: UriData? = null

    private val startPasswordActivityForOpeningWallet = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val walletName = result.data?.extras?.getString(Constants.EXTRA_WALLET_NAME)
            val walletPassword = result.data?.extras?.getString(Constants.EXTRA_WALLET_PASSWORD)
            if (walletName != null && walletPassword != null) {
                openWallet(walletName, walletPassword)
            } else {
                // if we ever get here, it's a bug ¯\_(ツ)_/¯ so let's just recreate the activity
                Timber.e("Password activity returned null wallet name or password")
                recreate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.data?.let { uriData = UriData.parse(it.toString()) }
        // TODO: multiple wallets support
        val walletName = Constants.DEFAULT_WALLET_NAME
        val walletKeysFile = File(applicationInfo.dataDir, "$walletName.keys")
        if (walletKeysFile.exists()) {
            Timber.d("Wallet keys file exists, launching password activity")
            val intent = Intent(this, PasswordActivity::class.java)
            intent.putExtra(Constants.EXTRA_PREVENT_GOING_BACK, true)
            intent.putExtra(Constants.EXTRA_WALLET_NAME, walletName)
            startPasswordActivityForOpeningWallet.launch(intent)
            return
        } else {
            Timber.d("Wallet keys file does not exist, launching onboarding activity")
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
    }

    private fun openWallet(walletName: String, walletPassword: String) {
        val homeActivityIntent = Intent(this, HomeActivity::class.java)
        homeActivityIntent.putExtra(Constants.EXTRA_WALLET_NAME, walletName)
        homeActivityIntent.putExtra(Constants.EXTRA_WALLET_PASSWORD, walletPassword)
        if (uriData == null) {
            // the app was NOT started with a monero uri payment data, proceed to the home activity
            Timber.d("Uri payment data not present, launching home activity")
            startActivity(homeActivityIntent)
        } else {
            // the app was started with a monero uri payment data, we proceed to the send activity but launch the home activity as well
            // so that when users press back button they go to home activity instead of closing the app
            Timber.d("Uri payment data present, launching home and send activities")
            homeActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(homeActivityIntent)

            val sendIntent = Intent(this, SendActivity::class.java)
            sendIntent.putExtra(Constants.EXTRA_SEND_ADDRESS, uriData!!.address)
            uriData!!.amount?.let { sendIntent.putExtra(Constants.EXTRA_SEND_AMOUNT, it) }
            startActivity(sendIntent)
        }

        finish()
    }
}