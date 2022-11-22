package net.mynero.wallet.fragment.receive;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.mynero.wallet.data.Subaddress;
import net.mynero.wallet.model.WalletManager;
import net.mynero.wallet.service.AddressService;

import java.util.ArrayList;
import java.util.List;

public class ReceiveViewModel extends ViewModel {
    private final MutableLiveData<Subaddress> _address = new MutableLiveData<>();
    public LiveData<Subaddress> address = _address;
    private final MutableLiveData<List<Subaddress>> _addresses = new MutableLiveData<>();
    public LiveData<List<Subaddress>> addresses = _addresses;

    public void init() {
        _address.setValue(AddressService.getInstance().currentSubaddress());
        _addresses.setValue(getSubaddresses());
    }

    private List<Subaddress> getSubaddresses() {
        ArrayList<Subaddress> subaddresses = new ArrayList<>();
        int addressesSize = AddressService.getInstance().getLatestAddressIndex();
        for(int i = 0; i < addressesSize; i++) {
            subaddresses.add(WalletManager.getInstance().getWallet().getSubaddressObject(i));
        }
        return subaddresses;
    }

    public void getFreshSubaddress() {
        _address.setValue(AddressService.getInstance().freshSubaddress());
        _addresses.setValue(getSubaddresses());
    }

    public void selectAddress(Subaddress subaddress) {
        _address.setValue(subaddress);
    }
}