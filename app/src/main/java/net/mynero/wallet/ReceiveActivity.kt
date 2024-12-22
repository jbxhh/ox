package net.mynero.wallet

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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
import net.mynero.wallet.service.wallet.WalletServiceObserver
import net.mynero.wallet.service.wallet.WalletService
import net.mynero.wallet.util.Helper.clipBoardCopy
import net.mynero.wallet.util.PreferenceUtils
import java.nio.charset.StandardCharsets
import java.util.EnumMap

class ReceiveActivity : AppCompatActivity(), WalletServiceObserver {

    private val viewModel: ReceiveViewModel by viewModels()

    private var walletService: WalletService? = null

    private lateinit var addressTextView: TextView
    private lateinit var addressLabelTextView: TextView
    private lateinit var addressImageView: ImageView
    private lateinit var copyAddressImageButton: ImageButton
    private lateinit var freshAddressImageView : ImageButton
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setVisible(false)
        setContentView(R.layout.activity_receive)

        addressImageView = findViewById(R.id.monero_qr_imageview)
        addressTextView = findViewById(R.id.address_textview)
        addressLabelTextView = findViewById(R.id.address_label_textview)
        copyAddressImageButton = findViewById(R.id.copy_address_imagebutton)
        freshAddressImageView = findViewById(R.id.fresh_address_imageview)
        recyclerView = findViewById(R.id.address_list_recyclerview)

        bindListeners()
        bindObservers()

        bindService(Intent(applicationContext, WalletService::class.java), connection, 0)
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val walletService = (service as WalletService.WalletServiceBinder).service
            this@ReceiveActivity.walletService = walletService
            walletService.addObserver(this@ReceiveActivity)
            viewModel.refreshSubaddresses(walletService)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            walletService = null
        }
    }

    private fun bindListeners() {
        freshAddressImageView.setOnClickListener {
            walletService?.generateNewSubaddress("")
        }
    }

    private fun bindObservers() {
        val subaddressAdapterListener = object : SubaddressAdapterListener {
            override fun onSubaddressSelected(subaddress: Subaddress) {
                viewModel.selectAddress(subaddress)
            }

            override fun onSubaddressEditLabel(subaddress: Subaddress) {
                editAddressLabel(subaddress)
            }
        }
        val streetMode = PreferenceUtils.isStreetMode(this)
        val adapter = SubaddressAdapter(emptyList(), null, streetMode, subaddressAdapterListener)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        viewModel.selectedSubaddress.observe(this) { address: Subaddress? ->
            if (address != null) {
                setAddress(address)
                adapter.submitSelectedAddress(address)
            }
        }
        viewModel.subaddresses.observe(this) { addresses: List<Subaddress> ->
            // We want newer addresses addresses to be shown first
            adapter.submitAddresses(addresses.reversed())
        }
    }

    override fun onSubaddressesUpdated() {
        walletService?.let { walletService ->
            viewModel.refreshSubaddresses(walletService)
        }
    }

    private fun editAddressLabel(subaddress: Subaddress) {
        val dialog = EditAddressLabelBottomSheetDialog(subaddress.label) {
            walletService?.setSubaddressLabel(subaddress.addressIndex, it)
        }
        dialog.show(supportFragmentManager, "edit_address_dialog")
    }

    private fun setAddress(subaddress: Subaddress) {
        val label = subaddress.displayLabel
        val address = getString(
            R.string.subbaddress_info_subtitle,
            subaddress.addressIndex, subaddress.squashedAddress
        )
        addressLabelTextView.text = label.ifEmpty { address }
        addressLabelTextView.visibility = View.VISIBLE
        addressTextView.text = subaddress.address
        addressTextView.visibility = View.VISIBLE
        // TODO: this takes a few hundred milliseconds, do something about it
        addressImageView.setImageBitmap(generate(subaddress.address, 256, 256))
        addressImageView.visibility = View.VISIBLE
        copyAddressImageButton.setOnClickListener {
            clipBoardCopy(
                this, "address", subaddress.address
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
    private val _selectedSubaddress = MutableLiveData<Subaddress?>()
    val selectedSubaddress: LiveData<Subaddress?> = _selectedSubaddress

    private val _subaddresses = MutableLiveData<List<Subaddress>>()
    val subaddresses: LiveData<List<Subaddress>> = _subaddresses

    fun refreshSubaddresses(walletService: WalletService) {
        val wallet = walletService.getWalletOrThrow()

        val newSubaddresses = (0 until wallet.numSubaddresses).map { wallet.getSubaddressObject(it) }
        _subaddresses.postValue(newSubaddresses)

        val currentSelectedSubaddress = selectedSubaddress.value
        if (currentSelectedSubaddress == null) {
            _selectedSubaddress.postValue(newSubaddresses.lastOrNull())
        } else {
            _selectedSubaddress.postValue(newSubaddresses.getOrNull(currentSelectedSubaddress.addressIndex))
        }
    }

    fun selectAddress(subaddress: Subaddress?) {
        _selectedSubaddress.value = subaddress
    }
}