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
package net.mynero.wallet.util

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.view.animation.Animation
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import net.mynero.wallet.R
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

object Helper {
    const val NOCRAZYPASS_FLAGFILE = ".nocrazypass"
    const val XMR_DECIMALS = 12
    val ONE_XMR = Math.round(Math.pow(10.0, XMR_DECIMALS.toDouble()))
    const val SHOW_EXCHANGERATES = true
    const val PERMISSIONS_REQUEST_CAMERA = 7
    const val HTTP_TIMEOUT = 5000
    private const val WALLET_DIR = "wallets"
    private const val MONERO_DIR = "monero"
    private val HexArray = "0123456789ABCDEF".toCharArray()
    var ALLOW_SHIFT = false
    var DISPLAY_DIGITS_INFO = 5
    private var ShakeAnimation: Animation? = null
    fun getWalletRoot(context: Context): File {
        return getStorage(context, WALLET_DIR)
    }

    fun getStorage(context: Context, folderName: String?): File {
        val dir = File(context.filesDir, folderName)
        if (!dir.exists()) {
            Log.i("Helper.kt", "Creating ${dir.absolutePath}")
            dir.mkdirs() // try to make it
        }
        if (!dir.isDirectory) {
            val msg = "Directory " + dir.absolutePath + " does not exist."
            Log.e("Helper.kt",msg)
            throw IllegalStateException(msg)
        }
        return dir
    }

    fun getCameraPermission(context: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED
            ) {
                Log.w("Helper.kt", "Permission denied for CAMERA - requesting it")
                val permissions = arrayOf(Manifest.permission.CAMERA)
                context.requestPermissions(
                    permissions,
                    PERMISSIONS_REQUEST_CAMERA
                )
                false
            } else {
                true
            }
        } else {
            true
        }
    }

    fun getCameraPermission(context: Activity, launcher: ActivityResultLauncher<String?>): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED
            ) {
                Log.w("Helper.kt", "Permission denied for CAMERA - requesting it")
                launcher.launch(Manifest.permission.CAMERA)
                false
            } else {
                true
            }
        } else {
            true
        }
    }

    fun getWalletFile(context: Context, aWalletName: String?): File {
        val walletDir = getWalletRoot(context)
        val f = File(walletDir, aWalletName)
        Log.d("Helper.kt", "wallet=${f.absolutePath} size= ${f.length()}")
        return f
    }

    fun showKeyboard(act: Activity) {
        val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focus = act.currentFocus
        if (focus != null) imm.showSoftInput(focus, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard(act: Activity?) {
        if (act == null) return
        if (act.currentFocus == null) {
            act.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        } else {
            val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            act.currentFocus?.let {
                imm.hideSoftInputFromWindow(
                    it.windowToken,
                    InputMethodManager.HIDE_NOT_ALWAYS
                )
            }
        }
    }

    fun showKeyboard(dialog: Dialog) {
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    fun hideKeyboardAlways(act: Activity) {
        act.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
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

    fun getFormattedAmount(amount: Double, isCrypto: Boolean): String? {
        // at this point selection is XMR in case of error
        val displayB: String? = if (isCrypto) {
            if (amount >= 0 || amount == 0.0) {
                String.format(Locale.US, "%,.5f", amount)
            } else {
                null
            }
        } else { // not crypto
            String.format(Locale.US, "%,.2f", amount)
        }
        return displayB
    }

    fun getDisplayAmount(amount: Double): String {
        // a Java bug does not strip zeros properly if the value is 0
        var d = BigDecimal(amount)
            .setScale(XMR_DECIMALS, BigDecimal.ROUND_HALF_UP)
            .stripTrailingZeros()
        if (d.scale() < 1) d = d.setScale(1, BigDecimal.ROUND_UNNECESSARY)
        return d.toPlainString()
    }

    fun getBitmap(context: Context, drawableId: Int): Bitmap {
        return when (val drawable = ContextCompat.getDrawable(context, drawableId)) {
            is BitmapDrawable -> BitmapFactory.decodeResource(context.resources, drawableId)
            is VectorDrawable -> getBitmap(drawable)
            else -> throw IllegalArgumentException("unsupported drawable type")
        }
    }

    private fun getBitmap(vectorDrawable: VectorDrawable): Bitmap {
        val bitmap = Bitmap.createBitmap(
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)
        return bitmap
    }

    fun getUrl(httpsUrl: String?): String? {
        var urlConnection: HttpsURLConnection? = null
        try {
            val url = URL(httpsUrl)
            urlConnection = url.openConnection() as HttpsURLConnection
            urlConnection.connectTimeout = HTTP_TIMEOUT
            urlConnection.readTimeout = HTTP_TIMEOUT
            val `in` = InputStreamReader(urlConnection.inputStream)
            val sb = StringBuffer()
            val BUFFER_SIZE = 512
            val buffer = CharArray(BUFFER_SIZE)
            var length = `in`.read(buffer, 0, BUFFER_SIZE)
            while (length >= 0) {
                sb.append(buffer, 0, length)
                length = `in`.read(buffer, 0, BUFFER_SIZE)
            }
            return sb.toString()
        } catch (ex: SocketTimeoutException) {
            Log.w("Helper.kt", "C ${ex.localizedMessage}")
        } catch (ex: MalformedURLException) {
            Log.e("Helper.kt","A ${ex.localizedMessage}")
        } catch (ex: IOException) {
            Log.e("Helper.kt","B ${ex.localizedMessage}")
        } finally {
            urlConnection?.disconnect()
        }
        return null
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
}