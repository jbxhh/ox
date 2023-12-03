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
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import timber.log.Timber;

public class EditNodeBottomSheetDialog extends BottomSheetDialogFragment {
    public EditNodeListener listener = null;
    public JSONObject nodeJson = null;

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
        EditText portEditText = view.findViewById(R.id.node_port_edittext);
        EditText nodeNameEditText = view.findViewById(R.id.node_name_edittext);
        EditText usernameEditText = view.findViewById(R.id.username_edittext);
        EditText passwordEditText = view.findViewById(R.id.password_edittext);
        ImageButton pastePasswordImageButton = view.findViewById(R.id.paste_password_imagebutton);

        Node node = Node.fromJson(nodeJson);
        if(node == null) return;
        addressEditText.setText(node.getHost());
        portEditText.setText(""+node.getRpcPort());
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
            listener.onNodeDeleted(Node.fromJson(nodeJson));
            dismiss();
        });
        doneEditingButton.setOnClickListener(view1 -> {
            String nodeAddr = addressEditText.getText().toString();
            String portString = portEditText.getText().toString();
            String nodeName = nodeNameEditText.getText().toString();
            String user = usernameEditText.getText().toString();
            String pass = passwordEditText.getText().toString();

            if(nodeName.isEmpty()) {
                Toast.makeText(getContext(), "Enter node name", Toast.LENGTH_SHORT).show();
                return;
            } else if(nodeAddr.isEmpty() || portString.isEmpty()) {
                Toast.makeText(getContext(), "Enter node address", Toast.LENGTH_SHORT).show();
                return;
            } else if(!user.isEmpty() && pass.isEmpty()) {
                Toast.makeText(getContext(), "Enter password", Toast.LENGTH_SHORT).show();
                return;
            }
            JSONObject jsonObject = new JSONObject();
            try {
                if (!user.isEmpty()) {
                    jsonObject.put("username", user);
                    jsonObject.put("password", pass);
                }

                jsonObject.put("host", nodeAddr);
                jsonObject.put("rpcPort", Integer.parseInt(portString));
                jsonObject.put("network", "mainnet");
                jsonObject.put("name", nodeName);

                listener.onNodeEdited(Node.fromJson(nodeJson), Node.fromJson(jsonObject));
                dismiss();
            } catch (JSONException e) {
                throw new RuntimeException(e);
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

    public interface EditNodeListener {
        void onNodeDeleted(Node node);
        void onNodeEdited(Node oldNode, Node newNode);
    }
}