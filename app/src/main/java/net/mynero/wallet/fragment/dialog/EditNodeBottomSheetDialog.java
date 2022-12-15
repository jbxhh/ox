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

public class EditNodeBottomSheetDialog extends BottomSheetDialogFragment {
    public EditNodeListener listener = null;
    public String nodeString = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.edit_node_bottom_sheet_dialog, null);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Button deleteNodeButton = view.findViewById(R.id.delete_node_button);
        Button doneEditingButton = view.findViewById(R.id.done_editing_button);
        EditText addressEditText = view.findViewById(R.id.address_edittext);
        EditText nodeNameEditText = view.findViewById(R.id.node_name_edittext);
        EditText usernameEditText = view.findViewById(R.id.username_edittext);
        EditText passwordEditText = view.findViewById(R.id.password_edittext);
        ImageButton pastePasswordImageButton = view.findViewById(R.id.paste_password_imagebutton);

        Node node = Node.fromString(nodeString);
        addressEditText.setText(node.getAddress());
        nodeNameEditText.setText(node.getName());
        usernameEditText.setText(node.getUsername());
        if(!node.getPassword().isEmpty()) {
            passwordEditText.setText(node.getPassword());
            passwordEditText.setVisibility(View.VISIBLE);
            pastePasswordImageButton.setVisibility(View.VISIBLE);
        }

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

        deleteNodeButton.setOnClickListener(view1 -> {
            listener.onNodeDeleted(Node.fromString(nodeString));
            dismiss();
        });
        doneEditingButton.setOnClickListener(view1 -> {
            String nodeAddress = addressEditText.getText().toString();
            String nodeName = nodeNameEditText.getText().toString();
            String user = usernameEditText.getText().toString();
            String pass = passwordEditText.getText().toString();

            String auth = "";
            if(!user.isEmpty() && !pass.isEmpty()) {
                auth = user + ":" + pass + "@";
            } else if(!user.isEmpty()) {
                Toast.makeText(getContext(), "Enter password", Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder nodeStringBuilder = new StringBuilder();
            nodeStringBuilder.append(auth);

            if(!nodeName.isEmpty()) {
                if(!nodeAddress.isEmpty()) {
                    if (nodeAddress.contains(":")) {
                        String[] nodeParts = nodeAddress.split(":");
                        if (nodeParts.length == 2) {
                            String address = nodeParts[0];
                            int port = Integer.parseInt(nodeParts[1]);
                            nodeStringBuilder.append(address).append(":").append(port);
                        }
                    } else {
                        nodeStringBuilder.append(nodeAddress);
                    }

                    nodeStringBuilder.append("/mainnet/").append(nodeName);
                    listener.onNodeEdited(Node.fromString(nodeString), Node.fromString(nodeStringBuilder.toString()));
                } else {
                    Toast.makeText(getContext(), "Enter node address", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                Toast.makeText(getContext(), "Enter node name", Toast.LENGTH_SHORT).show();
                return;
            }
            dismiss();
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

    public interface EditNodeListener {
        void onNodeDeleted(Node node);
        void onNodeEdited(Node oldNode, Node newNode);
    }
}