package net.mynero.wallet.fragment.send;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.mynero.wallet.MoneroApplication;
import net.mynero.wallet.R;
import net.mynero.wallet.model.PendingTransaction;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.service.BalanceService;
import net.mynero.wallet.service.TxService;
import net.mynero.wallet.util.Helper;

import java.util.ArrayList;
import java.util.List;

import kotlin.Pair;

public class SendFragment extends Fragment {
    private SendViewModel mViewModel;
    private Button sendMaxButton;
    private ImageButton addOutputImageView;
    private LinearLayout destList;
    private LayoutInflater inflater;
    private Button createButton;
    private RadioGroup feeRadioGroup;
    private TextView feeRadioGroupLabelTextView;

    private TextView feeTextView;
    private TextView addressTextView;
    private TextView amountTextView;

    public PendingTransaction.Priority priority;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_send, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(SendViewModel.class);
        sendMaxButton = view.findViewById(R.id.send_max_button);
        addOutputImageView = view.findViewById(R.id.add_output_button);
        destList = view.findViewById(R.id.transaction_destination_list);
        createButton = view.findViewById(R.id.create_tx_button);
        feeRadioGroup = view.findViewById(R.id.tx_fee_radiogroup);
        feeTextView = view.findViewById(R.id.fee_textview);
        addressTextView = view.findViewById(R.id.address_pending_textview);
        amountTextView = view.findViewById(R.id.amount_pending_textview);
        feeRadioGroup = view.findViewById(R.id.tx_fee_radiogroup);
        feeRadioGroupLabelTextView = view.findViewById(R.id.tx_fee_radiogroup_label_textview);

        FragmentActivity activity = getActivity();
        if(activity != null) {
            inflater = activity.getLayoutInflater();
        }
        bindListeners();
        bindObservers();
        init();
    }

    private void init() {
        addOutput();
    }

    private void bindListeners() {
        feeRadioGroup.check(R.id.low_fee_radiobutton);
        priority = PendingTransaction.Priority.Priority_Low;
        feeRadioGroup.setOnCheckedChangeListener((radioGroup, i) -> {
            if (i == R.id.low_fee_radiobutton) {
                priority = PendingTransaction.Priority.Priority_Low;
            } else if (i == R.id.med_fee_radiobutton) {
                priority = PendingTransaction.Priority.Priority_Medium;
            } else if (i == R.id.high_fee_radiobutton) {
                priority = PendingTransaction.Priority.Priority_High;
            }
        });

        addOutputImageView.setOnClickListener(view1 -> {
            sendMaxButton.setVisibility(View.GONE);
            addOutput();
        });
        sendMaxButton.setOnClickListener(view1 -> {
            addOutputImageView.setVisibility(View.INVISIBLE);
            boolean currentValue = mViewModel.sendingMax.getValue() != null ? mViewModel.sendingMax.getValue() : false;
            mViewModel.setSendingMax(!currentValue);
        });
        createButton.setOnClickListener(view1 -> {
            boolean sendAll = mViewModel.sendingMax.getValue() != null ? mViewModel.sendingMax.getValue() : false;
            ArrayList<Pair<String, String>> dests = new ArrayList<>();
            for(int i = 0; i < getDestCount(); i++) {
                ConstraintLayout entryView = getDestView(i);
                EditText amountField = entryView.findViewById(R.id.amount_edittext);
                EditText addressField = entryView.findViewById(R.id.address_edittext);
                String amount = amountField.getText().toString().trim();
                if(!sendAll) {
                    if(amount.isEmpty()) {
                        Toast.makeText(getActivity(), getString(R.string.send_amount_empty), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    long amountRaw = Wallet.getAmountFromString(amount);
                    long balance = BalanceService.getInstance().getUnlockedBalanceRaw();
                    if (amountRaw >= balance || amountRaw <= 0) {
                        Toast.makeText(getActivity(), getString(R.string.send_amount_invalid), Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                String address = addressField.getText().toString().trim();
                boolean isValidAddress = Wallet.isAddressValid(address);
                if(!isValidAddress) {
                    Toast.makeText(getActivity(), getString(R.string.send_address_invalid), Toast.LENGTH_SHORT).show();
                    return;
                }
                dests.add(new Pair<>(address, amount));
            }

            Toast.makeText(getActivity(), getString(R.string.creating_tx), Toast.LENGTH_SHORT).show();
            createButton.setEnabled(false);
            createTx(dests, sendAll, priority);
        });
    }

    private void bindObservers() {
        mViewModel.sendingMax.observe(getViewLifecycleOwner(), sendingMax -> {
            if (mViewModel.pendingTransaction.getValue() == null) {
                if (sendingMax) {
                    addOutputImageView.setVisibility(View.GONE);
                    prepareOutputsForMaxSend();
                    sendMaxButton.setText(getText(R.string.undo));
                } else {
                    addOutputImageView.setVisibility(View.VISIBLE);
                    unprepareMaxSend();
                    sendMaxButton.setText(getText(R.string.send_max));
                }
            }
        });

        mViewModel.pendingTransaction.observe(getViewLifecycleOwner(), pendingTx -> {
            showConfirmationLayout(pendingTx != null);

            if (pendingTx != null) {
                String address = getDestCount() == 1 ? getAddressField(0).getText().toString() : "Multiple";
                addressTextView.setText(getString(R.string.tx_address_text, address));
                amountTextView.setText(getString(R.string.tx_amount_text, Helper.getDisplayAmount(pendingTx.getAmount())));
                feeTextView.setText(getString(R.string.tx_fee_text, Helper.getDisplayAmount(pendingTx.getFee())));
            }
        });
    }

    private void addOutput() {
        if (inflater != null) {
            ConstraintLayout entryView = (ConstraintLayout)inflater.inflate(R.layout.transaction_output_item, null);
            entryView.findViewById(R.id.remove_output_imagebutton).setOnClickListener(view -> {
                int currentCount = getDestCount();
                if(currentCount > 1) {
                    destList.removeView(entryView);
                }
            });
            destList.addView(entryView);
        }
    }

    private int getDestCount() {
        return destList.getChildCount();
    }

    private ConstraintLayout getDestView(int pos) {
        return (ConstraintLayout) destList.getChildAt(pos);
    }

    private EditText getAddressField(int pos) {
        return (EditText) getDestView(pos).findViewById(R.id.address_edittext);
    }

    private void unprepareMaxSend() {
        ConstraintLayout entryView = getDestView(0);
        entryView.findViewById(R.id.sending_all_textview).setVisibility(View.GONE);
        entryView.findViewById(R.id.amount_edittext).setVisibility(View.VISIBLE);
    }

    private void prepareOutputsForMaxSend() {
        ConstraintLayout entryView = getDestView(0);
        entryView.findViewById(R.id.sending_all_textview).setVisibility(View.VISIBLE);
        entryView.findViewById(R.id.amount_edittext).setVisibility(View.GONE);
    }

    private void showConfirmationLayout(boolean show) {
        if (show) {
            destList.setVisibility(View.GONE);
            addOutputImageView.setVisibility(View.GONE);
            sendMaxButton.setVisibility(View.GONE);
            createButton.setVisibility(View.GONE);
            feeRadioGroup.setVisibility(View.GONE);
            feeRadioGroupLabelTextView.setVisibility(View.GONE);

            feeTextView.setVisibility(View.VISIBLE);
            addressTextView.setVisibility(View.VISIBLE);
            amountTextView.setVisibility(View.VISIBLE);
        } else {
            destList.setVisibility(View.VISIBLE);
            addOutputImageView.setVisibility(View.VISIBLE);
            sendMaxButton.setVisibility(View.VISIBLE);
            createButton.setVisibility(View.VISIBLE);
            feeRadioGroup.setVisibility(View.VISIBLE);
            feeRadioGroupLabelTextView.setVisibility(View.VISIBLE);

            feeTextView.setVisibility(View.GONE);
            addressTextView.setVisibility(View.GONE);
            amountTextView.setVisibility(View.GONE);
        }
    }

    private void createTx(List<Pair<String, String>> dests, boolean sendAll, PendingTransaction.Priority feePriority) {
        ((MoneroApplication)getActivity().getApplication()).getExecutor().execute(() -> {
            try {
                PendingTransaction pendingTx = TxService.getInstance().createTx(dests, sendAll, feePriority, new ArrayList<>());
                if (pendingTx != null && pendingTx.getStatus() == PendingTransaction.Status.Status_Ok) {
                    mViewModel.setPendingTransaction(pendingTx);
                } else {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            createButton.setEnabled(true);
                            Toast.makeText(getActivity(), getString(R.string.error_creating_tx), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        createButton.setEnabled(true);
                        Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
}