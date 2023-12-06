package net.mynero.wallet.fragment.utxos;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

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
import net.mynero.wallet.util.MoneroThreadPoolExecutor;
import net.mynero.wallet.util.UriData;

import java.util.ArrayList;
import java.util.HashMap;

public class UtxosFragment extends Fragment implements CoinsInfoAdapter.CoinsInfoAdapterListener, SendBottomSheetDialog.Listener {

    private final CoinsInfoAdapter adapter = new CoinsInfoAdapter(this);
    private UtxosViewModel mViewModel;
    private Button sendUtxosButton;
    private Button churnUtxosButton;
    private Button freezeUtxosButton;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_utxos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(UtxosViewModel.class);
        bindListeners(view);
        bindObservers(view);
    }

    private void bindListeners(View view) {
        freezeUtxosButton = view.findViewById(R.id.freeze_utxos_button);
        sendUtxosButton = view.findViewById(R.id.send_utxos_button);
        churnUtxosButton = view.findViewById(R.id.churn_utxos_button);
        sendUtxosButton.setVisibility(View.GONE);
        churnUtxosButton.setVisibility(View.GONE);
        freezeUtxosButton.setVisibility(View.GONE);
        freezeUtxosButton.setOnClickListener(view1 -> {
            Toast.makeText(getContext(), "Toggling freeze status, please wait.", Toast.LENGTH_SHORT).show();
            MoneroThreadPoolExecutor.MONERO_THREAD_POOL_EXECUTOR.execute(() -> {
                UTXOService.getInstance().toggleFrozen(adapter.selectedUtxos);
                getActivity().runOnUiThread(() -> {
                    adapter.clear();
                    sendUtxosButton.setVisibility(View.GONE);
                    churnUtxosButton.setVisibility(View.GONE);
                    freezeUtxosButton.setVisibility(View.GONE);
                });
            });
        });
        sendUtxosButton.setOnClickListener(view1 -> {
            ArrayList<String> selectedKeyImages = new ArrayList<>();
            for (CoinsInfo coinsInfo : adapter.selectedUtxos.values()) {
                selectedKeyImages.add(coinsInfo.keyImage);
            }
            SendBottomSheetDialog sendDialog = new SendBottomSheetDialog();
            sendDialog.listener = this;
            sendDialog.selectedUtxos = selectedKeyImages;
            sendDialog.show(getActivity().getSupportFragmentManager(), null);
        });
        churnUtxosButton.setOnClickListener(view1 -> {
            ArrayList<String> selectedKeyImages = new ArrayList<>();
            for (CoinsInfo coinsInfo : adapter.selectedUtxos.values()) {
                selectedKeyImages.add(coinsInfo.keyImage);
            }
            SendBottomSheetDialog sendDialog = new SendBottomSheetDialog();
            sendDialog.listener = this;
            sendDialog.isChurning = true;
            sendDialog.uriData = UriData.parse(AddressService.getInstance().currentSubaddress().address);
            sendDialog.selectedUtxos = selectedKeyImages;
            sendDialog.show(getActivity().getSupportFragmentManager(), null);
        });
    }

    private void bindObservers(View view) {
        RecyclerView utxosRecyclerView = view.findViewById(R.id.transaction_history_recyclerview);
        UTXOService utxoService = UTXOService.getInstance();
        utxosRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        utxosRecyclerView.setAdapter(adapter);
        if (utxoService != null) {
            utxoService.utxos.observe(getViewLifecycleOwner(), utxos -> {
                HashMap<String, CoinsInfo> filteredUtxos = new HashMap<>();
                for (CoinsInfo coinsInfo : utxos) {
                    if (!coinsInfo.isSpent()) {
                        filteredUtxos.put(coinsInfo.pubKey, coinsInfo);
                    }
                }
                if (filteredUtxos.isEmpty()) {
                    utxosRecyclerView.setVisibility(View.GONE);
                } else {
                    adapter.submitList(filteredUtxos);
                    utxosRecyclerView.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    @Override
    public void onUtxoSelected(CoinsInfo coinsInfo) {
        boolean selected = adapter.contains(coinsInfo);
        if (selected) {
            adapter.deselectUtxo(coinsInfo);
        } else {
            adapter.selectUtxo(coinsInfo);
        }

        boolean frozenExists = false, unfrozenExists = false, bothExist = false;
        for (CoinsInfo selectedUtxo : adapter.selectedUtxos.values()) {
            if (selectedUtxo.isFrozen() || UTXOService.getInstance().isCoinFrozen(selectedUtxo))
                frozenExists = true;
            else {
                unfrozenExists = true;
            }
        }
        bothExist = frozenExists && unfrozenExists;

        if (adapter.selectedUtxos.isEmpty()) {
            sendUtxosButton.setVisibility(View.GONE);
            churnUtxosButton.setVisibility(View.GONE);
            freezeUtxosButton.setVisibility(View.GONE);
            freezeUtxosButton.setBackgroundResource(R.drawable.button_bg_left);
        } else {
            if (frozenExists) {
                freezeUtxosButton.setBackgroundResource(R.drawable.button_bg);
                sendUtxosButton.setVisibility(View.GONE);
                churnUtxosButton.setVisibility(View.GONE);
            } else {
                freezeUtxosButton.setBackgroundResource(R.drawable.button_bg_left);
                sendUtxosButton.setVisibility(View.VISIBLE);
                churnUtxosButton.setVisibility(View.VISIBLE);
            }
            freezeUtxosButton.setVisibility(View.VISIBLE);
        }

        if (bothExist) {
            freezeUtxosButton.setText(R.string.toggle_freeze);
        } else if (frozenExists) {
            freezeUtxosButton.setText(R.string.unfreeze);
        } else if (unfrozenExists) {
            freezeUtxosButton.setText(R.string.freeze);
        }
    }

    @Override
    public void onSentTransaction() {
        adapter.clear();
        churnUtxosButton.setVisibility(View.GONE);
        sendUtxosButton.setVisibility(View.GONE);
        freezeUtxosButton.setVisibility(View.GONE);
    }
}