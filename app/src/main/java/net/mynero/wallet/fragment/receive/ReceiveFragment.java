package net.mynero.wallet.fragment.receive;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import net.mynero.wallet.R;
import net.mynero.wallet.data.Subaddress;
import net.mynero.wallet.util.DayNightMode;
import net.mynero.wallet.util.Helper;
import net.mynero.wallet.util.NightmodeHelper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

public class ReceiveFragment extends Fragment {
    private TextView addressTextView = null;
    private ImageView addressImageView = null;
    private ImageButton copyAddressImageButton = null;
    private ReceiveViewModel mViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_receive, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ReceiveViewModel.class);
        addressImageView = view.findViewById(R.id.monero_qr_imageview);
        addressTextView = view.findViewById(R.id.address_textview);
        copyAddressImageButton = view.findViewById(R.id.copy_address_imagebutton);
        mViewModel.init();
        bindListeners(view);
        bindObservers(view);
    }

    private void bindListeners(View view) {
        ImageView freshAddressImageView = view.findViewById(R.id.fresh_address_imageview);
        freshAddressImageView.setOnClickListener(view1 -> {
            mViewModel.getFreshSubaddress();
        });
    }

    private void bindObservers(View view) {
        mViewModel.address.observe(getViewLifecycleOwner(), this::setAddress);
    }

    private void setAddress(Subaddress subaddress) {
        addressTextView.setText(subaddress.getAddress());
        addressImageView.setImageBitmap(generate(subaddress.getAddress(), 256, 256));
        copyAddressImageButton.setOnClickListener(view1 -> Helper.clipBoardCopy(getContext(), "address", subaddress.getAddress()));
    }

    private Bitmap generate(String text, int width, int height) {
        if ((width <= 0) || (height <= 0)) return null;
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        try {
            BitMatrix bitMatrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            int[] pixels = new int[width * height];
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    boolean night = NightmodeHelper.getPreferredNightmode() == DayNightMode.NIGHT;
                    if (bitMatrix.get(j, i)) {
                        pixels[i * width + j] = night ? 0xffffffff : 0x00000000;
                    } else {
                        pixels[i * height + j] = getResources().getColor(R.color.oled_dialogBackgroundColor);
                    }
                }
            }
            return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.RGB_565);
        } catch (WriterException ex) {
            Timber.e(ex);
        }
        return null;
    }
}