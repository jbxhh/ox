package net.mynero.wallet.fragment.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import net.mynero.wallet.R;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.model.WalletManager;
import net.mynero.wallet.service.AddressService;
import net.mynero.wallet.util.Helper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class EditAddressLabelBottomSheetDialog extends BottomSheetDialogFragment {
    public LabelListener listener = null;
    public int addressIndex = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_dialog_edit_address_label, null);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Wallet wallet = WalletManager.getInstance().getWallet();
        AddressService addressService = AddressService.instance;
        ImageButton pasteButton = view.findViewById(R.id.paste_password_imagebutton);
        EditText labelEditText = view.findViewById(R.id.wallet_password_edittext);
        Button saveLabelButton = view.findViewById(R.id.unlock_wallet_button);

        boolean isDate = false;
        try {
            new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.US).parse(wallet.getSubaddressLabel(addressIndex));
            isDate = true;
        } catch (ParseException ignored) {
        }

        labelEditText.setText(isDate ? null : wallet.getSubaddressLabel(addressIndex));
        pasteButton.setOnClickListener(view1 -> labelEditText.setText(Helper.getClipBoardText(view.getContext())));
        saveLabelButton.setOnClickListener(view1 -> {
            String label = labelEditText.getText().toString();
            if (addressService.getLatestAddressIndex() == addressIndex) {
                addressService.freshSubaddress();
            }
            wallet.setSubaddressLabel(addressIndex, label);
            wallet.store();
            if (listener != null) {
                listener.onDismiss();
            }
            dismiss();
        });
    }

    public interface LabelListener {
        void onDismiss();
    }
}