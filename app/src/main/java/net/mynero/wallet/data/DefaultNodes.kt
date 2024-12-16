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
package net.mynero.wallet.data

import org.json.JSONException
import org.json.JSONObject

// Nodes stolen from https://moneroworld.com/#nodes
enum class DefaultNodes(
    val address: String,
    private val port: Int,
    private val network: String,
    private val nodeName: String
) {
    MONERUJO(
        "nodex.monerujo.io",
        18081,
        "mainnet",
        "monerujo"
    ),
    SETHFORPRIVACY(
        "node.sethforprivacy.com",
        18089,
        "mainnet",
        "sethforprivacy"
    ),
    RUCKNIUM(
        "rucknium.me",
        18081,
        "mainnet",
        "Rucknium"
    ),
    STACKWALLET(
        "monero.stackwallet.com",
        18081,
        "mainnet",
        "StackWallet"
    ),
    CAKEWALLET(
        "xmr-node.cakewallet.com",
        18081,
        "mainnet",
        "CakeWallet"
    ),
    HASHVAULT(
        "nodes.hashvault.pro",
        18081,
        "mainnet",
        "Hashvault"
    ),
    MONERUJO_ONION(
        "monerujods7mbghwe6cobdr6ujih6c22zu5rl7zshmizz2udf7v7fsad.onion",
        18081,
        "mainnet",
        "monerujo.onion"
    ),
    SETHFORPRIVACY_ONION(
        "sfprpc5klzs5vyitq2mrooicgk2wcs5ho2nm3niqduvzn5o6ylaslaqd.onion",
        18089,
        "mainnet",
        "sethforprivacy.onion"
    ),
    RUCKNIUM_ONION(
        "rucknium757bokwv3ss35ftgc3gzb7hgbvvglbg3hisp7tsj2fkd2nyd.onion",
        18081,
        "mainnet",
        "rucknium.onion"
    ),
    PLOWSOF_ONION1(
        "plowsofe6cleftfmk2raiw5h2x66atrik3nja4bfd3zrfa2hdlgworad.onion",
        18089,
        "mainnet",
        "plowsof.onion.1"
    ),
    PLOWSOF_ONION2(
        "plowsoffjexmxalw73tkjmf422gq6575fc7vicuu4javzn2ynnte6tyd.onion",
        18089,
        "mainnet",
        "plowsof.onion.2"
    ),
    Boldsuck(
        "6dsdenp6vjkvqzy4wzsnzn6wixkdzihx3khiumyzieauxuxslmcaeiad.onion",
        18081,
        "mainnet",
        "boldsuck.onion"
    );

    val nodeString: String
        get() = "$address:$port/$network/$nodeName"
    val json: JSONObject
        get() {
            val jsonObject = JSONObject()
            try {
                jsonObject.put("host", address)
                jsonObject.put("rpcPort", port)
                jsonObject.put("network", network)
                if (nodeName.isNotEmpty()) jsonObject.put("name", nodeName)
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
            return jsonObject
        }
}