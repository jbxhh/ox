package net.mynero.wallet.fragment.send;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.ncorti.slidetoact.SlideToActView;

import net.mynero.wallet.MoneroApplication;
import net.mynero.wallet.R;
import net.mynero.wallet.model.PendingTransaction;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.service.BalanceService;
import net.mynero.wallet.service.TxService;
import net.mynero.wallet.util.Helper;
import net.mynero.wallet.util.UriData;

import java.util.ArrayList;
import java.util.List;

import kotlin.Pair;

public class SendFragment extends Fragment {
    public PendingTransaction.Priority priority;
    private SendViewModel mViewModel;
    private Button sendMaxButton;
    private ImageButton addOutputImageView;
    private LinearLayout destList;
    private LayoutInflater inflater;
    private Button createButton;
    private SlideToActView sendTxSlider;
    private RadioGroup feeRadioGroup;
    private TextView feeRadioGroupLabelTextView;
    private TextView feeTextView;
    private TextView addressTextView;
    private TextView amountTextView;
    private int currentEntryIndex = -1;
    private final ActivityResultLauncher<ScanOptions> qrCodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        if (result.getContents() != null) {
            if (currentEntryIndex != -1) {
                pasteAddress(getDestView(currentEntryIndex), result.getContents(), false);
                currentEntryIndex = -1;
            }
        }
    });

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
        sendTxSlider = view.findViewById(R.id.send_tx_slider);
        addressTextView = view.findViewById(R.id.address_pending_textview);
        amountTextView = view.findViewById(R.id.amount_pending_textview);
        feeRadioGroup = view.findViewById(R.id.tx_fee_radiogroup);
        feeRadioGroupLabelTextView = view.findViewById(R.id.tx_fee_radiogroup_label_textview);

        FragmentActivity activity = getActivity();
        if (activity != null) {
            inflater = activity.getLayoutInflater();
        }
        bindListeners();
        bindObservers();
        init();
    }    private final ActivityResultLauncher<String> cameraPermissionsLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    onScan(currentEntryIndex);
                } else {
                    Toast.makeText(getActivity(), getString(R.string.no_camera_permission), Toast.LENGTH_SHORT).show();
                    currentEntryIndex = -1;
                }
            });

    private void init() {
        addOutput(true);
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
            int outputCount = getDestCount();
            if (outputCount < 8) {
                addOutput(false);
            } else {
                Toast.makeText(getActivity(), getString(R.string.max_outputs_allowed), Toast.LENGTH_SHORT).show();
            }
        });
        sendMaxButton.setOnClickListener(view1 -> {
            mViewModel.setSendingMax(!isSendAll());
        });
        createButton.setOnClickListener(view1 -> {
            boolean outputsValid = checkDestsValidity(isSendAll());

            if (outputsValid) {
                Toast.makeText(getActivity(), getString(R.string.creating_tx), Toast.LENGTH_SHORT).show();
                createButton.setEnabled(false);
                sendMaxButton.setEnabled(false);
                createTx(getRawDests(), isSendAll(), priority);
            } else {
                Toast.makeText(getActivity(), getString(R.string.creating_tx_failed_invalid_outputs), Toast.LENGTH_SHORT).show();
            }
        });

        sendTxSlider.setOnSlideCompleteListener(slideToActView -> {
            PendingTransaction pendingTx = mViewModel.pendingTransaction.getValue();
            if (pendingTx != null) {
                Toast.makeText(getActivity(), getString(R.string.sending_tx), Toast.LENGTH_SHORT).show();
                sendTx(pendingTx);
            }
        });
    }

    private boolean checkDestsValidity(boolean sendAll) {
        List<Pair<String, String>> dests = getRawDests();
        for (Pair<String, String> dest : dests) {
            String address = dest.component1();
            String amount = dest.component2();
            if (!sendAll) {
                if (amount.isEmpty()) {
                    Toast.makeText(getActivity(), getString(R.string.send_amount_empty), Toast.LENGTH_SHORT).show();
                    return false;
                }

                long amountRaw = Wallet.getAmountFromString(amount);
                long balance = BalanceService.instance.getUnlockedBalanceRaw();
                if (amountRaw >= balance || amountRaw <= 0) {
                    Toast.makeText(getActivity(), getString(R.string.send_amount_invalid), Toast.LENGTH_SHORT).show();
                    return false;
                }
            } else if (dests.size() > 1) {
                Toast.makeText(getActivity(), getString(R.string.send_amount_invalid_sendall_paytomany), Toast.LENGTH_SHORT).show();
                return false;
            }

            UriData uriData = UriData.parse(address);
            boolean isValidAddress = uriData != null;
            if (!isValidAddress) {
                Toast.makeText(getActivity(), getString(R.string.send_address_invalid), Toast.LENGTH_SHORT).show();
                return false;
            }

            if (dests.size() > 1 && uriData.hasPaymentId()) {
                Toast.makeText(getActivity(), getString(R.string.paymentid_paytomany), Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        return true;
    }

    private boolean destsHasPaymentId() {
        List<Pair<String, String>> dests = getRawDests();
        for (Pair<String, String> dest : dests) {
            String address = dest.component1();
            UriData uriData = UriData.parse(address);
            if (uriData == null) return false;
            if (uriData.hasPaymentId()) return true;
        }
        return false;
    }

    private void bindObservers() {
        mViewModel.sendingMax.observe(getViewLifecycleOwner(), sendingMax -> {
            if (mViewModel.pendingTransaction.getValue() == null) {
                if (sendingMax) {
                    prepareOutputsForMaxSend();
                    sendMaxButton.setText(getText(R.string.undo));
                } else {
                    unprepareMaxSend();
                    sendMaxButton.setText(getText(R.string.send_max));
                }
            }
        });

        mViewModel.showAddOutputButton.observe(getViewLifecycleOwner(), show -> {
            setAddOutputButtonVisibility((show && !destsHasPaymentId()) ? View.VISIBLE : View.INVISIBLE);
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

    private void addOutput(boolean initial) {
        if (inflater != null) {
            int index = getDestCount();
            ConstraintLayout entryView = (ConstraintLayout) inflater.inflate(R.layout.transaction_output_item, null);
            ImageButton removeOutputImageButton = entryView.findViewById(R.id.remove_output_imagebutton);
            EditText addressField = entryView.findViewById(R.id.address_edittext);
            addressField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    int currentOutputs = getDestCount();
                    UriData uriData = UriData.parse(editable.toString());
                    if (uriData != null) {
                        // we have valid address
                        boolean hasPaymentId = uriData.hasPaymentId();
                        if (currentOutputs > 1 && hasPaymentId) {
                            // multiple outputs when pasting/editing in integrated address. this is not allowed
                            Toast.makeText(getActivity(), getString(R.string.paymentid_paytomany), Toast.LENGTH_SHORT).show();
                            addressField.setText(null);
                        } else if (currentOutputs == 1 && hasPaymentId) {
                            // show add output button: we are sending to integrated address
                            mViewModel.setShowAddOutputButton(false);
                        }
                    } else if (currentOutputs == 1 && !isSendAll()) {
                        // when send-all is false and this is our only dest and address is invalid, then show add output button
                        mViewModel.setShowAddOutputButton(true);
                    }
                }
            });
            entryView.findViewById(R.id.paste_amount_imagebutton).setOnClickListener(view1 -> {
                Context ctx = getContext();
                if (ctx != null) {
                    String clipboard = Helper.getClipBoardText(ctx);
                    if (clipboard != null) {
                        pasteAddress(entryView, clipboard, true);
                    }
                }
            });
            entryView.findViewById(R.id.paste_address_imagebutton).setOnClickListener(view1 -> {
                Context ctx = getContext();
                if (ctx != null) {
                    String clipboard = Helper.getClipBoardText(ctx);
                    if (clipboard != null) {
                        pasteAddress(entryView, clipboard, false);
                    }
                }
            });
            entryView.findViewById(R.id.scan_address_imagebutton).setOnClickListener(view -> onScan(index));
            if (initial) {
                removeOutputImageButton.setVisibility(View.INVISIBLE);
            } else {
                removeOutputImageButton.setOnClickListener(view -> {
                    int currentCount = getDestCount();
                    if (currentCount > 1) {
                        if (currentCount == 2) {
                            sendMaxButton.setVisibility(View.VISIBLE);
                        }
                        destList.removeView(entryView);
                    }
                });
            }
            destList.addView(entryView);
        }
    }

    private int getDestCount() {
        return destList.getChildCount();
    }

    private List<Pair<String, String>> getRawDests() {
        ArrayList<Pair<String, String>> dests = new ArrayList<>();
        for (int i = 0; i < getDestCount(); i++) {
            ConstraintLayout entryView = getDestView(i);
            EditText amountField = entryView.findViewById(R.id.amount_edittext);
            EditText addressField = entryView.findViewById(R.id.address_edittext);
            String amount = amountField.getText().toString().trim();
            String address = addressField.getText().toString().trim();
            dests.add(new Pair<>(address, amount));
        }

        return dests;
    }

    private boolean isSendAll() {
        return mViewModel.sendingMax.getValue() != null ? mViewModel.sendingMax.getValue() : false;
    }

    private ConstraintLayout getDestView(int pos) {
        return (ConstraintLayout) destList.getChildAt(pos);
    }

    private EditText getAddressField(int pos) {
        return (EditText) getDestView(pos).findViewById(R.id.address_edittext);
    }

    private void unprepareMaxSend() {
        ConstraintLayout entryView = getDestView(0);
        entryView.findViewById(R.id.sending_all_textview).setVisibility(View.INVISIBLE);
        entryView.findViewById(R.id.amount_edittext).setVisibility(View.VISIBLE);
    }

    private void prepareOutputsForMaxSend() {
        ConstraintLayout entryView = getDestView(0);
        entryView.findViewById(R.id.sending_all_textview).setVisibility(View.VISIBLE);
        entryView.findViewById(R.id.amount_edittext).setVisibility(View.INVISIBLE);
    }

    private void showConfirmationLayout(boolean show) {
        if (show) {
            destList.setVisibility(View.GONE);
            setAddOutputButtonVisibility(View.GONE);
            sendMaxButton.setVisibility(View.GONE);
            createButton.setVisibility(View.GONE);
            feeRadioGroup.setVisibility(View.GONE);
            feeRadioGroupLabelTextView.setVisibility(View.GONE);

            sendTxSlider.setVisibility(View.VISIBLE);
            feeTextView.setVisibility(View.VISIBLE);
            addressTextView.setVisibility(View.VISIBLE);
            amountTextView.setVisibility(View.VISIBLE);
        } else {
            destList.setVisibility(View.VISIBLE);
            setAddOutputButtonVisibility(View.VISIBLE);
            sendMaxButton.setVisibility(View.VISIBLE);
            createButton.setVisibility(View.VISIBLE);
            feeRadioGroup.setVisibility(View.VISIBLE);
            feeRadioGroupLabelTextView.setVisibility(View.VISIBLE);

            sendTxSlider.setVisibility(View.GONE);
            feeTextView.setVisibility(View.GONE);
            addressTextView.setVisibility(View.GONE);
            amountTextView.setVisibility(View.GONE);
        }
    }

    private void onScan(int index) {
        currentEntryIndex = index;
        if (Helper.getCameraPermission(getActivity(), cameraPermissionsLauncher)) {
            ScanOptions options = new ScanOptions();
            options.setBeepEnabled(false);
            options.setOrientationLocked(true);
            options.setDesiredBarcodeFormats(List.of(Intents.Scan.QR_CODE_MODE));
            options.addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN);
            qrCodeLauncher.launch(options);
        }
    }

    private void pasteAddress(ConstraintLayout entryView, String clipboard, boolean pastingAmount) {
        if (pastingAmount) {
            try {
                Double.parseDouble(clipboard);
                setAmount(entryView, clipboard);
            } catch (Exception e) {
                Toast.makeText(getActivity(), getString(R.string.send_amount_invalid), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        UriData uriData = UriData.parse(clipboard);
        if (uriData != null) {
            int currentOutputs = getDestCount();
            if (currentOutputs > 1 && uriData.hasPaymentId()) {
                Toast.makeText(getActivity(), getString(R.string.paymentid_paytomany), Toast.LENGTH_SHORT).show();
                return;
            } else if (currentOutputs == 1 && uriData.hasPaymentId()) {
                mViewModel.setShowAddOutputButton(false);
            }
            EditText addressField = entryView.findViewById(R.id.address_edittext);
            addressField.setText(uriData.address);
            if (uriData.hasAmount()) {
                setAmount(entryView, uriData.getAmount());
            }
        } else {
            Toast.makeText(getActivity(), getString(R.string.send_address_invalid), Toast.LENGTH_SHORT).show();
        }
    }

    private void setAmount(ConstraintLayout entryView, String amount) {
        sendMaxButton.setEnabled(false);
        EditText amountField = entryView.findViewById(R.id.amount_edittext);
        amountField.setText(amount);
    }

    private void createTx(List<Pair<String, String>> dests, boolean sendAll, PendingTransaction.Priority feePriority) {
        ((MoneroApplication) getActivity().getApplication()).getExecutor().execute(() -> {
            try {
                PendingTransaction pendingTx = TxService.instance.createTx(dests, sendAll, feePriority, new ArrayList<>());
                if (pendingTx != null && pendingTx.getStatus() == PendingTransaction.Status.Status_Ok) {
                    mViewModel.setPendingTransaction(pendingTx);
                } else {
                    Activity activity = getActivity();
                    if (activity != null && pendingTx != null) {
                        activity.runOnUiThread(() -> {
                            createButton.setEnabled(true);
                            sendMaxButton.setEnabled(true);
                            if (pendingTx.getErrorString() != null)
                                Toast.makeText(activity, getString(R.string.error_creating_tx, pendingTx.getErrorString()), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        createButton.setEnabled(true);
                        sendMaxButton.setEnabled(true);
                        Toast.makeText(activity, getString(R.string.error_creating_tx, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void sendTx(PendingTransaction pendingTx) {
        ((MoneroApplication) getActivity().getApplication()).getExecutor().execute(() -> {
            boolean success = TxService.instance.sendTx(pendingTx);
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(getActivity(), getString(R.string.sent_tx), Toast.LENGTH_SHORT).show();
                        getActivity().onBackPressed();
                    } else {
                        sendTxSlider.resetSlider();
                        Toast.makeText(getActivity(), getString(R.string.error_sending_tx), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void setAddOutputButtonVisibility(int visibility) {
        addOutputImageView.setVisibility(visibility);
    }




}