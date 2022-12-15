package net.mynero.wallet.fragment.dialog;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import net.mynero.wallet.R;
import net.mynero.wallet.data.Node;
import net.mynero.wallet.service.PrefService;
import net.mynero.wallet.util.Constants;
import net.mynero.wallet.util.Helper;

import org.json.JSONArray;
import org.json.JSONException;

public class AddNodeBottomSheetDialog extends BottomSheetDialogFragment {
    public AddNodeListener listener = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.add_node_bottom_sheet_dialog, null);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Button addNodeButton = view.findViewById(R.id.add_node_button);
        EditText addressEditText = view.findViewById(R.id.address_edittext);
        EditText nodeNameEditText = view.findViewById(R.id.node_name_edittext);
        EditText usernameEditText = view.findViewById(R.id.username_edittext);
        EditText passwordEditText = view.findViewById(R.id.password_edittext);
        ImageButton pastePasswordImageButton = view.findViewById(R.id.paste_password_imagebutton);

        usernameEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override
            public void afterTextChanged(Editable editable) {
                if(editable.toString().isEmpty()) {
                    passwordEditText.setText(null);
                    passwordEditText.setVisibility(View.GONE);
                    pastePasswordImageButton.setVisibility(View.GONE);
                } else {
                    passwordEditText.setVisibility(View.VISIBLE);
                    pastePasswordImageButton.setVisibility(View.VISIBLE);
                }
            }
        });

        addPasteListener(view, addressEditText, R.id.paste_address_imagebutton);
        addPasteListener(view, usernameEditText, R.id.paste_username_imagebutton);
        addPasteListener(view, passwordEditText, R.id.paste_password_imagebutton);

        addNodeButton.setOnClickListener(view1 -> {
            String node = addressEditText.getText().toString();
            String name = nodeNameEditText.getText().toString();
            String user = usernameEditText.getText().toString();
            String pass = passwordEditText.getText().toString();
            try {
                String auth = "";
                if(!user.isEmpty() && !pass.isEmpty()) {
                    auth = user + ":" + pass + "@";
                } else if(!user.isEmpty()) {
                    Toast.makeText(getContext(), "Enter password", Toast.LENGTH_SHORT).show();
                    return;
                }

                StringBuilder nodeStringBuilder = new StringBuilder();
                nodeStringBuilder.append(auth);

                if(!name.isEmpty()) {
                    if(!node.isEmpty()) {
                        if (node.contains(":")) {
                            String[] nodeParts = node.split(":");
                            if (nodeParts.length == 2) {
                                String address = nodeParts[0];
                                int port = Integer.parseInt(nodeParts[1]);
                                nodeStringBuilder.append(address).append(":").append(port);
                            }
                        } else {
                            nodeStringBuilder.append(node);
                        }

                        nodeStringBuilder.append("/mainnet/").append(name);
                    } else {
                        Toast.makeText(getContext(), "Enter node address", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else {
                    Toast.makeText(getContext(), "Enter node name", Toast.LENGTH_SHORT).show();
                    return;
                }

                String nodesArray = PrefService.getInstance().getString(Constants.PREF_CUSTOM_NODES, "[]");
                JSONArray jsonArray = new JSONArray(nodesArray);
                boolean exists = false;
                for (int i = 0; i < jsonArray.length(); i++) {
                    String nodeString = jsonArray.getString(i);
                    if (nodeString.equals(nodeStringBuilder.toString()))
                        exists = true;
                }

                if (!exists) {
                    jsonArray.put(nodeStringBuilder.toString());
                } else {
                    Toast.makeText(getContext(), "Node already exists", Toast.LENGTH_SHORT).show();
                    return;
                }

                PrefService.getInstance().edit().putString(Constants.PREF_CUSTOM_NODES, jsonArray.toString()).apply();
                if (listener != null) {
                    listener.onNodeAdded();
                }
            } catch (NumberFormatException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void addPasteListener(View root, EditText editText, int layoutId) {
        ImageButton pasteImageButton = root.findViewById(layoutId);
        pasteImageButton.setOnClickListener(view1 -> {
            Context ctx = getContext();
            if (ctx != null) {
                editText.setText(Helper.getClipBoardText(ctx));
            }
        });
    }

    public interface AddNodeListener {
        void onNodeAdded();
    }
}