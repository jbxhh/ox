package org.monfluo.wallet.util.acitivity

import android.os.Bundle
import androidx.fragment.app.Fragment
import org.monfluo.wallet.service.wallet.WalletService
import timber.log.Timber

abstract class MoneroFragment : Fragment(), MoneroComponent {

    override val componentName: String = "fragment"
    override var walletService: WalletService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.onMoneroComponentCreate(requireContext())
    }

    override fun onWalletServiceNotFound() {}

    override fun onWalletServiceDisconnected() {}
}