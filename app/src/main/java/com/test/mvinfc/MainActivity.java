package com.test.mvinfc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.eze.api.EzeAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

public class MainActivity extends Activity {

    private static final String TAG = "NfcTest";

    private static final int REQUEST_CODE_INITIALIZE = 10001;
    private static final int REQUEST_CODE_PAY        = 10016;

    private static final String DEMO_APP_KEY  = "f249e904-1935-4d7a-be63-2a318d6145e7";
    private static final String MERCHANT_NAME = "MOVING_TECH_INNOVATIONS";
    private static final String USER_NAME     = "1411001148";  // login username — try org code if this fails

    private TextView statusText;
    private TextView resultText;
    private EditText amountInput;
    private Button initBtn;
    private Button payBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText  = findViewById(R.id.statusText);
        resultText  = findViewById(R.id.tagText);
        amountInput = findViewById(R.id.amountInput);
        initBtn     = findViewById(R.id.initBtn);
        payBtn      = findViewById(R.id.payBtn);

        payBtn.setEnabled(false);

        initBtn.setOnClickListener(v -> doInitialize());
        payBtn.setOnClickListener(v -> doPay());
    }

    private void doInitialize() {
        statusText.setText("Initialising SDK...");
        try {
            JSONObject req = new JSONObject();
            req.put("demoAppKey",       DEMO_APP_KEY);
            req.put("prodAppKey",       DEMO_APP_KEY);
            req.put("merchantName",     MERCHANT_NAME);
            req.put("userName",         USER_NAME);
            req.put("currencyCode",     "INR");
            req.put("appMode",          "EZETAP_DEMO");
            req.put("captureSignature", "false");
            req.put("prepareDevice",    "false");
            req.put("captureReceipt",   "false");
            Log.d(TAG, "initialize() request: " + req.toString(2));
            EzeAPI.initialize(this, REQUEST_CODE_INITIALIZE, req);
        } catch (JSONException e) {
            statusText.setText("Init JSON error: " + e.getMessage());
            Log.e(TAG, "Init JSON error", e);
        }
    }

    private void doPay() {
        String amountStr = amountInput.getText().toString().trim();
        if (amountStr.isEmpty()) {
            statusText.setText("Enter amount first");
            return;
        }
        statusText.setText("Launching payment — tap card/phone...");
        try {
            JSONObject refs = new JSONObject();
            refs.put("reference1", "TEST-" + System.currentTimeMillis());

            JSONObject customer = new JSONObject();
            customer.put("name",     "Test User");
            customer.put("mobileNo", "9999999999");
            customer.put("email",    "test@test.com");

            JSONObject options = new JSONObject();
            options.put("references", refs);
            options.put("customer",   customer);

            JSONObject req = new JSONObject();
            req.put("amount",  amountStr);
            req.put("options", options);

            Log.d(TAG, "pay() request: " + req.toString(2));
            EzeAPI.pay(this, REQUEST_CODE_PAY, req);
        } catch (JSONException e) {
            statusText.setText("Pay JSON error: " + e.getMessage());
            Log.e(TAG, "Pay JSON error", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "=== onActivityResult ===");
        Log.d(TAG, "  requestCode=" + requestCode + "  resultCode=" + resultCode);

        if (data == null) {
            Log.w(TAG, "  data Intent is null");
            statusText.setText("No response from Service App (null intent)");
            return;
        }

        // Log every extra in the response intent
        Bundle extras = data.getExtras();
        if (extras != null) {
            Set<String> keys = extras.keySet();
            Log.d(TAG, "  Intent extras (" + keys.size() + " keys):");
            for (String key : keys) {
                Log.d(TAG, "    [" + key + "] = " + extras.get(key));
            }
        } else {
            Log.w(TAG, "  Intent extras bundle is null");
        }

        String responseJson = data.getStringExtra("response");
        Log.d(TAG, "  response extra: " + responseJson);

        // Pretty-print JSON to screen
        String display = responseJson;
        if (responseJson != null) {
            try {
                display = new JSONObject(responseJson).toString(2);
            } catch (JSONException ignored) {}
        }

        if (requestCode == REQUEST_CODE_INITIALIZE) {
            if (resultCode == RESULT_OK) {
                statusText.setText("✅ SDK Initialised — ready to pay");
                payBtn.setEnabled(true);
            } else {
                statusText.setText("❌ Init failed (resultCode=" + resultCode + ")");
            }
            resultText.setText(display);
            return;
        }

        if (requestCode == REQUEST_CODE_PAY) {
            if (resultCode == RESULT_OK) {
                statusText.setText("✅ Payment success!");
            } else {
                statusText.setText("❌ Payment failed/cancelled (resultCode=" + resultCode + ")");
            }
            resultText.setText(display != null ? display : "(no response body)");
        }
    }
}
