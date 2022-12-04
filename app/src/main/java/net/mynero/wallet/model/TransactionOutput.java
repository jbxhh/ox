package net.mynero.wallet.model;

import net.mynero.wallet.service.PrefService;
import net.mynero.wallet.util.Constants;

public class TransactionOutput {
    private final String destination;
    private final long amount;

    public TransactionOutput(String destination, long amount) {
        this.destination = destination;
        this.amount = amount;
    }

    public String getDestination() {
        return destination;
    }

    public long getAmount() {
        return amount;
    }
}