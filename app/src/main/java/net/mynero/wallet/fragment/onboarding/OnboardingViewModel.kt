package net.mynero.wallet.fragment.onboarding

import android.app.Activity
import android.util.Patterns
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.mynero.wallet.MainActivity
import net.mynero.wallet.MoneroApplication
import net.mynero.wallet.R
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.RestoreHeight
import java.io.File
import java.util.Calendar

class OnboardingViewModel : ViewModel() {
    private val _showMoreOptions = MutableLiveData(false)
    private val _enableCreateButton = MutableLiveData(true)
    private val _seedType = MutableLiveData(SeedType.POLYSEED)
    var showMoreOptions: LiveData<Boolean> = _showMoreOptions
    var enableCreateButton: LiveData<Boolean> = _enableCreateButton
    var seedType: LiveData<SeedType> = _seedType
    private var proxyAddress = ""
    private var proxyPort = ""
    fun onMoreOptionsClicked() {
        val currentValue = showMoreOptions.value ?: false
        val newValue = !currentValue
        _showMoreOptions.value = newValue
    }

    fun updateProxy(application: MoneroApplication) {
        application.executor?.execute {
            val usesProxy = PrefService.instance?.getBoolean(Constants.PREF_USES_TOR, false) == true
            if (!usesProxy) {
                return@execute
            }
            if (proxyAddress.isEmpty()) proxyAddress = "127.0.0.1"
            if (proxyPort.isEmpty()) proxyPort = "9050"
            val validIpAddress = Patterns.IP_ADDRESS.matcher(proxyAddress).matches()
            if (validIpAddress) {
                val proxy = "$proxyAddress:$proxyPort"
                PrefService.instance?.edit()?.putString(Constants.PREF_PROXY, proxy)?.apply()
            }
        }
    }

    fun setSeedType(seedType: SeedType?) {
        _seedType.value = seedType
    }

    fun setProxyAddress(address: String) {
        proxyAddress = address
    }

    fun setProxyPort(port: String) {
        proxyPort = port
    }

    fun createOrImportWallet(
        mainActivity: Activity,
        walletPassword: String,
        confirmedPassword: String,
        walletSeed: String,
        restoreHeightText: String,
        useOffset: Boolean
    ) {
        val application = mainActivity.application as MoneroApplication
        application.executor?.execute {
            _enableCreateButton.postValue(false)
            val offset = if (useOffset) walletPassword else ""
            if (walletPassword.isNotEmpty()) {
                if (walletPassword != confirmedPassword) {
                    _enableCreateButton.postValue(true)
                    mainActivity.runOnUiThread {
                        Toast.makeText(
                            mainActivity,
                            application.getString(R.string.invalid_confirmed_password),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@execute
                }
                PrefService.instance?.edit()?.putBoolean(Constants.PREF_USES_PASSWORD, true)
                    ?.apply()
            }
            var restoreHeight = newRestoreHeight
            val walletFile = File(mainActivity.applicationInfo.dataDir, Constants.WALLET_NAME)
            var wallet: Wallet? = null
            if (offset.isNotEmpty()) {
                PrefService.instance?.edit()?.putBoolean(Constants.PREF_USES_OFFSET, true)?.apply()
            }
            if (walletSeed.isEmpty()) {
                val seedTypeValue = seedType.value ?: return@execute
                if (seedTypeValue == SeedType.POLYSEED) {
                    wallet = if (offset.isEmpty()) {
                        mainActivity.runOnUiThread {
                            _enableCreateButton.postValue(true)
                            Toast.makeText(
                                mainActivity,
                                application.getString(R.string.invalid_empty_passphrase),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@execute
                    } else {
                        WalletManager.instance?.createWalletPolyseed(
                            walletFile,
                            walletPassword,
                            offset,
                            Constants.MNEMONIC_LANGUAGE
                        )
                    }
                } else if (seedTypeValue == SeedType.LEGACY) {
                    val tmpWalletFile =
                        File(mainActivity.applicationInfo.dataDir, Constants.WALLET_NAME + "_tmp")
                    val tmpWallet =
                        createTempWallet(tmpWalletFile) //we do this to get seed, then recover wallet so we can use seed offset
                    tmpWallet?.let {
                        wallet = WalletManager.instance?.recoveryWallet(
                            walletFile,
                            walletPassword,
                            tmpWallet.getSeed("") ?: return@let,
                            offset,
                            restoreHeight
                        )
                        tmpWalletFile.delete()
                    }
                }
            } else {
                if (getMnemonicType(walletSeed) == SeedType.UNKNOWN) {
                    mainActivity.runOnUiThread {
                        _enableCreateButton.postValue(true)
                        Toast.makeText(
                            mainActivity,
                            application.getString(R.string.invalid_mnemonic_code),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@execute
                }
                if (restoreHeightText.isNotEmpty()) {
                    restoreHeight = restoreHeightText.toLong()
                }
                wallet = WalletManager.instance?.recoveryWallet(
                    walletFile,
                    walletPassword,
                    walletSeed,
                    offset,
                    restoreHeight
                )
            }
            val walletStatus = wallet?.status
            wallet?.close()
            val ok = walletStatus?.isOk
            walletFile.delete() // cache is broken for some reason when recovering wallets. delete the file here. this happens in monerujo too.
            if (ok == true) {
                (mainActivity as MainActivity).init(walletFile, walletPassword)
                mainActivity.runOnUiThread { mainActivity.onBackPressed() }
            } else {
                mainActivity.runOnUiThread {
                    _enableCreateButton.postValue(true)
                    Toast.makeText(
                        mainActivity,
                        application.getString(
                            R.string.create_wallet_failed,
                            walletStatus?.errorString
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private val newRestoreHeight: Long
        get() {
            val restoreDate = Calendar.getInstance()
            restoreDate.add(Calendar.DAY_OF_MONTH, 0)
            return RestoreHeight.instance?.getHeight(restoreDate.time) ?: 0
        }

    private fun getMnemonicType(seed: String): SeedType {
        val words = seed.split("\\s".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val seedTypeValue = seedType.value ?: return SeedType.LEGACY
        return if (words.size == 16 && seedTypeValue == SeedType.POLYSEED) {
            SeedType.POLYSEED
        } else if (words.size == 25 && seedTypeValue == SeedType.LEGACY) {
            SeedType.LEGACY
        } else {
            SeedType.UNKNOWN
        }
    }

    private fun createTempWallet(tmpWalletFile: File): Wallet? {
        return WalletManager.instance?.createWallet(
            tmpWalletFile,
            "",
            Constants.MNEMONIC_LANGUAGE,
            0
        )
    }

    enum class SeedType(val descResId: Int) {
        LEGACY(R.string.seed_desc_legacy), POLYSEED(R.string.seed_desc_polyseed), UNKNOWN(0)

    }
}