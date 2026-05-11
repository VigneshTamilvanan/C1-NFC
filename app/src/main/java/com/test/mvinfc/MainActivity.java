package com.test.mvinfc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.eze.api.EzeAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String TAG = "NfcTest";

    private static final int REQUEST_CODE_INITIALIZE = 10001;
    private static final int REQUEST_CODE_PAY        = 10016;

    private static final String DEMO_APP_KEY  = "f249e904-1935-4d7a-be63-2a318d6145e7";
    private static final String MERCHANT_NAME = "MOVING_TECH_INNOVATIONS";
    private static final String USER_NAME     = "1411001148";
    private static final String BACKEND_URL   = "https://beautiful-serenity-production-7473.up.railway.app/api/transit/tap";

    // Route → ordered stops (from Chennai GTFS)
    private static final Map<String, List<String>> ROUTE_STOPS = new LinkedHashMap<>();
    static {
        ROUTE_STOPS.put("21 — Royapuram ↔ Guindy", Arrays.asList(
                "ROYAPURAM B.S", "PARRYS CORNER", "M.G.R.CENTRAL", "GOVT ESTATE METRO R.S",
                "WESLEY H.S.S", "Y.M.I.A", "MANDAVELI", "ADYAR GATE",
                "KOTTURPURAM", "ANNA UNIVERSITY", "GUINDY TVK ESTATE"
        ));
        ROUTE_STOPS.put("15 — Island Ground ↔ Koyambedu", Arrays.asList(
                "Island Ground", "PARK STATION", "EGMORE HOTEL EVEREST", "DASAPRAKASH",
                "KMC HOSPITAL", "TAYLORS ROAD", "PACHAIYAPPAS COLLEGE",
                "AMINJIKARAI", "ARUMBAKKAM POST OFFICE", "KOYAMBEDU SCHOOL",
                "M.G.R.KOYAMBEDU B.T"
        ));
        ROUTE_STOPS.put("M70 — Koyambedu ↔ Thiruvanmiyur", Arrays.asList(
                "M.G.R.KOYAMBEDU B.T", "VADAPALANI TEMPLE", "ASHOK PILLAR",
                "KASI THEATRE", "EKKATTUTHANGAL", "GUINDY B.T",
                "VELACHERY CHECK POST", "VELACHERY", "THARAMANI PILLAIYAR TEMPLE",
                "TIDEL PARK", "THIRUVANMIYUR B.T"
        ));
        ROUTE_STOPS.put("47 — Besant Nagar ↔ ICF", Arrays.asList(
                "BESANT NAGAR B.T", "ADYAR DEPOT", "MADHYA KAILASH",
                "ANNA UNIVERSITY", "SAIDAPET", "THYAGARAYA NAGAR BUS TERMINUS",
                "T.NAGAR JEEVA PARK", "VALLUVAR KOTTAM", "NUNGAMBAKKAM RAILWAY STATION",
                "AMINJIKARAI", "ANNA NAGAR 14 SHOP COMPLEX", "ICF"
        ));
        ROUTE_STOPS.put("70 — Avadi ↔ Tambaram", Arrays.asList(
                "AVADI B.T", "AMBATTUR O.T B.T", "AMBATTUR INDUSTRIAL ESTATE B.T",
                "PADI LUCAS T.V.S", "ANNA NAGAR WEST DEPOT", "M.G.R.KOYAMBEDU B.T",
                "VADAPALANI TEMPLE", "EKKATTUTHANGAL", "GUINDY CIPET",
                "ALANDUR METRO R.S", "PALLAVARAM BUS STAND", "CHROMEPET",
                "TAMBARAM WEST BUS STAND"
        ));
    }

    // Screens
    private View screenTripSetup, screenTap, screenResult;

    // Screen 1
    private Spinner spinnerRoute, spinnerSource, spinnerDest;
    private EditText inputFare;
    private Button btnStartCollection;

    // Screen 2
    private TextView tapRoute, tapJourney, tapFare;
    private Button btnPay, btnEditTrip;

    // Screen 3
    private TextView resultStatus, resultTicketNo, resultTxnId, resultJourney;
    private Button btnNextPassenger;

    // Trip state
    private String route, source, dest, fare;

    private boolean sdkInitialised = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        screenTripSetup  = findViewById(R.id.screenTripSetup);
        screenTap        = findViewById(R.id.screenTap);
        screenResult     = findViewById(R.id.screenResult);

        spinnerRoute     = findViewById(R.id.spinnerRoute);
        spinnerSource    = findViewById(R.id.spinnerSource);
        spinnerDest      = findViewById(R.id.spinnerDest);
        inputFare        = findViewById(R.id.inputFare);
        btnStartCollection = findViewById(R.id.btnStartCollection);

        tapRoute   = findViewById(R.id.tapRoute);
        tapJourney = findViewById(R.id.tapJourney);
        tapFare    = findViewById(R.id.tapFare);
        btnPay     = findViewById(R.id.btnPay);
        btnEditTrip = findViewById(R.id.btnEditTrip);

        resultStatus     = findViewById(R.id.resultStatus);
        resultTicketNo   = findViewById(R.id.resultTicketNo);
        resultTxnId      = findViewById(R.id.resultTxnId);
        resultJourney    = findViewById(R.id.resultJourney);
        btnNextPassenger = findViewById(R.id.btnNextPassenger);

        setupRouteSpinner();

        btnStartCollection.setOnClickListener(v -> onStartCollection());
        btnPay.setOnClickListener(v -> onPayNow());
        btnEditTrip.setOnClickListener(v -> showScreen(1));
        btnNextPassenger.setOnClickListener(v -> showScreen(2));

        showScreen(1);
        initSdk();
    }

    private void setupRouteSpinner() {
        String[] routeNames = ROUTE_STOPS.keySet().toArray(new String[0]);
        ArrayAdapter<String> routeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, routeNames);
        routeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRoute.setAdapter(routeAdapter);

        spinnerRoute.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                updateStopSpinners(routeNames[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        updateStopSpinners(routeNames[0]);
    }

    private void updateStopSpinners(String routeName) {
        List<String> stops = ROUTE_STOPS.get(routeName);
        if (stops == null) return;

        ArrayAdapter<String> stopAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, stops);
        stopAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerSource.setAdapter(stopAdapter);
        spinnerDest.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, stops));
        ((ArrayAdapter) spinnerDest.getAdapter())
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerSource.setSelection(0);
        spinnerDest.setSelection(stops.size() - 1);
    }

    private void onStartCollection() {
        route  = spinnerRoute.getSelectedItem().toString();
        source = spinnerSource.getSelectedItem().toString();
        dest   = spinnerDest.getSelectedItem().toString();
        fare   = inputFare.getText().toString().trim();

        if (fare.isEmpty()) {
            inputFare.setError("Required");
            return;
        }
        if (source.equals(dest)) {
            inputFare.setError(null);
            spinnerSource.requestFocus();
            return;
        }

        // Extract short route number (before " — ")
        String shortRoute = route.contains(" — ") ? route.split(" — ")[0] : route;
        tapRoute.setText("Route " + shortRoute);
        tapJourney.setText(source + " → " + dest);
        tapFare.setText("₹" + fare);
        showScreen(2);
    }

    private void initSdk() {
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
            Log.d(TAG, "initSdk: " + req);
            EzeAPI.initialize(this, REQUEST_CODE_INITIALIZE, req);
        } catch (JSONException e) {
            Log.e(TAG, "initSdk error", e);
        }
    }

    private void onPayNow() {
        if (!sdkInitialised) {
            Log.w(TAG, "SDK not ready — retrying init");
            initSdk();
            return;
        }
        btnPay.setEnabled(false);
        try {
            String shortRoute = route.contains(" — ") ? route.split(" — ")[0] : route;
            JSONObject refs = new JSONObject();
            refs.put("reference1", "C1-" + System.currentTimeMillis());
            refs.put("reference2", shortRoute);
            refs.put("reference3", source + " to " + dest);

            JSONObject options = new JSONObject();
            options.put("references", refs);

            JSONObject req = new JSONObject();
            req.put("amount",  fare);
            req.put("options", options);

            Log.d(TAG, "pay(): " + req.toString(2));
            EzeAPI.pay(this, REQUEST_CODE_PAY, req);
        } catch (JSONException e) {
            Log.e(TAG, "pay error", e);
            btnPay.setEnabled(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult req=" + requestCode + " result=" + resultCode);
        if (data != null && data.getExtras() != null) {
            for (String key : data.getExtras().keySet())
                Log.d(TAG, "  [" + key + "] = " + data.getExtras().get(key));
        }

        if (requestCode == REQUEST_CODE_INITIALIZE) {
            sdkInitialised = (resultCode == RESULT_OK);
            Log.d(TAG, "SDK init " + (sdkInitialised ? "OK" : "FAILED"));
            if (sdkInitialised && screenTap.getVisibility() == View.VISIBLE) {
                tapFare.setText("₹" + fare);
                btnPay.setEnabled(true);
            }
            return;
        }

        if (requestCode == REQUEST_CODE_PAY) {
            btnPay.setEnabled(true);
            String responseJson = data != null ? data.getStringExtra("response") : null;
            if (resultCode == RESULT_OK) {
                handlePaymentSuccess(responseJson);
            } else {
                handlePaymentFailure(responseJson);
            }
        }
    }

    private void handlePaymentSuccess(String responseJson) {
        String txnId = extractTxnId(responseJson);
        executor.execute(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                        Locale.US).format(new Date());
                String shortRoute = route.contains(" — ") ? route.split(" — ")[0] : route;
                JSONObject body = new JSONObject();
                body.put("txnId",       txnId);
                body.put("route",       shortRoute);
                body.put("source",      source);
                body.put("destination", dest);
                body.put("fare",        fare);
                body.put("timestamp",   timestamp);
                Log.d(TAG, "POST backend: " + body.toString(2));

                URL url = new URL(BACKEND_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) { os.write(payload); }

                int code = conn.getResponseCode();
                java.io.InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream() : conn.getErrorStream();
                String resp = new java.util.Scanner(is).useDelimiter("\\A").next();
                Log.d(TAG, "Backend response " + code + ": " + resp);

                if (code == 200 || code == 201) {
                    JSONObject respObj = new JSONObject(resp);
                    String ticketNo = respObj.optString("ticketNo", "TK-?????");
                    uiHandler.post(() -> showResultScreen(true, null, ticketNo, txnId));
                } else {
                    uiHandler.post(() -> showResultScreen(false, "Backend error " + code, null, txnId));
                }
            } catch (Exception e) {
                Log.e(TAG, "Backend error", e);
                uiHandler.post(() -> showResultScreen(false, "Backend error", null, null));
            }
        });
    }

    private void handlePaymentFailure(String responseJson) {
        String errorMsg = "Payment failed";
        String errorCode = "";
        if (responseJson != null) {
            try {
                JSONObject root = new JSONObject(responseJson);
                JSONObject err = root.optJSONObject("error");
                if (err != null) {
                    errorMsg = err.optString("message", errorMsg);
                    errorCode = err.optString("code", "");
                }
                // also check top-level externalError
                String ext = root.optString("externalError", "");
                if (!ext.isEmpty()) errorCode = ext;
            } catch (JSONException ignored) {}
        }
        Log.w(TAG, "Payment failed code=" + errorCode + " msg=" + errorMsg);

        boolean isSessionError = errorCode.contains("SESSION") || errorCode.contains("LOGIN")
                || errorMsg.toLowerCase().contains("login") || errorMsg.toLowerCase().contains("session");

        if (isSessionError) {
            sdkInitialised = false;
            Log.w(TAG, "Session lost — reinitialising SDK");
            initSdk();
            // Go back to tap screen so user can retry immediately after reinit
            uiHandler.postDelayed(() -> {
                btnPay.setEnabled(true);
                showScreen(2);
            }, 2500);
            // Brief toast-style feedback via tapFare field
            tapFare.setText("Re-logging in…");
        } else {
            showResultScreen(false, errorMsg, null, null);
        }
    }

    private void showResultScreen(boolean success, String errorMsg, String ticketNo, String txnId) {
        String shortRoute = route.contains(" — ") ? route.split(" — ")[0] : route;
        if (success) {
            resultStatus.setText("✅ TICKET ISSUED");
            resultStatus.setTextColor(0xFF2E7D32);
            resultTicketNo.setText("Ticket #: " + ticketNo);
            resultTxnId.setText("Txn: " + (txnId != null ? txnId : "—"));
            resultJourney.setText(shortRoute + "  |  " + source + " → " + dest + "  |  ₹" + fare);
        } else {
            resultStatus.setText("❌ " + errorMsg);
            resultStatus.setTextColor(0xFFC62828);
            resultTicketNo.setText("");
            resultTxnId.setText("");
            resultJourney.setText(shortRoute + "  |  " + source + " → " + dest + "  |  ₹" + fare);
        }
        showScreen(3);
    }

    private String extractTxnId(String responseJson) {
        if (responseJson == null) return "unknown";
        try {
            JSONObject result = new JSONObject(responseJson).optJSONObject("result");
            if (result != null) {
                for (String key : new String[]{"txnId","transactionId","id"}) {
                    String v = result.optString(key, null);
                    if (v != null && !v.isEmpty()) return v;
                }
            }
        } catch (JSONException ignored) {}
        return "TXN-" + System.currentTimeMillis();
    }

    private void showScreen(int screen) {
        screenTripSetup.setVisibility(screen == 1 ? View.VISIBLE : View.GONE);
        screenTap.setVisibility(screen == 2 ? View.VISIBLE : View.GONE);
        screenResult.setVisibility(screen == 3 ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
