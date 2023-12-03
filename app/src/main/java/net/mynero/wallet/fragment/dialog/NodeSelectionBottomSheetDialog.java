package net.mynero.wallet.fragment.dialog;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import net.mynero.wallet.R;
import net.mynero.wallet.adapter.NodeSelectionAdapter;
import net.mynero.wallet.data.DefaultNodes;
import net.mynero.wallet.data.Node;
import net.mynero.wallet.model.WalletManager;
import net.mynero.wallet.service.PrefService;
import net.mynero.wallet.util.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class NodeSelectionBottomSheetDialog extends BottomSheetDialogFragment implements NodeSelectionAdapter.NodeSelectionAdapterListener {
    public NodeSelectionDialogListener listener = null;
    private NodeSelectionAdapter adapter = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.node_selection_bottom_sheet_dialog, null);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ArrayList<Node> nodes = new ArrayList<>();
        adapter = new NodeSelectionAdapter(this);

        RecyclerView recyclerView = view.findViewById(R.id.node_selection_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(adapter);

        Button addNodeButton = view.findViewById(R.id.add_node_button);
        addNodeButton.setOnClickListener(view1 -> {
            if (listener != null) {
                listener.onClickedAddNode();
            }
            dismiss();
        });

        String nodesArray = PrefService.getInstance().getString(Constants.PREF_CUSTOM_NODES, "[]");
        JSONArray jsonArray = null;
        try {
            jsonArray = new JSONArray(nodesArray);
        } catch (JSONException e) { throw new RuntimeException(e); }

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject nodeJsonObject = null;
            try {
                nodeJsonObject = jsonArray.getJSONObject(i);
                if (nodeJsonObject != null) {
                    Node node = Node.fromJson(nodeJsonObject);
                    if (node != null) {
                        nodes.add(node);
                    }
                }
            } catch (JSONException e) {
                // if stored node is old string format, try parse and upgrade, if fail then remove
                try {
                    String nodeString = jsonArray.getString(i);
                    Node node = Node.fromString(nodeString);
                    if (node != null) {
                        nodes.add(node);
                        jsonArray.put(i, node.toJson());
                    } else {
                        jsonArray.remove(i);
                    }
                } catch (JSONException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        PrefService.getInstance().edit().putString(Constants.PREF_CUSTOM_NODES, jsonArray.toString()).apply();

        for (DefaultNodes defaultNode : DefaultNodes.values()) {
            nodes.add(Node.fromJson(defaultNode.getJson()));
        }
        adapter.submitList(nodes);
    }

    @Override
    public void onSelectNode(Node node) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, getString(R.string.node_selected), Toast.LENGTH_SHORT).show();
            });
        }
        PrefService.getInstance().edit().putString(Constants.PREF_NODE_2, node.toJson().toString()).apply();
        WalletManager.getInstance().setDaemon(node);
        adapter.updateSelectedNode();
        listener.onNodeSelected();
    }

    @Override
    public boolean onSelectEditNode(Node node) {
        if (listener != null) {
            listener.onClickedEditNode(node);
        }
        dismiss();
        return true;
    }

    public interface NodeSelectionDialogListener {
        void onNodeSelected();
        void onClickedEditNode(Node node);
        void onClickedAddNode();
    }
}