package org.monfluo.wallet.lib

object Monfluo {
    external fun monfluoVersion(): String

    external fun listWallets(monfluoDirectory: String): List<String>
}
