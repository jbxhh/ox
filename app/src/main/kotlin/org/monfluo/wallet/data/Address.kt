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

import java.util.regex.Pattern

data class Address(
    val accountIndex: Int,
    val addressIndex: Int,
    val address: String,
    val label: String,
    val used: Boolean = false,
    val totalBalance: Long = 0L,
) : Comparable<Address> {
    override fun compareTo(other: Address): Int { // newer is <
        val compareAccountIndex = other.accountIndex - accountIndex
        return if (compareAccountIndex == 0) other.addressIndex - addressIndex else compareAccountIndex
    }
}
