/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.mynero.wallet.service;

import net.mynero.wallet.data.DefaultNodes;
import net.mynero.wallet.data.Node;
import net.mynero.wallet.data.TxData;
import net.mynero.wallet.model.CoinsInfo;
import net.mynero.wallet.model.PendingTransaction;
import net.mynero.wallet.model.TransactionOutput;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.model.WalletListener;
import net.mynero.wallet.model.WalletManager;
import net.mynero.wallet.util.Constants;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Handy class for starting a new thread that has a looper. The looper can then be
 * used to create handler classes. Note that start() must still be called.
 * The started Thread has a stck size of STACK_SIZE (=5MB)
 */
public class MoneroHandlerThread extends Thread implements WalletListener {
    // from src/cryptonote_config.h
    static public final long THREAD_STACK_SIZE = 5 * 1024 * 1024;
    private final Wallet wallet;
    int triesLeft = 5;
    private Listener listener = null;

    public MoneroHandlerThread(String name, Listener listener, Wallet wallet) {
        super(null, null, name, THREAD_STACK_SIZE);
        this.listener = listener;
        this.wallet = wallet;
    }

    @Override
    public synchronized void start() {
        super.start();
        this.listener.onRefresh(BlockchainService.GETHEIGHT_FETCH);
    }

    @Override
    public void run() {
        PrefService prefService = PrefService.getInstance();
        boolean usesTor = prefService.getBoolean(Constants.PREF_USES_TOR, false);
        String currentNodeString = prefService.getNode().toNodeString();
        Node selectedNode = Node.fromString(currentNodeString);
        boolean isLocalIp = currentNodeString.startsWith("10.") || currentNodeString.startsWith("192.168.") || currentNodeString.equals("localhost") || currentNodeString.equals("127.0.0.1");
        if (usesTor && !isLocalIp) {
            String proxy = prefService.getProxy();
            WalletManager.getInstance().setProxy(proxy);
            wallet.setProxy(proxy);
        }
        WalletManager.getInstance().setDaemon(selectedNode);
        wallet.init(0);
        wallet.setListener(this);
        wallet.startRefresh();
    }

    @Override
    public void moneySpent(String txId, long amount) {
    }

    @Override
    public void moneyReceived(String txId, long amount) {
    }

    @Override
    public void unconfirmedMoneyReceived(String txId, long amount) {
    }

    @Override
    public void newBlock(long height) {
        if(isEveryNthBlock(height, 100)) { // refresh services every 100 blocks downloaded
            refresh(false, height);
        } else if(isEveryNthBlock(height, 2160)) { // save wallet every 2160 blocks (~3 days)
            wallet.store();
        }
        BlockchainService.getInstance().setDaemonHeight(wallet.isSynchronized() ? height : 0);
    }

    @Override
    public void updated() {
        refresh(false, BlockchainService.GETHEIGHT_FETCH);
    }

    @Override
    public void refreshed() {
        Wallet.ConnectionStatus status = wallet.getFullStatus().getConnectionStatus();
        if (status == Wallet.ConnectionStatus.ConnectionStatus_Disconnected || status == null) {
            if (triesLeft > 0) {
                wallet.startRefresh();
                triesLeft--;
            } else {
                listener.onConnectionFail();
            }
        } else {
            long height = wallet.getDaemonBlockChainHeight();
            BlockchainService.getInstance().setDaemonHeight(height);
            wallet.setSynchronized();
            wallet.store();
            refresh(true, height);
        }

        BlockchainService.getInstance().setConnectionStatus(status);
    }

    private void refresh(boolean refreshCoins, long height) {
        wallet.refreshHistory();
        if (refreshCoins) {
            wallet.refreshCoins();
        }
        listener.onRefresh(height);
    }

    public PendingTransaction createTx(String address, String amountStr, boolean sendAll, PendingTransaction.Priority feePriority, ArrayList<String> selectedUtxos) throws Exception {
        long amount = Wallet.getAmountFromString(amountStr);
        ArrayList<String> preferredInputs;
        if (selectedUtxos.isEmpty()) {
            // no inputs manually selected, we are sending from home screen most likely, or user somehow broke the app
            preferredInputs = UTXOService.getInstance().selectUtxos(amount, sendAll);
        } else {
            preferredInputs = selectedUtxos;
            checkSelectedAmounts(preferredInputs, amount, sendAll);
        }

        if(sendAll) {
            return wallet.createSweepTransaction(address, feePriority, preferredInputs);
        }

        ArrayList<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(new TransactionOutput(address, amount));
        List<TransactionOutput> finalOutputs = maybeAddDonationOutputs(amount, outputs, preferredInputs);
        return wallet.createTransactionMultDest(finalOutputs, feePriority, preferredInputs);
    }

    private List<TransactionOutput> maybeAddDonationOutputs(long amount, List<TransactionOutput> outputs, List<String> preferredInputs) throws Exception {
        TransactionOutput mainDestination = outputs.get(0); // at this point, for now, we should only have one item in the list. TODO: add multi-dest/pay-to-many feature in the UI
        String paymentId = Wallet.getPaymentIdFromAddress(mainDestination.getDestination(), WalletManager.getInstance().getNetworkType().getValue());
        ArrayList<TransactionOutput> newOutputs = new ArrayList<>(outputs);
        boolean donatePerTx = PrefService.getInstance().getBoolean(Constants.PREF_DONATE_PER_TX, false);
        if(donatePerTx && paymentId.isEmpty()) { // only attach donation when no payment id is needed (i.e. integrated address)
            SecureRandom rand = new SecureRandom();
            float randomDonatePct = getRandomDonateAmount(0.005f, 0.015f); // occasionally attaches a 0.5% to 1.5% donation. It is random so that not even I know how much exactly you are sending.
            /*
            It's also not entirely "per tx". It won't always attach it so as to not have a consistently uncommon fingerprint on-chain. When it does attach a donation,
            it will periodically split it up into multiple outputs instead of one.
             */
            int attachDonationRoll = rand.nextInt(100);
            if(attachDonationRoll > 90) { // 10% chance of being added
                int splitDonationRoll = rand.nextInt(100);
                long donateAmount = (long) (amount*randomDonatePct);
                if(splitDonationRoll > 50) { // 50% chance of being split
                    // split
                    int split = genRandomDonationSplit(1, 4); // splits into at most 4 outputs, for a total of 6 outputs in the transaction (real dest + change. we don't add donations to send-all/sweep transactions)
                    long splitAmount = donateAmount / split;
                    for(int i = 0; i < split; i++) {
                        // TODO this can be expanded upon into the future to perform an auto-splitting/auto-churning for the user if their wallet is fresh and has few utxos.
                        // randomly split between multiple wallets
                        int randomDonationAddress = rand.nextInt(Constants.DONATION_ADDRESSES.length);
                        String donationAddress = Constants.DONATION_ADDRESSES[randomDonationAddress];
                        newOutputs.add(new TransactionOutput(donationAddress, splitAmount));
                    }
                } else {
                    // just add one output, for a total of 3 (real dest + change)
                    newOutputs.add(new TransactionOutput(Constants.DONATE_ADDRESS, donateAmount));
                }
                long total = amount + donateAmount;
                checkSelectedAmounts(preferredInputs, total, false); // check that the selected UTXOs satisfy the new amount total
            }
        }

        Collections.shuffle(newOutputs); // shuffle the outputs just in case. i think the monero library handles this for us anyway

        return newOutputs;
    }

    private void checkSelectedAmounts(List<String> selectedUtxos, long amount, boolean sendAll) throws Exception {
        if (!sendAll) {
            long amountSelected = 0;
            for (CoinsInfo coinsInfo : UTXOService.getInstance().getUtxos()) {
                if (selectedUtxos.contains(coinsInfo.getKeyImage())) {
                    amountSelected += coinsInfo.getAmount();
                }
            }

            if (amountSelected <= amount) {
                throw new Exception("insufficient wallet balance");
            }
        }
    }

    public boolean sendTx(PendingTransaction pendingTx) {
        return pendingTx.commit("", true);
    }

    private boolean isEveryNthBlock(long height, long interval) {
        return height % interval == 0;
    }

    private float getRandomDonateAmount(float min, float max) {
        SecureRandom rand = new SecureRandom();
        return rand.nextFloat() * (max - min) + min;
    }

    private int genRandomDonationSplit(int min, int max) {
        SecureRandom rand = new SecureRandom();
        return rand.nextInt(max) + min;
    }

    public interface Listener {
        void onRefresh(long height);

        void onConnectionFail();
    }
}
