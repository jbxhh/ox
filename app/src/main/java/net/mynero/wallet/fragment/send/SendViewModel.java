package net.mynero.wallet.fragment.send;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.mynero.wallet.data.Subaddress;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.model.WalletManager;
import net.mynero.wallet.service.AddressService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SendViewModel extends ViewModel {
    private final MutableLiveData<Subaddress> _address = new MutableLiveData<>();
    public LiveData<Subaddress> address = _address;
    private final MutableLiveData<List<Subaddress>> _addresses = new MutableLiveData<>();
    public LiveData<List<Subaddress>> addresses = _addresses;

    public void init() {
        _address.setValue(AddressService.getInstance().currentSubaddress());
        _addresses.setValue(getSubaddresses());
    }

    private List<Subaddress> getSubaddresses() {
        Wallet wallet = WalletManager.getInstance().getWallet();
        ArrayList<Subaddress> subaddresses = new ArrayList<>();
        int addressesSize = AddressService.getInstance().getLatestAddressIndex();
        for(int i = addressesSize - 1; i >= 0; i--) {
            subaddresses.add(wallet.getSubaddressObject(i));
        }
        return Collections.unmodifiableList(subaddresses);
    }

    public void getFreshSubaddress() {
        _address.setValue(AddressService.getInstance().freshSubaddress());
        _addresses.setValue(getSubaddresses());
    }

    public void selectAddress(Subaddress subaddress) {
        _address.setValue(subaddress);
    }
}