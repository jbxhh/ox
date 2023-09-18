package net.mynero.wallet.service;

import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import net.mynero.wallet.model.Coins;
import net.mynero.wallet.model.CoinsInfo;
import net.mynero.wallet.model.PendingTransaction;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.model.WalletManager;
import net.mynero.wallet.util.Constants;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class UTXOService extends ServiceBase {
    public static UTXOService instance = null;
    private final MutableLiveData<List<CoinsInfo>> _utxos = new MutableLiveData<>();
    public LiveData<List<CoinsInfo>> utxos = _utxos;
    private ArrayList<String> frozenCoins = new ArrayList<>();

    public UTXOService(MoneroHandlerThread thread) {
        super(thread);
        instance = this;
        try {
            this.loadFrozenCoins();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static UTXOService getInstance() {
        return instance;
    }

    public void refreshUtxos() {
        _utxos.postValue(getUtxosInternal());
    }

    public List<CoinsInfo> getUtxos() {
        List<CoinsInfo> value = utxos.getValue();
        return value != null ? value : List.of();
    }

    private List<CoinsInfo> getUtxosInternal() {
        return WalletManager.getInstance().getWallet().getCoins().getAll();
    }

    public void toggleFrozen(HashMap<String, CoinsInfo> selectedCoins) {
        ArrayList<String> frozenCoinsCopy = new ArrayList<>(frozenCoins);
        for(CoinsInfo coin : selectedCoins.values()) {
            if(frozenCoinsCopy.contains(coin.getPubKey())) {
                frozenCoinsCopy.remove(coin.getPubKey());
            } else {
                frozenCoinsCopy.add(coin.getPubKey());
            }
        }
        this.frozenCoins = frozenCoinsCopy;
        this.saveFrozenCoins();
        refreshUtxos();
        BalanceService.getInstance().refreshBalance();
    }

    public boolean isCoinFrozen(CoinsInfo coinsInfo) {
        return frozenCoins.contains(coinsInfo.getPubKey());
    }

    private void loadFrozenCoins() throws JSONException {
        PrefService prefService = PrefService.getInstance();
        String frozenCoinsArrayString = prefService.getString(Constants.PREF_FROZEN_COINS, "[]");
        JSONArray frozenCoinsArray = new JSONArray(frozenCoinsArrayString);
        for(int i = 0; i < frozenCoinsArray.length(); i++) {
            String pubKey = frozenCoinsArray.getString(i);
            frozenCoins.add(pubKey);
        }
        this.refreshUtxos();
    }

    private void saveFrozenCoins() {
        PrefService prefService = PrefService.getInstance();
        JSONArray jsonArray = new JSONArray();
        ArrayList<String> frozenCoinsCopy = new ArrayList<>(frozenCoins);
        for(String pubKey : frozenCoinsCopy) {
            jsonArray.put(pubKey);
        }
        prefService.edit().putString(Constants.PREF_FROZEN_COINS, jsonArray.toString()).apply();
    }

    public ArrayList<String> selectUtxos(long amount, boolean sendAll) throws Exception {
        final long basicFeeEstimate = calculateBasicFee(amount);
        final long amountWithBasicFee = amount + basicFeeEstimate;
        ArrayList<String> selectedUtxos = new ArrayList<>();
        ArrayList<String> seenTxs = new ArrayList<>();
        List<CoinsInfo> utxos = getUtxos();
        long amountSelected = 0;
        Collections.sort(utxos);
        //loop through each utxo
        for (CoinsInfo coinsInfo : utxos) {
            if (!coinsInfo.isSpent() && coinsInfo.isUnlocked() && !coinsInfo.isFrozen() && !frozenCoins.contains(coinsInfo.getPubKey())) { //filter out spent, locked, and frozen outputs
                if (sendAll) {
                    // if send all, add all utxos and set amount to send all
                    selectedUtxos.add(coinsInfo.getKeyImage());
                    amountSelected = Wallet.SWEEP_ALL;
                } else {
                    //if amount selected is still less than amount needed, and the utxos tx hash hasn't already been seen, add utxo
                    if (amountSelected <= amountWithBasicFee && !seenTxs.contains(coinsInfo.getHash())) {
                        selectedUtxos.add(coinsInfo.getKeyImage());
                        // we don't want to spend multiple utxos from the same transaction, so we prevent that from happening here.
                        seenTxs.add(coinsInfo.getHash());
                        amountSelected += coinsInfo.getAmount();
                    }
                }
            }
        }

        if (amountSelected < amountWithBasicFee && !sendAll) {
            throw new Exception("insufficient wallet balance");
        }

        return selectedUtxos;
    }

    private long calculateBasicFee(long amount) {
        ArrayList<Pair<String, Long>> destinations = new ArrayList<>();
        destinations.add(new Pair<>("87MRtZPrWUCVUgcFHdsVb5MoZUcLtqfD3FvQVGwftFb8eSdMnE39JhAJcbuSW8X2vRaRsB9RQfuCpFciybJFHaz3QYPhCLw", amount));
        // destination string doesn't actually matter here, so i'm using the donation address. amount also technically doesn't matter
        // priority also isn't accounted for in the Monero C++ code. maybe this is a bug by the core Monero team, or i'm using an outdated method.
        return WalletManager.getInstance().getWallet().estimateTransactionFee(destinations, PendingTransaction.Priority.Priority_Low);
    }
}
