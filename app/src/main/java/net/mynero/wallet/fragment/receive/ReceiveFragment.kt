package net.mynero.wallet.fragment.receive

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import net.mynero.wallet.R
import net.mynero.wallet.adapter.SubaddressAdapter
import net.mynero.wallet.adapter.SubaddressAdapter.SubaddressAdapterListener
import net.mynero.wallet.data.Subaddress
import net.mynero.wallet.fragment.dialog.EditAddressLabelBottomSheetDialog
import net.mynero.wallet.fragment.dialog.EditAddressLabelBottomSheetDialog.LabelListener
import net.mynero.wallet.util.Helper.clipBoardCopy
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.util.EnumMap

class ReceiveFragment : Fragment() {
    private var addressTextView: TextView? = null
    private var addressLabelTextView: TextView? = null
    private var addressImageView: ImageView? = null
    private var copyAddressImageButton: ImageButton? = null
    private var mViewModel: ReceiveViewModel? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_receive, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel = ViewModelProvider(this)[ReceiveViewModel::class.java]
        addressImageView = view.findViewById(R.id.monero_qr_imageview)
        addressTextView = view.findViewById(R.id.address_textview)
        addressLabelTextView = view.findViewById(R.id.address_label_textview)
        copyAddressImageButton = view.findViewById(R.id.copy_address_imagebutton)
        bindListeners(view)
        bindObservers(view)
        mViewModel?.init()
    }

    private fun bindListeners(view: View) {
        val freshAddressImageView = view.findViewById<ImageButton>(R.id.fresh_address_imageview)
        freshAddressImageView.setOnClickListener { mViewModel?.freshSubaddress }
    }

    private fun bindObservers(view: View) {
        val adapter = SubaddressAdapter(object : SubaddressAdapterListener {
            override fun onSubaddressSelected(subaddress: Subaddress?) {
                mViewModel?.selectAddress(subaddress)
            }

            override fun onSubaddressEditLabel(subaddress: Subaddress?) {
                editAddressLabel(subaddress)
            }
        })
        val recyclerView = view.findViewById<RecyclerView>(R.id.address_list_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter
        mViewModel?.address?.observe(viewLifecycleOwner) { subaddress: Subaddress? ->
            setAddress(
                subaddress
            )
        }
        mViewModel?.addresses?.observe(viewLifecycleOwner) { dataSet: List<Subaddress> ->
            adapter.submitList(
                dataSet
            )
        }
    }

    private fun editAddressLabel(subaddress: Subaddress?) {
        val dialog = EditAddressLabelBottomSheetDialog()
        dialog.addressIndex = subaddress?.addressIndex ?: return
        dialog.listener = object : LabelListener {
            override fun onDismiss() {
                mViewModel?.init()
            }
        }
        dialog.show(parentFragmentManager, "edit_address_dialog")
    }

    private fun setAddress(subaddress: Subaddress?) {
        val label = subaddress?.displayLabel
        val address = context?.getString(
            R.string.subbaddress_info_subtitle,
            subaddress?.addressIndex, subaddress?.squashedAddress
        )
        addressLabelTextView?.text = if (label?.isEmpty() == true) address else label
        addressTextView?.text = subaddress?.address
        addressImageView?.setImageBitmap(subaddress?.address?.let { generate(it, 256, 256) })
        copyAddressImageButton?.setOnClickListener {
            clipBoardCopy(
                context, "address", subaddress?.address
            )
        }
        addressLabelTextView?.setOnLongClickListener {
            editAddressLabel(subaddress)
            true
        }
        addressTextView?.setOnLongClickListener {
            editAddressLabel(subaddress)
            true
        }
    }

    private fun generate(text: String, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val hints: MutableMap<EncodeHintType, Any?> = EnumMap(com.google.zxing.EncodeHintType::class.java)
        hints[EncodeHintType.CHARACTER_SET] = StandardCharsets.UTF_8
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        try {
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints)
            val pixels = IntArray(width * height)
            for (i in 0 until height) {
                for (j in 0 until width) {
                    if (bitMatrix[j, i]) {
                        pixels[i * width + j] = -0x1
                    } else {
                        pixels[i * height + j] = resources.getColor(R.color.oled_colorBackground)
                    }
                }
            }
            return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.RGB_565)
        } catch (ex: WriterException) {
            Timber.e(ex)
        }
        return null
    }
}