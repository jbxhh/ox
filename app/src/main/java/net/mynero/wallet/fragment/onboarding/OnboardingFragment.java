package net.mynero.wallet.fragment.onboarding;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import net.mynero.wallet.MainActivity;
import net.mynero.wallet.MoneroApplication;
import net.mynero.wallet.R;
import net.mynero.wallet.data.Node;
import net.mynero.wallet.fragment.dialog.AddNodeBottomSheetDialog;
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.model.WalletManager;
import net.mynero.wallet.service.PrefService;
import net.mynero.wallet.util.Constants;
import net.mynero.wallet.util.RestoreHeight;

import org.json.JSONObject;

import java.io.File;
import java.util.Calendar;

public class OnboardingFragment extends Fragment implements NodeSelectionBottomSheetDialog.NodeSelectionDialogListener, AddNodeBottomSheetDialog.AddNodeListener {
    private boolean useOffset = true;
    private OnboardingViewModel mViewModel;
    TextWatcher proxyAddressListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (mViewModel != null) {
                mViewModel.setProxyAddress(editable.toString());
                mViewModel.updateProxy(((MoneroApplication)getActivity().getApplication()));
            }
        }
    };
    TextWatcher proxyPortListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (mViewModel != null) {
                mViewModel.setProxyPort(editable.toString());
                mViewModel.updateProxy(((MoneroApplication)getActivity().getApplication()));
            }
        }
    };
    private EditText walletProxyAddressEditText;
    private EditText walletProxyPortEditText;
    private EditText walletPasswordEditText;
    private EditText walletPasswordConfirmEditText;
    private EditText walletSeedEditText;
    private EditText walletRestoreHeightEditText;
    private Button createWalletButton;
    private TextView moreOptionsDropdownTextView;
    private SwitchCompat torSwitch;
    private ConstraintLayout proxySettingsLayout;
    private ConstraintLayout advancedOptionsLayout;
    private ImageView moreOptionsChevronImageView;
    private CheckBox seedOffsetCheckbox;
    private Button selectNodeButton;
    private SwitchCompat showXmrchanSwitch;
    private ImageView xmrchanOnboardingImage;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);
        selectNodeButton = view.findViewById(R.id.select_node_button);
        walletPasswordEditText = view.findViewById(R.id.wallet_password_edittext);
        walletPasswordConfirmEditText = view.findViewById(R.id.wallet_password_confirm_edittext);
        walletSeedEditText = view.findViewById(R.id.wallet_seed_edittext);
        walletRestoreHeightEditText = view.findViewById(R.id.wallet_restore_height_edittext);
        createWalletButton = view.findViewById(R.id.create_wallet_button);
        moreOptionsDropdownTextView = view.findViewById(R.id.advanced_settings_dropdown_textview);
        moreOptionsChevronImageView = view.findViewById(R.id.advanced_settings_chevron_imageview);
        torSwitch = view.findViewById(R.id.tor_onboarding_switch);
        proxySettingsLayout = view.findViewById(R.id.wallet_proxy_settings_layout);
        seedOffsetCheckbox = view.findViewById(R.id.seed_offset_checkbox);
        walletProxyAddressEditText = view.findViewById(R.id.wallet_proxy_address_edittext);
        walletProxyPortEditText = view.findViewById(R.id.wallet_proxy_port_edittext);
        seedOffsetCheckbox.setChecked(useOffset);
        advancedOptionsLayout = view.findViewById(R.id.more_options_layout);
        showXmrchanSwitch = view.findViewById(R.id.show_xmrchan_switch);
        xmrchanOnboardingImage = view.findViewById(R.id.xmrchan_onboarding_imageview);

        bindListeners();
        bindObservers();
    }

    private void bindObservers() {
        mViewModel.showMoreOptions.observe(getViewLifecycleOwner(), show -> {
            if (show) {
                moreOptionsChevronImageView.setImageResource(R.drawable.ic_keyboard_arrow_up);
                advancedOptionsLayout.setVisibility(View.VISIBLE);
            } else {
                moreOptionsChevronImageView.setImageResource(R.drawable.ic_keyboard_arrow_down);
                advancedOptionsLayout.setVisibility(View.GONE);
            }
        });

        mViewModel.enableCreateButton.observe(getViewLifecycleOwner(), enable -> {
            createWalletButton.setEnabled(enable);
        });
    }

    private void bindListeners() {
        // Disable onBack click
        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) { @Override public void handleOnBackPressed() { } };
        FragmentActivity activity = getActivity();
        if(activity != null)
            activity.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);

        moreOptionsDropdownTextView.setOnClickListener(view12 -> mViewModel.onMoreOptionsClicked());
        moreOptionsChevronImageView.setOnClickListener(view12 -> mViewModel.onMoreOptionsClicked());
        seedOffsetCheckbox.setOnCheckedChangeListener((compoundButton, b) -> useOffset = b);

        createWalletButton.setOnClickListener(view1 -> {
            prepareDefaultNode();
            onBackPressedCallback.setEnabled(false);
            ((MoneroApplication)getActivity().getApplication()).getExecutor().execute(() -> {
                createOrImportWallet(
                        walletPasswordEditText.getText().toString(),
                        walletPasswordConfirmEditText.getText().toString(),
                        walletSeedEditText.getText().toString().trim(),
                        walletRestoreHeightEditText.getText().toString().trim()
                );
            });
        });
        walletPasswordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String text = editable.toString();
                if (text.isEmpty()) {
                    walletPasswordConfirmEditText.setText(null);
                    walletPasswordConfirmEditText.setVisibility(View.GONE);
                } else {
                    walletPasswordConfirmEditText.setVisibility(View.VISIBLE);
                }
            }
        });
        walletSeedEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String text = editable.toString();
                if (text.isEmpty()) {
                    createWalletButton.setText(R.string.create_wallet);
                } else {
                    createWalletButton.setText(R.string.menu_restore);
                }
            }
        });
        torSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            PrefService.getInstance().edit().putBoolean(Constants.PREF_USES_TOR, b).apply();
            if (b) {
                removeProxyTextListeners();

                if (PrefService.getInstance().hasProxySet()) {
                    String proxyAddress = PrefService.getInstance().getProxyAddress();
                    String proxyPort = PrefService.getInstance().getProxyPort();
                    initProxyStuff(proxyAddress, proxyPort);
                } else {
                    initProxyStuff("127.0.0.1", "9050");
                }
                addProxyTextListeners();

                proxySettingsLayout.setVisibility(View.VISIBLE);
            } else {
                proxySettingsLayout.setVisibility(View.GONE);
            }

            mViewModel.updateProxy(((MoneroApplication)getActivity().getApplication()));
        });

        showXmrchanSwitch.setChecked(PrefService.getInstance().getBoolean(Constants.PREF_MONEROCHAN, true));
        showXmrchanSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            PrefService.getInstance().edit().putBoolean(Constants.PREF_MONEROCHAN, b).apply();
            if(b) {
                xmrchanOnboardingImage.setVisibility(View.VISIBLE);
            } else {
                xmrchanOnboardingImage.setVisibility(View.GONE);
            }
        });

        Node node = PrefService.getInstance().getNode(); // should be using default here
        selectNodeButton.setText(getString(R.string.node_button_text, node.getAddress()));
        selectNodeButton.setOnClickListener(view1 -> {
            NodeSelectionBottomSheetDialog dialog = new NodeSelectionBottomSheetDialog();
            dialog.listener = this;
            dialog.show(getActivity().getSupportFragmentManager(), "node_selection_dialog");
        });
    }

    private void prepareDefaultNode() {
        PrefService.getInstance().getNode();
    }

    private void createOrImportWallet(String walletPassword, String confirmedPassword, String walletSeed, String restoreHeightText) {
        Activity activity = getActivity();
        if(activity != null) {
            mViewModel.createOrImportWallet(activity, walletPassword, confirmedPassword, walletSeed, restoreHeightText, useOffset);
        }
    }

    private void removeProxyTextListeners() {
        walletProxyAddressEditText.removeTextChangedListener(proxyAddressListener);
        walletProxyPortEditText.removeTextChangedListener(proxyPortListener);
    }

    private void addProxyTextListeners() {
        walletProxyAddressEditText.addTextChangedListener(proxyAddressListener);
        walletProxyPortEditText.addTextChangedListener(proxyPortListener);
    }

    private void initProxyStuff(String proxyAddress, String proxyPort) {
        boolean validIpAddress = Patterns.IP_ADDRESS.matcher(proxyAddress).matches();
        if (validIpAddress) {
            mViewModel.setProxyAddress(proxyAddress);
            mViewModel.setProxyPort(proxyPort);
            walletProxyAddressEditText.setText(proxyAddress);
            walletProxyPortEditText.setText(proxyPort);
        }
    }

    @Override
    public void onNodeSelected() {
        Node node = PrefService.getInstance().getNode();
        selectNodeButton.setText(getString(R.string.node_button_text, node.getAddress()));
        mViewModel.updateProxy(((MoneroApplication)getActivity().getApplication()));
    }

    @Override
    public void onClickedEditNode(Node node) { }

    @Override
    public void onClickedAddNode() {
        AddNodeBottomSheetDialog addNodeDialog = new AddNodeBottomSheetDialog();
        addNodeDialog.listener = this;
        addNodeDialog.show(getActivity().getSupportFragmentManager(), "add_node_dialog");
    }

    @Override
    public void onNodeAdded() {
        NodeSelectionBottomSheetDialog dialog = new NodeSelectionBottomSheetDialog();
        dialog.listener = this;
        dialog.show(getActivity().getSupportFragmentManager(), "node_selection_dialog");
    }
}