package org.monfluo.wallet.fragment

import android.app.Dialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import org.monfluo.wallet.R

class DialogFragmentQrCode(
    private val bitmap: Bitmap,
    private val showIconOnQrCodes: Boolean,
    private val increaseBrightness: Boolean,
) : DialogFragment() {
    private lateinit var qrCodeImageView: ImageView
    private lateinit var qrCodeIconImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        if (increaseBrightness) {
            val window = dialog.window!!
            window.attributes =
                window.attributes.also {
                    it.screenBrightness = 1f
                }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.dialog_fragment_qr_code, container)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        qrCodeImageView = view.findViewById(R.id.qrcode_imageview)
        qrCodeIconImageView = view.findViewById(R.id.qrcode_icon_imageview)

        bindListeners()
        init()
    }

    private fun bindListeners() {
        qrCodeImageView.setOnClickListener {
            dismiss()
        }
        qrCodeIconImageView.setOnClickListener {
            dismiss()
        }
    }

    private fun init() {
        qrCodeImageView.setImageBitmap(bitmap)
        if (showIconOnQrCodes) {
            qrCodeIconImageView.visibility = View.VISIBLE
        } else {
            qrCodeIconImageView.visibility = View.GONE
        }
    }
}
