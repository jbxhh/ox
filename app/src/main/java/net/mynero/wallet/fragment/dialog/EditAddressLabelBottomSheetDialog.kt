package net.mynero.wallet.fragment.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.mynero.wallet.R
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.AddressService
import net.mynero.wallet.util.Helper.getClipBoardText
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class EditAddressLabelBottomSheetDialog : BottomSheetDialogFragment() {
    @JvmField
    var listener: LabelListener? = null
    @JvmField
    var addressIndex = 0
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_dialog_edit_address_label, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val wallet = WalletManager.instance?.wallet
        val addressService = AddressService.instance
        val pasteButton = view.findViewById<ImageButton>(R.id.paste_password_imagebutton)
        val labelEditText = view.findViewById<EditText>(R.id.wallet_password_edittext)
        val saveLabelButton = view.findViewById<Button>(R.id.unlock_wallet_button)
        var isDate = false
        try {
            wallet?.getSubaddressLabel(
                addressIndex
            )?.let {
                SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.US).parse(
                    it
                )
                isDate = true
            }
        } catch (ignored: ParseException) {
        }
        labelEditText.setText(if (isDate) null else wallet?.getSubaddressLabel(addressIndex))
        pasteButton.setOnClickListener { labelEditText.setText(getClipBoardText(view.context)) }
        saveLabelButton.setOnClickListener {
            val label = labelEditText.text.toString()
            if (addressService?.latestAddressIndex == addressIndex) {
                addressService.freshSubaddress()
            }
            wallet?.setSubaddressLabel(addressIndex, label)
            wallet?.store()
            if (listener != null) {
                listener?.onDismiss()
            }
            dismiss()
        }
    }

    interface LabelListener {
        fun onDismiss()
    }
}