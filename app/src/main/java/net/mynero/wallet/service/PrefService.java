package net.mynero.wallet.service;

import android.content.Context;
import android.content.SharedPreferences;

import net.mynero.wallet.MoneroApplication;
import net.mynero.wallet.data.DefaultNodes;
import net.mynero.wallet.data.Node;
import net.mynero.wallet.util.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PrefService extends ServiceBase {
    private static SharedPreferences preferences = null;
    private static PrefService instance = null;

    public PrefService(MoneroApplication application) {
        super(null);
        preferences = application.getSharedPreferences(application.getApplicationInfo().packageName, Context.MODE_PRIVATE);
        instance = this;
    }

    public SharedPreferences.Editor edit() {
        return preferences.edit();
    }

    public Node getNode() {
        boolean usesProxy = getBoolean(Constants.PREF_USES_TOR, false);
        DefaultNodes defaultNode = DefaultNodes.SAMOURAI;
        if(usesProxy) {
            String proxyPort = getProxyPort();
            if (!proxyPort.isEmpty()) {
                int port = Integer.parseInt(proxyPort);
                if(port == 4447) {
                    defaultNode = DefaultNodes.MYNERO_I2P;
                } else {
                    defaultNode = DefaultNodes.MYNERO_ONION;
                }
            }
        }
        String nodeString = getString(Constants.PREF_NODE_2, defaultNode.getNodeString());
        try {
            JSONObject nodeJson = new JSONObject(nodeString);
            return Node.fromJson(nodeJson);
        } catch (JSONException e) {
            if(!nodeString.isEmpty()) {
                Node node = Node.fromString(nodeString);
                if(node != null) {
                    edit().putString(Constants.PREF_NODE_2, node.toJson().toString()).apply();
                    return node;
                }
            }
        }
        return null;
    }

    public String getProxy() {
        return PrefService.getInstance().getString(Constants.PREF_PROXY, "");
    }

    public boolean hasProxySet() {
        String proxyString = getProxy();
        return proxyString.contains(":");
    }

    public String getProxyAddress() {
        if (hasProxySet()) {
            String proxyString = getProxy();
            return proxyString.split(":")[0];
        }
        return "";
    }

    public String getProxyPort() {
        if (hasProxySet()) {
            String proxyString = getProxy();
            return proxyString.split(":")[1];
        }
        return "";
    }

    public String getString(String key, String defaultValue) {
        String value = preferences.getString(key, "");
        if(value.isEmpty() && !defaultValue.isEmpty()) {
            edit().putString(key, defaultValue).apply();
            return defaultValue;
        }
        return value;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        boolean containsKey = preferences.contains(key);
        boolean value = preferences.getBoolean(key, false);
        if(!value && defaultValue && !containsKey) {
            edit().putBoolean(key, true).apply();
            return true;
        }
        return value;
    }

    public static PrefService getInstance() {
        return instance;
    }
}
