package net.mynero.wallet.fragment.onboarding;

import android.app.Activity;
import android.util.Patterns;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.mynero.wallet.MainActivity;
import net.mynero.wallet.MoneroApplication;
import net.mynero.wallet.R;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.model.WalletManager;
import net.mynero.wallet.service.PrefService;
import net.mynero.wallet.util.Constants;
import net.mynero.wallet.util.RestoreHeight;

import java.io.File;
import java.util.Calendar;

public class OnboardingViewModel extends ViewModel {
    private final MutableLiveData<Boolean> _showMoreOptions = new MutableLiveData<>(false);
    public LiveData<Boolean> showMoreOptions = _showMoreOptions;
    private final MutableLiveData<Boolean> _enableCreateButton = new MutableLiveData<>(true);
    public LiveData<Boolean> enableCreateButton = _enableCreateButton;
    private String proxyAddress = "";
    private String proxyPort = "";

    public void onMoreOptionsClicked() {
        boolean currentValue = showMoreOptions.getValue() != null ? showMoreOptions.getValue() : false;
        boolean newValue = !currentValue;
        _showMoreOptions.setValue(newValue);
    }

    public void updateProxy(MoneroApplication application) {
        application.getExecutor().execute(() -> {
            boolean usesProxy = PrefService.getInstance().getBoolean(Constants.PREF_USES_TOR, false);

            if (!usesProxy) {
                return;
            }

            if (proxyAddress.isEmpty()) proxyAddress = "127.0.0.1";
            if (proxyPort.isEmpty()) proxyPort = "9050";
            boolean validIpAddress = Patterns.IP_ADDRESS.matcher(proxyAddress).matches();

            if (validIpAddress) {
                String proxy = proxyAddress + ":" + proxyPort;
                PrefService.getInstance().edit().putString(Constants.PREF_PROXY, proxy).apply();
            }
        });
    }


    public void setProxyAddress(String address) {
        this.proxyAddress = address;
    }

    public void setProxyPort(String port) {
        this.proxyPort = port;
    }

    public void createOrImportWallet(Activity mainActivity, String walletPassword, String confirmedPassword, String walletSeed, String restoreHeightText, boolean useOffset) {
        MoneroApplication application = (MoneroApplication)mainActivity.getApplication();
        application.getExecutor().execute(() -> {
            mainActivity.runOnUiThread(() -> {
                _enableCreateButton.setValue(false);
            });
            String offset = useOffset ? walletPassword : "";
            if (!walletPassword.isEmpty()) {
                if(!walletPassword.equals(confirmedPassword)) {
                    _enableCreateButton.setValue(true);
                    mainActivity.runOnUiThread(() -> Toast.makeText(mainActivity, application.getString(R.string.invalid_confirmed_password), Toast.LENGTH_SHORT).show());
                    return;
                }
                PrefService.getInstance().edit().putBoolean(Constants.PREF_USES_PASSWORD, true).apply();
            }
            long restoreHeight = getNewRestoreHeight();
            File walletFile = new File(mainActivity.getApplicationInfo().dataDir, Constants.WALLET_NAME);
            Wallet wallet = null;
            if(!offset.isEmpty()) {
                PrefService.getInstance().edit().putBoolean(Constants.PREF_USES_OFFSET, true).apply();
            }

            if (walletSeed.isEmpty()) {
                if(offset.isEmpty()) {
                    mainActivity.runOnUiThread(() -> {
                        _enableCreateButton.setValue(true);
                        Toast.makeText(mainActivity, application.getString(R.string.invalid_empty_passphrase), Toast.LENGTH_SHORT).show();
                    });
                    return;
                } else {
                    wallet = WalletManager.getInstance().createWalletPolyseed(walletFile, walletPassword, offset, Constants.MNEMONIC_LANGUAGE);
                }
            } else {
                if (getMnemonicType(walletSeed) == SeedType.UNKNOWN) {
                    mainActivity.runOnUiThread(() -> {
                        _enableCreateButton.setValue(true);
                        Toast.makeText(mainActivity, application.getString(R.string.invalid_mnemonic_code), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                if (!restoreHeightText.isEmpty()) {
                    restoreHeight = Long.parseLong(restoreHeightText);
                }
                wallet = WalletManager.getInstance().recoveryWallet(walletFile, walletPassword, walletSeed, offset, restoreHeight);
            }
            Wallet.Status walletStatus = wallet.getStatus();
            wallet.close();
            boolean ok = walletStatus.isOk();
            //walletFile.delete(); // cache is broken for some reason when recovering wallets. delete the file here. this happens in monerujo too.

            if (ok) {
                ((MainActivity)mainActivity).init(walletFile, walletPassword);
                mainActivity.runOnUiThread(mainActivity::onBackPressed);
            } else {
                mainActivity.runOnUiThread(() -> {
                    _enableCreateButton.setValue(true);
                    Toast.makeText(mainActivity, application.getString(R.string.create_wallet_failed, walletStatus.getErrorString()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private long getNewRestoreHeight() {
        Calendar restoreDate = Calendar.getInstance();
        restoreDate.add(Calendar.DAY_OF_MONTH, 0);
        return RestoreHeight.getInstance().getHeight(restoreDate.getTime());
    }

    public SeedType getMnemonicType(String seed) {
        String[] words = seed.split("\\s");
        if(words.length == 16) {
            return SeedType.POLYSEED;
        } else if (words.length == 25){
            return SeedType.LEGACY;
        } else {
            return SeedType.UNKNOWN;
        }
    }

    public enum SeedType {
        LEGACY,
        POLYSEED,
        UNKNOWN
    }
}