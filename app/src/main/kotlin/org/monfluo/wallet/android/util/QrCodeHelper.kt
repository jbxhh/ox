package org.monfluo.wallet.android.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.util.EnumMap

object QrCodeHelper {
    fun generateQrCode(
        text: String,
        width: Int,
        height: Int,
        backgroundColor: Int,
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val hints: MutableMap<EncodeHintType, Any?> = EnumMap(com.google.zxing.EncodeHintType::class.java)
        hints[EncodeHintType.CHARACTER_SET] = StandardCharsets.UTF_8
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M

        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitMatrix = barcodeEncoder.encode(text, BarcodeFormat.QR_CODE, width, height, hints)
            val pixels = IntArray(bitMatrix.width * bitMatrix.height)
            for (i in 0 until bitMatrix.height) {
                for (j in 0 until bitMatrix.width) {
                    if (bitMatrix[j, i]) {
                        pixels[i * width + j] = -0x1
                    } else {
                        pixels[i * height + j] = backgroundColor
                    }
                }
            }
            return Bitmap.createBitmap(pixels, 0, bitMatrix.width, bitMatrix.width, bitMatrix.height, Bitmap.Config.ARGB_8888)
        } catch (e: WriterException) {
            Timber.e(e, "Failed to generate QR code")
        }
        return null
    }
}
