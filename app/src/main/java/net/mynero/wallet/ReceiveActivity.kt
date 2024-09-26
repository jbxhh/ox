package net.mynero.wallet

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import net.mynero.wallet.adapter.SubaddressAdapter
import net.mynero.wallet.adapter.SubaddressAdapter.SubaddressAdapterListener
import net.mynero.wallet.data.Subaddress
import net.mynero.wallet.fragment.dialog.EditAddressLabelBottomSheetDialog
import net.mynero.wallet.fragment.dialog.EditAddressLabelBottomSheetDialog.LabelListener
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.AddressService
import net.mynero.wallet.util.Helper.clipBoardCopy
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.EnumMap

class ReceiveActivity : AppCompatActivity() {

    private lateinit var mViewModel: ReceiveViewModel
    private lateinit var addressTextView: TextView
    private lateinit var addressLabelTextView: TextView
    private lateinit var addressImageView: ImageView
    private lateinit var copyAddressImageButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive)

        mViewModel = ViewModelProvider(this)[ReceiveViewModel::class.java]
        addressImageView = findViewById(R.id.monero_qr_imageview)
        addressTextView = findViewById(R.id.address_textview)
        addressLabelTextView = findViewById(R.id.address_label_textview)
        copyAddressImageButton = findViewById(R.id.copy_address_imagebutton)
        bindListeners()
        bindObservers()
        mViewModel.init()
    }

    private fun bindListeners() {
        val freshAddressImageView = findViewById<ImageButton>(R.id.fresh_address_imageview)
        freshAddressImageView.setOnClickListener { mViewModel.freshSubaddress }
    }

    private fun bindObservers() {
        val subaddressAdapterListener = object : SubaddressAdapterListener {
            override fun onSubaddressSelected(subaddress: Subaddress?) {
                mViewModel.selectAddress(subaddress)
            }

            override fun onSubaddressEditLabel(subaddress: Subaddress?) {
                editAddressLabel(subaddress)
            }
        }
        val adapter = SubaddressAdapter(emptyList(), null, subaddressAdapterListener)
        val recyclerView = findViewById<RecyclerView>(R.id.address_list_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        mViewModel.address.observe(this) { address: Subaddress? ->
            setAddress(address)
            adapter.submitSelectedAddress(address)
        }
        mViewModel.addresses.observe(this) { addresses: List<Subaddress> ->
            // We want newer addresses addresses to be shown first
            adapter.submitAddresses(addresses.reversed())
        }
    }

    private fun editAddressLabel(subaddress: Subaddress?) {
        val dialog = EditAddressLabelBottomSheetDialog()
        dialog.addressIndex = subaddress?.addressIndex ?: return
        dialog.listener = object : LabelListener {
            override fun onDismiss() {
                mViewModel.init()
            }
        }
        dialog.show(supportFragmentManager, "edit_address_dialog")
    }

    private fun setAddress(subaddress: Subaddress?) {
        val label = subaddress?.displayLabel
        val address = getString(
            R.string.subbaddress_info_subtitle,
            subaddress?.addressIndex, subaddress?.squashedAddress
        )
        addressLabelTextView.text = if (label?.isEmpty() == true) address else label
        addressTextView.text = subaddress?.address
        addressImageView.setImageBitmap(subaddress?.address?.let { generate(it, 256, 256) })
        copyAddressImageButton.setOnClickListener {
            clipBoardCopy(
                this, "address", subaddress?.address
            )
        }
        addressLabelTextView.setOnLongClickListener {
            editAddressLabel(subaddress)
            true
        }
        addressTextView.setOnLongClickListener {
            editAddressLabel(subaddress)
            true
        }
    }

    private fun generate(text: String, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val hints: MutableMap<EncodeHintType, Any?> =
            EnumMap(com.google.zxing.EncodeHintType::class.java)
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
                        pixels[i * height + j] = ContextCompat.getColor(this, R.color.oled_colorBackground)
                    }
                }
            }
            return Bitmap.createBitmap(pixels, 0, bitMatrix.width, bitMatrix.width, bitMatrix.height, Bitmap.Config.ARGB_8888)
        } catch (ex: WriterException) {
            Log.e("ReceiveFragment.kt", ex.toString())
        }
        return null
    }
}

class ReceiveViewModel : ViewModel() {
    private val _address = MutableLiveData<Subaddress?>()
    private val _addresses = MutableLiveData<List<Subaddress>>()
    val address: LiveData<Subaddress?> = _address
    val addresses: LiveData<List<Subaddress>> = _addresses

    fun init() {
        _addresses.value = subaddresses
        _address.value = addresses.value?.lastOrNull()
    }

    private val subaddresses: List<Subaddress>
        get() {
            val wallet = WalletManager.instance?.wallet
            val subaddresses = ArrayList<Subaddress>()
            val numAddresses = AddressService.instance?.numAddresses ?: 1
            for (i in 0 until numAddresses) {
                wallet?.getSubaddressObject(i)?.let { subaddresses.add(it) }
            }
            return Collections.unmodifiableList(subaddresses)
        }

    val freshSubaddress: Unit
        get() {
            _address.value = AddressService.instance?.freshSubaddress()
            _addresses.value = subaddresses
        }

    fun selectAddress(subaddress: Subaddress?) {
        _address.value = subaddress
    }
}