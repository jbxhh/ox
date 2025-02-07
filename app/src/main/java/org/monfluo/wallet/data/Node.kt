/*
 * Copyright (c) 2018 m2049r
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
package org.monfluo.wallet.data

import org.monfluo.wallet.model.NetworkType
import org.json.JSONObject

data class Node(
    val name: String,
    val address: String,
    val username: String?,
    val password: String?,
    val networkType: NetworkType = NetworkType.NetworkType_Mainnet,
    val trusted: Boolean = false,
) {

    val isOnion: Boolean = false
//        get() = host.let { OnionHelper.isOnionHost(it) }
    val isI2P: Boolean = false
//        get() = host.let { OnionHelper.isI2PHost(it) }

    fun toJson(): Result<JSONObject> = runCatching {
        val jsonObject = JSONObject()
        jsonObject.put(NAME, name)
        jsonObject.put(ADDRESS, address)
        jsonObject.put(USERNAME, username)
        jsonObject.put(PASSWORD, password)
        jsonObject.put(NETWORK_TYPE, networkType.string)
        jsonObject.put(TRUSTED, trusted)
        return Result.success(jsonObject)
    }

    companion object {

        private const val NAME = "name"
        private const val ADDRESS = "address"
        private const val USERNAME = "username"
        private const val PASSWORD = "password"
        private const val NETWORK_TYPE = "networkType"
        private const val TRUSTED = "trusted"

        fun fromJson(jsonObject: JSONObject): Result<Node> = runCatching {
            val name = jsonObject.getString(NAME)
            val address = jsonObject.getString(ADDRESS)
            val username = if (jsonObject.has(USERNAME)) jsonObject.getString(USERNAME) else null
            val password = if (jsonObject.has(PASSWORD)) jsonObject.getString(PASSWORD) else null
            val networkType = NetworkType.fromString(jsonObject.getString(NETWORK_TYPE))!!
            val trusted = jsonObject.getBoolean(TRUSTED)
            return Result.success(Node(name, address, username, password, networkType, trusted))
        }
    }
}