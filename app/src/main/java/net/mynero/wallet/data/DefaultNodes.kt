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
    SAMOURAI("163.172.56.213", 18089, "mainnet", "SamouraiWallet"), MONERUJO(
        "nodex.monerujo.io",
        18081,
        "mainnet",
        "monerujo"
    ),
    SUPPORTXMR(
        "node.supportxmr.com",
        18081,
        "mainnet",
        "SupportXMR"
    ),
    HASHVAULT(
        "nodes.hashvault.pro",
        18081,
        "mainnet",
        "Hashvault"
    ),
    MONEROWORLD(
        "node.moneroworld.com",
        18089,
        "mainnet",
        "MoneroWorld"
    ),
    XMRTW(
        "opennode.xmr-tw.org",
        18089,
        "mainnet",
        "XMRTW"
    ),
    MYNERO_I2P(
        "ynk3hrwte23asonojqeskoulek2g2cd6tqg4neghnenfyljrvhga.b32.i2p",
        0,
        "mainnet",
        "node.mysu.i2p"
    ),
    MYNERO_ONION(
        "tiopyrxseconw73thwlv2pf5hebfcqxj5zdolym7z6pbq6gl4z7xz4ad.onion",
        18081,
        "mainnet",
        "node.mysu.onion"
    ),
    SAMOURAI_ONION(
        "446unwib5vc7pfbzflosy6m6vtyuhddnalr3hutyavwe4esfuu5g6ryd.onion",
        18089,
        "mainnet",
        "SamouraiWallet.onion"
    ),
    MONERUJO_ONION(
        "monerujods7mbghwe6cobdr6ujih6c22zu5rl7zshmizz2udf7v7fsad.onion",
        18081,
        "mainnet",
        "monerujo.onion"
    ),
    Criminales78(
        "56wl7y2ebhamkkiza4b7il4mrzwtyvpdym7bm2bkg3jrei2je646k3qd.onion",
        18089,
        "mainnet",
        "Criminales78.onion"
    ),
    Xmrfail(
        "mxcd4577fldb3ppzy7obmmhnu3tf57gbcbd4qhwr2kxyjj2qi3dnbfqd.onion",
        18081,
        "mainnet",
        "xmrfail.onion"
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