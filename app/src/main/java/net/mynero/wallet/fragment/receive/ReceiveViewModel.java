package net.mynero.wallet.fragment.receive;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.mynero.wallet.data.Subaddress;
import net.mynero.wallet.service.AddressService;

public class ReceiveViewModel extends ViewModel {
    private MutableLiveData<Subaddress> _address = new MutableLiveData<>();
    public LiveData<Subaddress> address = _address;

    public void init() {
        _address.setValue(AddressService.getInstance().currentSubaddress());
    }

    public void getFreshSubaddress() {
        _address.setValue(AddressService.getInstance().freshSubaddress());
    }

    public void selectAddress(Subaddress subaddress) {
        _address.setValue(subaddress);
    }
}