package net.mynero.wallet.fragment.receive;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.mynero.wallet.R;
import net.mynero.wallet.adapter.CoinsInfoAdapter;
import net.mynero.wallet.fragment.dialog.SendBottomSheetDialog;
import net.mynero.wallet.model.CoinsInfo;
import net.mynero.wallet.service.AddressService;
import net.mynero.wallet.service.UTXOService;
import net.mynero.wallet.util.UriData;

import java.util.ArrayList;
import java.util.Collections;

public class ReceiveFragment extends Fragment {

    private ReceiveViewModel mViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.receive_bottom_sheet_dialog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ReceiveViewModel.class);
        bindListeners(view);
        bindObservers(view);
    }

    private void bindListeners(View view) {

    }

    private void bindObservers(View view) {

    }
}