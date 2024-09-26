package net.mynero.wallet

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import net.mynero.wallet.service.MoneroHandlerThread
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.UriData
import java.io.File

class StartActivity : AppCompatActivity() {

    private var uriData: UriData? = null

    private val startPasswordActivityForOpeningWallet = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val walletName = result.data?.extras?.getString(Constants.EXTRA_WALLET_NAME)
            val walletPassword = result.data?.extras?.getString(Constants.EXTRA_WALLET_PASSWORD)
            if (walletName != null && walletPassword != null) {
                val walletFile = File(applicationInfo.dataDir, walletName)
                openWallet(walletFile, walletPassword)
            } else {
                // if we ever get here, it's a bug ¯\_(ツ)_/¯ so let's just recreate the activity
                recreate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.data?.let { uriData = UriData.parse(it.toString()) }
        val walletFile = File(applicationInfo.dataDir, Constants.WALLET_NAME)
        val walletKeysFile = File(applicationInfo.dataDir, Constants.WALLET_NAME + ".keys")
        if (walletKeysFile.exists()) {
            val usesPassword = PrefService.instance?.getBoolean(Constants.PREF_USES_PASSWORD, false) == true
            if (!usesPassword) {
                openWallet(walletFile, "")
                return
            } else {
                val intent = Intent(this, PasswordActivity::class.java)
                intent.putExtra(Constants.EXTRA_PREVENT_GOING_BACK, true)
                startPasswordActivityForOpeningWallet.launch(intent)
                return
            }
        } else {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
    }

    private fun openWallet(walletFile: File, password: String) {
        MoneroHandlerThread.init(walletFile, password, applicationContext)

        if (uriData == null) {
            // the app was NOT started with a monero uri payment data, proceed to the home activity
            startActivity(Intent(this, HomeActivity::class.java))
        } else {
            // the app was started with a monero uri payment data, we proceed to the send activity but launch the home activity as well
            // so that when users press back button they go to home activity instead of closing the app
            val homeIntent = Intent(this, HomeActivity::class.java)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(homeIntent)

            val sendIntent = Intent(this, SendActivity::class.java)
            sendIntent.putExtra(Constants.EXTRA_SEND_ADDRESS, uriData!!.address)
            uriData!!.amount?.let { sendIntent.putExtra(Constants.EXTRA_SEND_AMOUNT, it) }
            startActivity(sendIntent)
        }

        finish()
    }
}