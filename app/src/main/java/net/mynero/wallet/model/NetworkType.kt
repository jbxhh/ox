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
package net.mynero.wallet.model

enum class NetworkType(val value: Int) {
    NetworkType_Mainnet(0), NetworkType_Testnet(1), NetworkType_Stagenet(2);

    companion object {
        fun fromInteger(n: Int): NetworkType? {
            when (n) {
                0 -> return NetworkType_Mainnet
                1 -> return NetworkType_Testnet
                2 -> return NetworkType_Stagenet
            }
            return null
        }
    }
}