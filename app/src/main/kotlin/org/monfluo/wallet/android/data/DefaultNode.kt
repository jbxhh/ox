/*
 * Copyright (c) 2020 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.monfluo.wallet.android.data

enum class DefaultNode(
    val value: Node,
) {
    MONERUJO(Node("Monerujo", "nodex.monerujo.io:18081", null, null)),
    SETHFORPRIVACY(Node("Seth For Privacy (HTTPS)", "https://node.sethforprivacy.com:443", null, null)),
    RUCKNIUM(Node("Rucknium", "rucknium.me:18081", null, null)),
    STACKWALLET(Node("Stack Wallet (HTTPS)", "https://monero.stackwallet.com:18081", null, null)),
    CAKEWALLET(Node("Cake Wallet", "xmr-node.cakewallet.com:18081", null, null)),
    HASHVAULT(Node("HashVault", "nodes.hashvault.pro:18081", null, null)),
    MONERUJO_ONION(Node("Monerujo (Tor)", "monerujods7mbghwe6cobdr6ujih6c22zu5rl7zshmizz2udf7v7fsad.onion:18081", null, null)),
    SETHFORPRIVACY_ONION(Node("Seth For Privacy (Tor)", "sfprpc5klzs5vyitq2mrooicgk2wcs5ho2nm3niqduvzn5o6ylaslaqd.onion:18089", null, null)),
    RUCKNIUM_ONION(Node("Rucknium (Tor)", "rucknium757bokwv3ss35ftgc3gzb7hgbvvglbg3hisp7tsj2fkd2nyd.onion:18081", null, null)),
    PLOWSOF_ONION1(Node("plowsof #1 (Tor)", "plowsofe6cleftfmk2raiw5h2x66atrik3nja4bfd3zrfa2hdlgworad.onion:18089", null, null)),
    PLOWSOF_ONION2(Node("plowsof #2 (Tor)", "plowsoffjexmxalw73tkjmf422gq6575fc7vicuu4javzn2ynnte6tyd.onion:18089", null, null)),
    BOLDSUCK(Node("boldsuck (Tor)", "6dsdenp6vjkvqzy4wzsnzn6wixkdzihx3khiumyzieauxuxslmcaeiad.onion:18081", null, null)),
    ;

    companion object {
        fun defaultNodes(): List<Node> = entries.map { it.value }

        fun defaultNode(): Node = MONERUJO.value
    }
}
