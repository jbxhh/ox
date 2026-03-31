package org.monfluo.wallet.android.util.component

import android.os.Bundle
import androidx.fragment.app.Fragment
import org.monfluo.wallet.android.service.wallet.WalletService

abstract class FragmentMonero :
    Fragment(),
    ComponentMonero {
    override val componentName: String = "fragment"
    override var walletService: WalletService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.onComponentMoneroCreate(requireContext())
    }

    override fun onWalletServiceNotFound() {}

    override fun onWalletServiceDisconnected() {}
}
