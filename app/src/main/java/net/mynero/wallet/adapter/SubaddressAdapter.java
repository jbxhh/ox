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

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import net.mynero.wallet.R;
import net.mynero.wallet.data.Subaddress;
import net.mynero.wallet.model.CoinsInfo;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.service.PrefService;
import net.mynero.wallet.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class SubaddressAdapter extends RecyclerView.Adapter<SubaddressAdapter.ViewHolder> {

    private List<Subaddress> localDataSet;

    /**
     * Initialize the dataset of the Adapter.
     */
    public SubaddressAdapter() {
        this.localDataSet = new ArrayList<>();
    }

    public void submitList(List<Subaddress> dataSet) {
        System.out.println("ADDRESSES: " + dataSet);
        this.localDataSet = dataSet;
        notifyDataSetChanged();
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.address_item, viewGroup, false);

        return new ViewHolder(view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        Subaddress subaddress = localDataSet.get(position);
        viewHolder.bind(subaddress);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return localDataSet.size();
    }

    public interface SubaddressAdapterListener {
        void onSubaddressSelected(Subaddress subaddress);
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View view) {
            super(view);
        }

        public void bind(Subaddress subaddress) {
            TextView addressTextView = itemView.findViewById(R.id.address_item_address_textview);
            addressTextView.setText(subaddress.getAddress());
        }
    }
}

