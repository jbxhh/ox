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
package org.monfluo.wallet.util

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.monfluo.wallet.R
import timber.log.Timber
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

object Helper {
    private const val XMR_DECIMALS = 12
    private const val WALLET_DIR = "wallets"
    private val REQUEST_CODE = AtomicInteger(1)
    var DISPLAY_DIGITS_INFO = 5

    fun getWalletRoot(context: Context): File {
        return getStorage(context, WALLET_DIR)
    }

    fun findWallets(file: File): List<String> {
        return file.listFiles()
            ?.filter { it.isFile && it.path.endsWith(".keys") }
            ?.map { it.path.removeSuffix(".keys") }
            ?: emptyList()
    }

    private fun getStorage(context: Context, folderName: String): File {
        val dir = File(context.filesDir, folderName)
        if (!dir.exists()) {
            Timber.i("Creating ${dir.absolutePath}")
            dir.mkdirs() // try to make it
        }
        if (!dir.isDirectory) {
            val message = "Directory " + dir.absolutePath + " does not exist."
            Timber.e(message)
            throw IllegalStateException(message)
        }
        return dir
    }

    fun getCameraPermission(context: Activity, launcher: ActivityResultLauncher<String?>): Boolean {
        return if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            Timber.w("Permission denied for CAMERA - requesting it")
            launcher.launch(Manifest.permission.CAMERA)
            false
        } else {
            true
        }
    }

    fun getDecimalAmount(amount: Long): BigDecimal {
        return BigDecimal(amount).scaleByPowerOfTen(-XMR_DECIMALS)
    }

    fun getDisplayAmount(amount: Long): String {
        return getDisplayAmount(amount, XMR_DECIMALS)
    }

    fun getDisplayAmount(amount: Long, maxDecimals: Int): String {
        // a Java bug does not strip zeros properly if the value is 0
        if (amount == 0L) return "0.00"
        var d = getDecimalAmount(amount)
            .setScale(maxDecimals, BigDecimal.ROUND_HALF_UP)
            .stripTrailingZeros()
        if (d.scale() < 2) d = d.setScale(2, BigDecimal.ROUND_UNNECESSARY)
        return d.toPlainString()
    }

    fun clipBoardCopy(context: Context?, label: String?, text: String?) {
        if (context != null) {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(
                context,
                context.getText(R.string.copied_to_clipboard),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun getClipBoardText(context: Context): String? {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val primaryClip = clipboardManager.primaryClip
        return primaryClip?.let {
            val description = it.description
            // some apps for some reason store clipboard text as html
            if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
                // getItemAt may throw IndexOutOfBoundsException and there is no safe version of it
                kotlin.runCatching { it.getItemAt(0) }.getOrNull()?.text?.toString()
            } else {
                null
            }
        }
    }

    fun requestPostNotificationsPermission(activity: Activity, force: Boolean = false) {
        if (!force && PreferenceUtils.isPostNotificationsPermissionRequested(activity)) {
            Timber.d("POST_NOTIFICATIONS permission has already been requested")
            return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            Timber.d("POST_NOTIFICATIONS permission has already been granted")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Timber.d("POST_NOTIFICATIONS permission does not need to be requested on this SDK version")
            return
        }
        Timber.i("Requesting POST_NOTIFICATIONS permission")
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE.getAndIncrement())
        PreferenceUtils.setPostNotificationsPermissionRequested(activity)
    }
}