/*
 * Copyright (c) 2017 m2049r
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

import java.util.regex.Pattern

class UserNotes(txNotes: String?) {
    var txNotes: String? = ""

    @JvmField
    var note: String? = ""

    @JvmField
    var xmrtoTag: String? = null

    @JvmField
    var xmrtoKey: String? = null

    @JvmField
    var xmrtoAmount: String? = null // could be a double - but we are not doing any calculations
    var xmrtoCurrency: String? = null

    @JvmField
    var xmrtoDestination: String? = null

    init {
        this.txNotes = txNotes
        val p = Pattern.compile("^\\{([a-z]+)-(\\w{6,}),([0-9.]*)([A-Z]+),(\\w*)\\} ?(.*)")
        val m = p.matcher(txNotes)
        if (m.find()) {
            xmrtoTag = m.group(1)
            xmrtoKey = m.group(2)
            xmrtoAmount = m.group(3)
            xmrtoCurrency = m.group(4)
            xmrtoDestination = m.group(5)
            note = m.group(6)
        } else {
            note = txNotes
        }
    }

    fun setNote(newNote: String?) {
        note = newNote ?: ""
        txNotes = buildTxNote()
    }

    private fun buildTxNote(): String {
        val sb = StringBuilder()
        if (xmrtoKey != null) {
            require(!(xmrtoAmount == null || xmrtoDestination == null)) { "Broken notes" }
            sb.append("{")
            sb.append(xmrtoTag)
            sb.append("-")
            sb.append(xmrtoKey)
            sb.append(",")
            sb.append(xmrtoAmount)
            sb.append(xmrtoCurrency)
            sb.append(",")
            sb.append(xmrtoDestination)
            sb.append("}")
            if (note != null && note?.isNotEmpty() == true) sb.append(" ")
        }
        sb.append(note)
        return sb.toString()
    }
}