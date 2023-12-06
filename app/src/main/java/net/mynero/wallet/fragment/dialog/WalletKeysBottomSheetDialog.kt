package net.mynero.wallet.fragment.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.mynero.wallet.R
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.Helper.clipBoardCopy

class WalletKeysBottomSheetDialog : BottomSheetDialogFragment() {
    @JvmField
    var password = ""
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.wallet_keys_bottom_sheet_dialog, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val copyViewKeyImageButton = view.findViewById<ImageButton>(R.id.copy_viewkey_imagebutton)
        val informationTextView = view.findViewById<TextView>(R.id.information_textview) // seed
        val viewKeyTextView = view.findViewById<TextView>(R.id.viewkey_textview)
        val restoreHeightTextView = view.findViewById<TextView>(R.id.restore_height_textview)
        val wallet = WalletManager.instance!!.wallet
        var seed = wallet!!.getSeed("")
        val usesOffset = PrefService.instance!!.getBoolean(Constants.PREF_USES_OFFSET, false)
        if (usesOffset) {
            seed = wallet.getSeed(password)
            view.findViewById<View>(R.id.wallet_seed_offset_textview).visibility = View.VISIBLE
        }
        val privateViewKey = wallet.getSecretViewKey()
        informationTextView.text = seed
        viewKeyTextView.text = privateViewKey
        restoreHeightTextView.text = "${wallet.getRestoreHeight()}"
        copyViewKeyImageButton.setOnClickListener {
            clipBoardCopy(
                context, "private view-key", privateViewKey
            )
        }
    }
}