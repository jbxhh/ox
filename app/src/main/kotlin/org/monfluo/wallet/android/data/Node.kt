package org.monfluo.wallet.android.data

import org.monfluo.wallet.lib.data.Network
import java.util.UUID

data class Credentials(
    val username: String,
    val password: String,
)

data class Node(
    val id: String,
    val name: String,
    val address: String,
    val credentials: Credentials?,
    val network: Network,
)

enum class DefaultNode(
    val nodeName: String,
    val address: String,
) {
    MONERUJO("Monerujo", "http://nodex.monerujo.io:18081"),
    SETHFORPRIVACY("Seth For Privacy (HTTPS)", "https://node.sethforprivacy.com:443"),
    RUCKNIUM("Rucknium", "http://rucknium.me:18081"),
    STACKWALLET("Stack Wallet (HTTPS)", "https://monero.stackwallet.com:18081"),
    CAKEWALLET("Cake Wallet", "http://xmr-node.cakewallet.com:18081"),
    HASHVAULT("HashVault", "http://nodes.hashvault.pro:18081"),
    MONERUJO_ONION("Monerujo (Tor)", "http://monerujods7mbghwe6cobdr6ujih6c22zu5rl7zshmizz2udf7v7fsad.onion:18081"),
    RUCKNIUM_ONION("Rucknium (Tor)", "http://rucknium757bokwv3ss35ftgc3gzb7hgbvvglbg3hisp7tsj2fkd2nyd.onion:18081"),
    PLOWSOF_ONION1("plowsof #1 (Tor)", "http://plowsofe6cleftfmk2raiw5h2x66atrik3nja4bfd3zrfa2hdlgworad.onion:18089"),
    PLOWSOF_ONION2("plowsof #2 (Tor)", "http://plowsoffjexmxalw73tkjmf422gq6575fc7vicuu4javzn2ynnte6tyd.onion:18089"),
    BOLDSUCK("boldsuck (Tor)", "http://6dsdenp6vjkvqzy4wzsnzn6wixkdzihx3khiumyzieauxuxslmcaeiad.onion:18081"),
    ;

    companion object {
        fun defaultNodes(): List<Node> =
            entries.map {
                Node(
                    id = UUID.randomUUID().toString(),
                    name = it.nodeName,
                    address = it.address,
                    credentials = null,
                    network = Network.Mainnet,
                )
            }
    }
}
