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

    private var preventGoingBack: Boolean = false
    private lateinit var walletName: String

    private lateinit var passwordEditText: EditText
    private lateinit var unlockButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)

        preventGoingBack = intent.getBooleanExtra(Constants.EXTRA_PREVENT_GOING_BACK, false)
        walletName = intent.getStringExtra(Constants.EXTRA_WALLET_NAME)!!

        passwordEditText = findViewById(R.id.wallet_password_edittext)
        unlockButton = findViewById(R.id.unlock_wallet_button)

        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!preventGoingBack) {
                    finish()
                }
            }
        })

        unlockButton.setOnClickListener {
            onUnlockClicked(passwordEditText.text.toString())
        }
    }

    private fun onUnlockClicked(walletPassword: String) {
        if (checkPassword(walletPassword)) {
            val intent = Intent()
            intent.putExtra(Constants.EXTRA_WALLET_NAME, walletName)
            intent.putExtra(Constants.EXTRA_WALLET_PASSWORD, walletPassword)
            setResult(RESULT_OK, intent)
            finish()
        } else {
            Toast.makeText(application, R.string.bad_password, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPassword(walletPassword: String): Boolean {
        val walletFile = File(applicationInfo.dataDir, walletName)

        return WalletManager.instance.verifyWalletPasswordOnly(
            walletFile.absolutePath + ".keys",
            walletPassword
        )
    }
}