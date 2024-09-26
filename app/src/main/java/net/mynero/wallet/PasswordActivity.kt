package net.mynero.wallet

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.util.Constants
import java.io.File

// Shows a password prompt
// Finishes and returns the wallet's name and password in extra when user enters valid password
class PasswordActivity : AppCompatActivity() {

    private var preventGoingBack = false
    private lateinit var passwordEdittext: EditText
    private lateinit var unlockButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)

        preventGoingBack = intent.getBooleanExtra(Constants.EXTRA_PREVENT_GOING_BACK, false)

        passwordEdittext = findViewById(R.id.wallet_password_edittext)
        unlockButton = findViewById(R.id.unlock_wallet_button)

        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!preventGoingBack) {
                    finish()
                }
            }
        })

        val walletFile = File(applicationInfo.dataDir, Constants.WALLET_NAME)

        unlockButton.setOnClickListener {
            onUnlockClicked(walletFile, passwordEdittext.text.toString())
        }
    }

    private fun onUnlockClicked(walletFile: File, password: String) {
        if (checkPassword(walletFile, password)) {
            val intent = Intent()
            intent.putExtra(Constants.EXTRA_WALLET_NAME, walletFile.name)
            intent.putExtra(Constants.EXTRA_WALLET_PASSWORD, password)
            setResult(RESULT_OK, intent)
            finish()
        } else {
            Toast.makeText(application, R.string.bad_password, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPassword(walletFile: File, password: String): Boolean {
        return WalletManager.instance?.verifyWalletPasswordOnly(
            walletFile.absolutePath + ".keys",
            password
        ) == true
    }
}