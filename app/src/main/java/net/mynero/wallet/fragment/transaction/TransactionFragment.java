package net.mynero.wallet.fragment.transaction;

import static net.mynero.wallet.util.DateHelper.DATETIME_FORMATTER;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import net.mynero.wallet.R;
import net.mynero.wallet.model.TransactionInfo;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.model.WalletManager;
import net.mynero.wallet.service.HistoryService;
import net.mynero.wallet.service.PrefService;
import net.mynero.wallet.util.Constants;
import net.mynero.wallet.util.Helper;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

public class TransactionFragment extends Fragment {

    private TransactionViewModel mViewModel;
    private TransactionInfo transactionInfo = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transaction, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Calendar cal = Calendar.getInstance();
        TimeZone tz = cal.getTimeZone(); //get the local time zone.
        DATETIME_FORMATTER.setTimeZone(tz);

        mViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        Bundle args = getArguments();
        if (args != null) {
            this.transactionInfo = getArguments().getParcelable(Constants.NAV_ARG_TXINFO);
        }

        bindObservers(view);
        bindListeners(view);
    }

    private void bindListeners(View view) {
        ImageButton copyTxHashImageButton = view.findViewById(R.id.copy_txhash_imagebutton);
        copyTxHashImageButton.setOnClickListener(view1 -> {
            TransactionInfo txInfo = this.transactionInfo;
            if (txInfo != null) {
                Helper.clipBoardCopy(getContext(), "transaction_hash", txInfo.hash);
            }
        });

        ImageButton copyTxAddressImageButton = view.findViewById(R.id.copy_txaddress_imagebutton);
        TextView addressTextView = view.findViewById(R.id.transaction_address_textview);
        copyTxAddressImageButton.setOnClickListener(view1 -> {
            TransactionInfo txInfo = this.transactionInfo;
            if (txInfo != null) {
                String destination = addressTextView.getText().toString();
                Helper.clipBoardCopy(getContext(), "transaction_address", destination);
            }
        });
    }

    private void bindObservers(View view) {
        TextView txActionTextView = view.findViewById(R.id.transaction_action_textview);
        TextView confLabel2 = view.findViewById(R.id.transaction_conf_label2_textview);
        TextView txHashTextView = view.findViewById(R.id.transaction_hash_textview);
        TextView txConfTextView = view.findViewById(R.id.transaction_conf_textview);
        TextView txAddressTextView = view.findViewById(R.id.transaction_address_textview);
        ImageButton copyTxAddressImageButton = view.findViewById(R.id.copy_txaddress_imagebutton);
        TextView txDateTextView = view.findViewById(R.id.transaction_date_textview);
        TextView txAmountTextView = view.findViewById(R.id.transaction_amount_textview);
        TextView blockHeightTextView = view.findViewById(R.id.tx_block_height_textview);

        HistoryService.getInstance().history.observe(getViewLifecycleOwner(), transactionInfos -> {
            TransactionInfo newTransactionInfo = findNewestVersionOfTransaction(this.transactionInfo, transactionInfos);
            if(newTransactionInfo == null) return;

            txHashTextView.setText(newTransactionInfo.hash);
            txConfTextView.setText("" + newTransactionInfo.confirmations);
            txDateTextView.setText(getDateTime(newTransactionInfo.timestamp));
            if(newTransactionInfo.confirmations > 1) {
                confLabel2.setText(getString(R.string.transaction_conf_desc2_confirmed));
                blockHeightTextView.setText("" + newTransactionInfo.blockheight);
                blockHeightTextView.setVisibility(View.VISIBLE);
            } else if(newTransactionInfo.confirmations == 1) {
                confLabel2.setText(getString(R.string.transaction_conf_1_desc2_confirmed));
                blockHeightTextView.setText("" + newTransactionInfo.blockheight);
                blockHeightTextView.setVisibility(View.VISIBLE);
            } else {
                blockHeightTextView.setVisibility(View.GONE);
                confLabel2.setText(getString(R.string.transaction_conf_desc2_unconfirmed));
            }

            Context ctx = getContext();
            if(ctx != null) {
                boolean streetModeEnabled = PrefService.getInstance().getBoolean(Constants.PREF_STREET_MODE, false);
                String balanceString = streetModeEnabled ? Constants.STREET_MODE_BALANCE : Helper.getDisplayAmount(newTransactionInfo.amount, 12);
                if (newTransactionInfo.direction == TransactionInfo.Direction.Direction_In) {
                    txActionTextView.setText(getString(R.string.transaction_action_recv));
                    txAmountTextView.setTextColor(ContextCompat.getColor(ctx, R.color.oled_positiveColor));
                } else {
                    txActionTextView.setText(getString(R.string.transaction_action_sent));
                    txAmountTextView.setTextColor(ContextCompat.getColor(ctx, R.color.oled_negativeColor));
                }
                txAmountTextView.setText(balanceString);
            }

            String destination = "-";
            Wallet wallet = WalletManager.getInstance().getWallet();
            if (newTransactionInfo.txKey == null) {
                newTransactionInfo.txKey = wallet.getTxKey(newTransactionInfo.hash);
            }
            if (newTransactionInfo.address == null && newTransactionInfo.direction == TransactionInfo.Direction.Direction_In) {
                destination = wallet.getSubaddress(newTransactionInfo.accountIndex, newTransactionInfo.addressIndex);
            } else if (newTransactionInfo.address != null && newTransactionInfo.direction == TransactionInfo.Direction.Direction_In) {
                destination = newTransactionInfo.address;
            } else if (newTransactionInfo.transfers != null && newTransactionInfo.direction == TransactionInfo.Direction.Direction_Out) {
                if (newTransactionInfo.transfers.size() == 1) {
                    destination = newTransactionInfo.transfers.get(0).address;
                }
            }

            txAddressTextView.setText(Objects.requireNonNullElse(destination, "-"));
            if (destination == null) {
                copyTxAddressImageButton.setVisibility(View.INVISIBLE);
            }
        });
    }

    private TransactionInfo findNewestVersionOfTransaction(TransactionInfo oldTransactionInfo, List<TransactionInfo> transactionInfoList) {
        for(TransactionInfo transactionInfo : transactionInfoList) {
            if(transactionInfo.hash.equals(oldTransactionInfo.hash)) {
                this.transactionInfo = transactionInfo;
                return this.transactionInfo;
            }
        }
        return null;
    }

    private String getDateTime(long time) {
        return DATETIME_FORMATTER.format(new Date(time * 1000));
    }
}