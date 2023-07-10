/*
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

package net.mynero.wallet.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import net.mynero.wallet.R;
import net.mynero.wallet.model.CoinsInfo;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.service.PrefService;
import net.mynero.wallet.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class CoinsInfoAdapter extends RecyclerView.Adapter<CoinsInfoAdapter.ViewHolder> {

    private List<CoinsInfo> localDataSet; // <public-key, coinsinfo>
    private final HashMap<String, CoinsInfo> selectedUtxos; // <public-key, coinsinfo>
    private CoinsInfoAdapterListener listener = null;
    private boolean editing = false;

    /**
     * Initialize the dataset of the Adapter.
     */
    public CoinsInfoAdapter(CoinsInfoAdapterListener listener) {
        this.listener = listener;
        this.localDataSet = new ArrayList<>();
        this.selectedUtxos = new HashMap<>();
    }

    public void submitList(HashMap<String, CoinsInfo> dataSet) {
        this.localDataSet = new ArrayList<>(dataSet.values());
        notifyDataSetChanged();
    }

    public void deselectUtxo(CoinsInfo coinsInfo) {
        this.selectedUtxos.remove(coinsInfo.getPubKey());

        if(this.selectedUtxos.size() == 0) {
            editing = false;
        }

        notifyDataSetChanged();
    }

    public void selectUtxo(CoinsInfo coinsInfo) {
        editing = true;
        this.selectedUtxos.put(coinsInfo.getPubKey(), coinsInfo);
        notifyDataSetChanged();
    }

    public boolean contains(CoinsInfo coinsInfo) {
        return selectedUtxos.containsKey(coinsInfo.getPubKey());
    }

    public void clear() {
        this.selectedUtxos.clear();
        editing = false;
        notifyDataSetChanged();
    }

    public HashMap<String, CoinsInfo> getSelectedUtxos() {
        return selectedUtxos;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.utxo_selection_item, viewGroup, false);

        return new ViewHolder(listener, view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        CoinsInfo tx = localDataSet.get(position);
        viewHolder.bind(editing, tx, selectedUtxos);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return localDataSet.size();
    }

    public interface CoinsInfoAdapterListener {
        void onUtxoSelected(CoinsInfo coinsInfo);
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private CoinsInfo coinsInfo;
        private boolean editing = false;
        private final CoinsInfoAdapterListener listener;

        public ViewHolder(CoinsInfoAdapterListener listener, View view) {
            super(view);
            this.listener = listener;
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
        }

        public void bind(boolean editing, CoinsInfo coinsInfo, HashMap<String, CoinsInfo> selectedUtxos) {
            this.editing = editing;
            this.coinsInfo = coinsInfo;
            boolean selected = selectedUtxos.containsKey(coinsInfo.getPubKey());

            TextView pubKeyTextView = itemView.findViewById(R.id.utxo_pub_key_textview);
            TextView amountTextView = itemView.findViewById(R.id.utxo_amount_textview);
            TextView addressTextView = itemView.findViewById(R.id.utxo_address_textview);
            TextView globalIdxTextView = itemView.findViewById(R.id.utxo_global_index_textview);
            TextView outpointTextView = itemView.findViewById(R.id.utxo_outpoint_textview);
            boolean streetModeEnabled = PrefService.getInstance().getBoolean(Constants.PREF_STREET_MODE, false);
            String balanceString = streetModeEnabled ? Constants.STREET_MODE_BALANCE : Wallet.getDisplayAmount(coinsInfo.getAmount());
            amountTextView.setText(itemView.getResources().getString(R.string.tx_amount_no_prefix, balanceString));
            pubKeyTextView.setText(coinsInfo.getPubKey());
            addressTextView.setText(coinsInfo.getAddress());
            globalIdxTextView.setText(itemView.getResources().getString(R.string.global_index_text, coinsInfo.getGlobalOutputIndex()));
            outpointTextView.setText(itemView.getResources().getString(R.string.outpoint_text, coinsInfo.getHash() + ":" + coinsInfo.getLocalOutputIndex()));
            if (selected) {
                itemView.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.oled_colorSecondary));
            } else if(coinsInfo.isFrozen()) {
                itemView.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.oled_frozen_utxo));
            } else if (!coinsInfo.isUnlocked()) {
                itemView.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.oled_locked_utxo));
            } else {
                itemView.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.oled_dialogBackgroundColor));
            }
        }

        @Override
        public void onClick(View view) {
            if(!editing) return;
            boolean unlocked = coinsInfo.isUnlocked();
            if (unlocked) {
                listener.onUtxoSelected(coinsInfo);
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if(editing) return false;
            boolean unlocked = coinsInfo.isUnlocked();
            if (unlocked) {
                listener.onUtxoSelected(coinsInfo);
            }
            return unlocked;
        }

        @Override
        public boolean onLongClickUseDefaultHapticFeedback(@NonNull View v) {
            return View.OnLongClickListener.super.onLongClickUseDefaultHapticFeedback(v);
        }
    }
}

