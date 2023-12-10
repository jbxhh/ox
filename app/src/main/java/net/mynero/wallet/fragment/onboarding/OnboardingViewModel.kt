package net.mynero.wallet.fragment.onboarding

import android.app.Activity
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.mynero.wallet.MainActivity
import net.mynero.wallet.MoneroApplication
import net.mynero.wallet.R
import net.mynero.wallet.livedata.combineLiveDatas
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.RestoreHeight
import java.io.File
import java.util.Calendar

class OnboardingViewModel : ViewModel() {
    private val _showMoreOptions = MutableLiveData(false)
    private val _creatingWallet = MutableLiveData(false)
    private val _seedType = MutableLiveData(SeedType.POLYSEED)
    private val _useProxy = MutableLiveData(false)
    val useProxy: LiveData<Boolean> = _useProxy
    private val _proxyAddress = MutableLiveData("")
    private val _proxyPort = MutableLiveData("")
    private val _useBundledTor = MutableLiveData(false)
    val useBundledTor: LiveData<Boolean> = _useBundledTor
    private val _passphrase = MutableLiveData("")
    val passphrase: LiveData<String> = _passphrase
    private val _confirmedPassphrase = MutableLiveData("")
    private val _showMonerochan = MutableLiveData(true)
    val showMonerochan: LiveData<Boolean> = _showMonerochan
    var showMoreOptions: LiveData<Boolean> = _showMoreOptions
    var seedType: LiveData<SeedType> = _seedType

    init {
        _useProxy.value = ProxyService.instance?.usingProxy
        _useBundledTor.value = ProxyService.instance?.useBundledTor
    }

    val enableButton = combineLiveDatas(
        seedType,
        _useProxy,
        _proxyAddress,
        _proxyPort,
        _useBundledTor,
        _passphrase,
        _confirmedPassphrase,
        _creatingWallet,
        ProxyService.instance?.samouraiTorManager?.getTorStateLiveData()
    ) { seedType, useProxy, proxyAddress, proxyPort, useBundledTor, passphrase, confirmedPassphrase, creatingWallet, torState ->
        if (seedType == null || useProxy == null || proxyAddress == null || proxyPort == null || useBundledTor == null || passphrase == null || confirmedPassphrase == null || creatingWallet == null) return@combineLiveDatas false
        if ((passphrase.isNotEmpty() || confirmedPassphrase.isNotEmpty()) && passphrase != confirmedPassphrase) return@combineLiveDatas false
        if (creatingWallet) return@combineLiveDatas false
        if (seedType == SeedType.POLYSEED && (passphrase.isEmpty() || confirmedPassphrase.isEmpty())) return@combineLiveDatas false
        if (useProxy && (proxyAddress.isEmpty() || proxyPort.isEmpty()) && !useBundledTor) return@combineLiveDatas false
        val progress = torState?.progressIndicator ?: 0
        if (useBundledTor && progress < 100 && useProxy) return@combineLiveDatas false

        return@combineLiveDatas true
    }

    fun onMoreOptionsClicked() {
        val currentValue = showMoreOptions.value ?: false
        val newValue = !currentValue
        _showMoreOptions.value = newValue
    }

    fun setSeedType(seedType: SeedType?) {
        _seedType.value = seedType
    }

    fun createOrImportWallet(
        mainActivity: Activity,
        walletSeed: String,
        restoreHeightText: String,
        useOffset: Boolean
    ) {
        val passphrase = _passphrase.value ?: return
        val confirmedPassphrase = _confirmedPassphrase.value ?: return

        val application = mainActivity.application as MoneroApplication
        _creatingWallet.postValue(true)
        val offset = if (useOffset) confirmedPassphrase else ""
        if (passphrase.isNotEmpty()) {
            if (passphrase != confirmedPassphrase) {
                _creatingWallet.postValue(false)
                mainActivity.runOnUiThread {
                    Toast.makeText(
                        mainActivity,
                        application.getString(R.string.invalid_confirmed_password),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
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
        val seedTypeValue = seedType.value ?: return
        if (walletSeed.isEmpty()) {
            if (seedTypeValue == SeedType.POLYSEED) {
                wallet = if (offset.isEmpty()) {
                    mainActivity.runOnUiThread {
                        _creatingWallet.postValue(false)
                        Toast.makeText(
                            mainActivity,
                            application.getString(R.string.invalid_empty_passphrase),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return
                } else {
                    WalletManager.instance?.createWalletPolyseed(
                        walletFile,
                        passphrase,
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
                        passphrase,
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
                    _creatingWallet.postValue(false)
                    Toast.makeText(
                        mainActivity,
                        application.getString(R.string.invalid_mnemonic_code),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
            if (restoreHeightText.isNotEmpty()) {
                restoreHeight = restoreHeightText.toLong()
            }
            if (seedTypeValue == SeedType.POLYSEED) {
                wallet = WalletManager.instance?.recoveryWalletPolyseed(
                    walletFile,
                    passphrase,
                    walletSeed,
                    offset
                )
            } else if (seedTypeValue == SeedType.LEGACY) {
                wallet = WalletManager.instance?.recoveryWallet(
                    walletFile,
                    passphrase,
                    walletSeed,
                    offset,
                    restoreHeight
                )
            }
        }
        val walletStatus = wallet?.status
        wallet?.close()
        val ok = walletStatus?.isOk
        walletFile.delete() // cache is broken for some reason when recovering wallets. delete the file here. this happens in monerujo too.
        if (ok == true) {
            (mainActivity as MainActivity).init(walletFile, passphrase)
            mainActivity.runOnUiThread { mainActivity.onBackPressed() }
        } else {
            mainActivity.runOnUiThread {
                _creatingWallet.postValue(false)
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

    fun setProxyAddress(address: String) {
        _proxyAddress.value = address
        if (address.isEmpty()) PrefService.instance?.deleteProxy()
        val port = _proxyPort.value ?: return
        ProxyService.instance?.updateProxy(address, port)
    }

    fun setProxyPort(port: String) {
        _proxyPort.value = port
        if (port.isEmpty()) PrefService.instance?.deleteProxy()
        val address = _proxyAddress.value ?: return
        ProxyService.instance?.updateProxy(address, port)
    }

    fun setUseBundledTor(useBundled: Boolean) {
        _useBundledTor.value = useBundled
        ProxyService.instance?.useBundledTor = useBundled

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        if (useBundled && ProxyService.instance?.usingProxy == true) {
            samouraiTorManager?.start()
        } else {
            samouraiTorManager?.stop()
        }
    }

    fun setUseProxy(useProxy: Boolean) {
        _useProxy.value = useProxy
        ProxyService.instance?.usingProxy = useProxy

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        if (useProxy && ProxyService.instance?.useBundledTor == true) {
            samouraiTorManager?.start()
        } else {
            samouraiTorManager?.stop()
        }
    }

    fun setPassphrase(passphrase: String) {
        _passphrase.value = passphrase
    }

    fun setConfirmedPassphrase(confirmedPassphrase: String) {
        _confirmedPassphrase.value = confirmedPassphrase
    }

    fun setMonerochan(b: Boolean) {
        _showMonerochan.value = b
        PrefService.instance?.edit()?.putBoolean(Constants.PREF_MONEROCHAN, b)?.apply()
    }

    enum class SeedType(val descResId: Int) {
        LEGACY(R.string.seed_desc_legacy), POLYSEED(R.string.seed_desc_polyseed), UNKNOWN(0)

    }
}