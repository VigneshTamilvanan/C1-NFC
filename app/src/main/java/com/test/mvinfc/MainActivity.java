package com.test.mvinfc;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.eze.api.EzeAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {

    private static final String TAG = "NfcTest";
    static final String BACKEND_URL = "https://movingtech-etm.onrender.com/api/transit/tap";

    private static final int REQUEST_CODE_INITIALIZE = 10001;
    private static final int REQUEST_CODE_PAY        = 10016;

    private static final String DEMO_APP_KEY  = "f249e904-1935-4d7a-be63-2a318d6145e7";
    private static final String MERCHANT_NAME = "MOVING_TECH_INNOVATIONS";
    private static final String USER_NAME     = "1411001148";

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
    private View screenTripSetup, screenTap, screenResult, screenQr;
    private static final int REQUEST_CODE_SCAN = 10099;
    private androidx.activity.result.ActivityResultLauncher<com.journeyapps.barcodescanner.ScanOptions> qrLauncher;

    // Screen 1
    private Spinner spinnerRoute, spinnerSource, spinnerDest;
    private TextView tvRouteFixed, tvActiveTripBanner;
    private EditText inputFare;
    private Button btnStartCollection;
    private TextView tvConductor;

    // Screen 2
    private TextView tapRoute, tapJourney, tapFare;
    private Button btnPay, btnEditTrip;

    // Screen 3
    private TextView resultStatus, resultTicketNo, resultTxnId, resultJourney;
    private Button btnNextPassenger;

    // Trip state
    private String route, source, dest, fare;
    private int passengerCount = 1;

    private boolean sdkInitialised = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        qrLauncher = registerForActivityResult(new com.journeyapps.barcodescanner.ScanContract(), result -> {
            android.util.Log.d("QR_SCAN", "launcher callback: contents=" + result.getContents());
            if (result.getContents() != null) {
                showScreen(4);
                validateQrContent(result.getContents());
            } else {
                android.util.Log.d("QR_SCAN", "scan cancelled or null");
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
                showScreen(4);
            }
        });

        if (!ConductorAuth.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        screenTripSetup    = findViewById(R.id.screenTripSetup);
        screenTap          = findViewById(R.id.screenTap);
        screenResult       = findViewById(R.id.screenResult);
        screenQr           = findViewById(R.id.screenQr);
        spinnerRoute       = findViewById(R.id.spinnerRoute);
        tvRouteFixed       = findViewById(R.id.tvRouteFixed);
        tvActiveTripBanner = findViewById(R.id.tvActiveTripBanner);
        spinnerSource      = findViewById(R.id.spinnerSource);
        spinnerDest        = findViewById(R.id.spinnerDest);
        inputFare          = findViewById(R.id.inputFare);
        btnStartCollection = findViewById(R.id.btnStartCollection);
        tvConductor        = findViewById(R.id.tvConductor);
        tapRoute           = findViewById(R.id.tapRoute);
        tapJourney         = findViewById(R.id.tapJourney);
        tapFare            = findViewById(R.id.tapFare);
        btnPay             = findViewById(R.id.btnPay);
        btnEditTrip        = findViewById(R.id.btnEditTrip);
        resultStatus       = findViewById(R.id.resultStatus);
        resultTicketNo     = findViewById(R.id.resultTicketNo);
        resultTxnId        = findViewById(R.id.resultTxnId);
        resultJourney      = findViewById(R.id.resultJourney);
        btnNextPassenger   = findViewById(R.id.btnNextPassenger);

        String conductorName = ConductorAuth.getLoggedInName(this);
        String conductorId   = ConductorAuth.getLoggedInId(this);
        tvConductor.setText(conductorId + " — " + conductorName);

        // Trip banner
        String tripLabel = "Trip " + WaybillActivity.activeTripNo + "  |  Route " + WaybillActivity.activeRouteNo;
        tvActiveTripBanner.setText(tripLabel);

        setupRouteSpinner();

        // Auto-select route from waybill
        if (WaybillActivity.activeRouteNo != null && !WaybillActivity.activeRouteNo.isEmpty()) {
            String[] routeKeys = ROUTE_STOPS.keySet().toArray(new String[0]);
            for (int i = 0; i < routeKeys.length; i++) {
                if (routeKeys[i].startsWith(WaybillActivity.activeRouteNo + " ") ||
                    routeKeys[i].startsWith(WaybillActivity.activeRouteNo + "—")) {
                    spinnerRoute.setSelection(i, false);
                    break;
                }
            }
            // Show fixed route label
            String matchedKey = (String) spinnerRoute.getSelectedItem();
            tvRouteFixed.setText(matchedKey != null ? matchedKey : "Route " + WaybillActivity.activeRouteNo);
        } else {
            tvRouteFixed.setText("Route " + WaybillActivity.activeRouteNo);
        }
        btnStartCollection.setOnClickListener(v -> onStartCollection());
        btnPay.setOnClickListener(v -> onPayNow());
        btnEditTrip.setOnClickListener(v -> showScreen(1));
        btnNextPassenger.setOnClickListener(v -> {
            passengerCount = 1;
            ((TextView) findViewById(R.id.tvPassCount)).setText("1");
            showScreen(2);
        });
        TextView tvPassCount = findViewById(R.id.tvPassCount);
        findViewById(R.id.btnPassMinus).setOnClickListener(v -> {
            if (passengerCount > 1) {
                passengerCount--;
                tvPassCount.setText(String.valueOf(passengerCount));
                updateFareButton();
            }
        });
        findViewById(R.id.btnPassPlus).setOnClickListener(v -> {
            if (passengerCount < 10) {
                passengerCount++;
                tvPassCount.setText(String.valueOf(passengerCount));
                updateFareButton();
            }
        });
        findViewById(R.id.btnValidateQr).setOnClickListener(v -> {
            showScreen(4);
            launchQrScanner();
        });
        findViewById(R.id.btnScanQr).setOnClickListener(v -> launchQrScanner());
        findViewById(R.id.btnQrBack).setOnClickListener(v -> {
            findViewById(R.id.qrResultCard).setVisibility(View.GONE);
            showScreen(1);
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            ConductorAuth.logout(this);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // End Trip button on tap screen
        Button btnEndTrip = new Button(this);
        btnEndTrip.setText("END TRIP");
        btnEndTrip.setBackgroundColor(0xFFE65100);
        btnEndTrip.setTextColor(0xFFFFFFFF);
        btnEndTrip.setOnClickListener(v -> {
            // Go back to waybill screen — WaybillActivity will call endTrip
            finish();
        });
        ((android.widget.LinearLayout) btnEditTrip.getParent()).addView(btnEndTrip);

        showScreen(1);
        initSdk();
        triggerSync();
    }

    private void setupRouteSpinner() {
        String[] routeNames = ROUTE_STOPS.keySet().toArray(new String[0]);
        ArrayAdapter<String> routeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, routeNames);
        routeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRoute.setAdapter(routeAdapter);
        spinnerRoute.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                tvRouteFixed.setText(routeNames[pos]);
                updateStopSpinners(routeNames[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        updateStopSpinners(routeNames[0]);
    }

    private void updateStopSpinners(String routeName) {
        List<String> stops = ROUTE_STOPS.get(routeName);
        if (stops == null) return;
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, stops);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSource.setAdapter(a);
        ArrayAdapter<String> b = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, stops);
        b.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDest.setAdapter(b);
        spinnerSource.setSelection(0);
        spinnerDest.setSelection(stops.size() - 1);

        AdapterView.OnItemSelectedListener fareListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { fetchFare(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
        spinnerSource.setOnItemSelectedListener(fareListener);
        spinnerDest.setOnItemSelectedListener(fareListener);
        fetchFare();
    }

    private void fetchFare() {
        String routeName = spinnerRoute.getSelectedItem() != null ? spinnerRoute.getSelectedItem().toString() : "";
        String from = spinnerSource.getSelectedItem() != null ? spinnerSource.getSelectedItem().toString() : "";
        String to   = spinnerDest.getSelectedItem()   != null ? spinnerDest.getSelectedItem().toString()   : "";
        if (from.isEmpty() || to.isEmpty() || from.equals(to)) return;
        String routeId = routeName.contains(" — ") ? routeName.split(" — ")[0] : routeName;

        executor.execute(() -> {
            try {
                String encodedFrom = java.net.URLEncoder.encode(from, "UTF-8");
                String encodedTo   = java.net.URLEncoder.encode(to,   "UTF-8");
                String urlStr = BACKEND_URL.replace("/tap", "/fare") +
                    "?route=" + routeId + "&from=" + encodedFrom + "&to=" + encodedTo + "&service=ordinary";
                java.net.URL url = new java.net.URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    String resp = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                    String fareVal = new org.json.JSONObject(resp).optString("fare", "");
                    if (!fareVal.isEmpty()) {
                        uiHandler.post(() -> {
                            inputFare.setText(fareVal);
                            updateFareButton();
                        });
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void updateFareButton() {
        String fareStr = inputFare.getText().toString().trim();
        if (fareStr.isEmpty()) return;
        try {
            double perPax = Double.parseDouble(fareStr);
            double total  = perPax * passengerCount;
            String label  = passengerCount > 1
                ? "TAP PAY  ₹" + String.format("%.0f", total) + " (" + passengerCount + " pax)"
                : "TAP PAY  ₹" + String.format("%.0f", total);
            btnStartCollection.setText(label);
        } catch (Exception ignored) {}
    }

    private void onStartCollection() {
        route  = WaybillActivity.activeRouteNo != null ? WaybillActivity.activeRouteNo : "";
        source = spinnerSource.getSelectedItem() != null ? spinnerSource.getSelectedItem().toString() : "";
        dest   = spinnerDest.getSelectedItem()   != null ? spinnerDest.getSelectedItem().toString()   : "";
        fare   = inputFare.getText().toString().trim();
        if (fare.isEmpty()) { inputFare.setError("Required"); return; }
        if (source.equals(dest)) { spinnerSource.requestFocus(); return; }
        String shortRoute = route.contains(" — ") ? route.split(" — ")[0] : route;
        tapRoute.setText("Route " + shortRoute);
        tapJourney.setText(source + " → " + dest);
        tapFare.setText("₹" + fare);
        // Skip Screen 2 — go straight to payment
        onPayNow();
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
            EzeAPI.initialize(this, REQUEST_CODE_INITIALIZE, req);
        } catch (JSONException e) {
            Log.e(TAG, "initSdk error", e);
        }
    }

    private void onPayNow() {
        if (!sdkInitialised) { initSdk(); return; }
        btnPay.setEnabled(false);
        try {
            String shortRoute = (route != null && route.contains(" — ")) ? route.split(" — ")[0] : (route != null ? route : "");
            JSONObject refs = new JSONObject();
            refs.put("reference1", "C1-" + System.currentTimeMillis());
            refs.put("reference2", shortRoute);
            refs.put("reference3", source + " to " + dest);
            JSONObject options = new JSONObject();
            options.put("references", refs);
            double perPax = Double.parseDouble(fare.isEmpty() ? "0" : fare);
            double total  = perPax * passengerCount;
            JSONObject req = new JSONObject();
            req.put("amount",  String.valueOf(total));
            req.put("options", options);
            EzeAPI.pay(this, REQUEST_CODE_PAY, req);
        } catch (JSONException e) {
            Log.e(TAG, "pay error", e);
            btnPay.setEnabled(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_INITIALIZE) {
            sdkInitialised = (resultCode == RESULT_OK);
            if (sdkInitialised && screenTap.getVisibility() == View.VISIBLE) {
                tapFare.setText("₹" + fare);
                btnPay.setEnabled(true);
            }
            return;
        }
        if (requestCode == REQUEST_CODE_PAY) {
            btnPay.setEnabled(true);
            String responseJson = data != null ? data.getStringExtra("response") : null;
            android.util.Log.d("PAY_RESULT", "requestCode=" + requestCode + " resultCode=" + resultCode + " data=" + data + " response=" + responseJson);
            if (resultCode == RESULT_OK) handlePaymentSuccess(responseJson);
            else {
                // Ezetap SDK may return non-RESULT_OK even on success — check response JSON
                if (responseJson != null) {
                    try {
                        org.json.JSONObject jr = new org.json.JSONObject(responseJson);
                        String txnStatus = jr.optString("txnStatus", jr.optString("status", ""));
                        android.util.Log.d("PAY_RESULT", "txnStatus=" + txnStatus);
                        if ("SUCCESS".equalsIgnoreCase(txnStatus) || "APPROVED".equalsIgnoreCase(txnStatus)) {
                            handlePaymentSuccess(responseJson);
                            return;
                        }
                    } catch (Exception ignored) {}
                }
                handlePaymentFailure(responseJson);
            }
        }
        if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK && data != null) {
            // fallback path for older ZXing intent flow
            String scanned = data.getStringExtra("SCAN_RESULT");
            if (scanned == null) scanned = data.getStringExtra("com.google.zxing.client.android.SCAN_RESULT");
            android.util.Log.d("QR_SCAN", "onActivityResult SCAN scanned=" + scanned);
            if (scanned != null && !scanned.isEmpty()) {
                showScreen(4);
                validateQrContent(scanned);
            }
        }
    }

    private String extractPaymentMode(String responseJson) {
        try {
            JSONObject jr = new JSONObject(responseJson);
            String mode = jr.optJSONObject("result")
                           .optJSONObject("txn")
                           .optString("paymentMode", "CARD");
            return mode.isEmpty() ? "CARD" : mode;
        } catch (Exception e) { return "CARD"; }
    }

    private void handlePaymentSuccess(String responseJson) {
        String txnId       = extractTxnId(responseJson);
        String payMode     = extractPaymentMode(responseJson);
        String conductorId = ConductorAuth.getLoggedInId(this);
        String timestamp   = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
        String shortRoute  = (route != null && route.contains(" — ")) ? route.split(" — ")[0] : (route != null ? route : "");

        // Save to local DB first (offline-safe)
        TicketEntity entity = new TicketEntity();
        entity.txnId       = txnId;
        entity.route       = shortRoute;
        entity.source      = source;
        entity.destination = dest;
        entity.fare        = fare;
        entity.conductorId = conductorId;
        entity.timestamp   = timestamp;
        entity.synced      = false;

        executor.execute(() -> {
            AppDatabase.get(this).ticketDao().insert(entity);

            if (isOnline()) {
                try {
                    JSONObject body = new JSONObject();
                    body.put("txnId",       txnId);
                    body.put("route",       shortRoute);
                    body.put("source",      source);
                    body.put("destination", dest);
                    body.put("fare",        fare);
                    body.put("conductorId", conductorId);
                    body.put("timestamp",   timestamp);
                    body.put("tripId",          WaybillActivity.activeTripId);
                    body.put("waybillNo",       WaybillActivity.activeWaybillNo);
                    body.put("paymentMode",     payMode);
                    body.put("passengerCount",  passengerCount);

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
                    if (code == 200 || code == 201) {
                        java.io.InputStream is = conn.getInputStream();
                        String resp    = new Scanner(is).useDelimiter("\\A").next();
                        String ticketNo = new JSONObject(resp).optString("ticketNo", "TK-?????");
                        // Mark synced in DB
                        TicketEntity[] pending = AppDatabase.get(this).ticketDao().getPending()
                                .stream().filter(t -> t.txnId.equals(txnId)).toArray(TicketEntity[]::new);
                        if (pending.length > 0) {
                            pending[0].synced   = true;
                            pending[0].ticketNo = ticketNo;
                            AppDatabase.get(this).ticketDao().update(pending[0]);
                        }
                        final String tNo = ticketNo;
                        uiHandler.post(() -> {
                            showResultScreen(true, null, tNo, txnId);
                            printTicket(tNo, shortRoute, source, dest, passengerCount,
                                Double.parseDouble(fare.isEmpty() ? "0" : fare), payMode, timestamp);
                        });
                    } else {
                        uiHandler.post(() -> {
                            showResultScreen(true, null, "TK-PENDING", txnId);
                            printTicket("TK-PENDING", shortRoute, source, dest, passengerCount,
                                Double.parseDouble(fare.isEmpty() ? "0" : fare), payMode, timestamp);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Backend POST error", e);
                    uiHandler.post(() -> {
                        showResultScreen(true, null, "TK-PENDING", txnId);
                        printTicket("TK-PENDING", shortRoute, source, dest, passengerCount,
                            Double.parseDouble(fare.isEmpty() ? "0" : fare), payMode, timestamp);
                    });
                }
            } else {
                // Offline — show pending, sync later
                triggerSync();
                uiHandler.post(() -> {
                    showResultScreen(true, null, "TK-PENDING", txnId);
                    printTicket("TK-PENDING", shortRoute, source, dest, passengerCount,
                        Double.parseDouble(fare.isEmpty() ? "0" : fare), payMode, timestamp);
                });
            }
        });
    }

    private void handlePaymentFailure(String responseJson) {
        String errorMsg  = "Payment failed";
        String errorCode = "";
        if (responseJson != null) {
            try {
                JSONObject root = new JSONObject(responseJson);
                JSONObject err  = root.optJSONObject("error");
                if (err != null) {
                    errorMsg  = err.optString("message", errorMsg);
                    errorCode = err.optString("code", "");
                }
                String ext = root.optString("externalError", "");
                if (!ext.isEmpty()) errorCode = ext;
            } catch (JSONException ignored) {}
        }
        boolean isSessionError = errorCode.contains("SESSION") || errorCode.contains("LOGIN")
                || errorMsg.toLowerCase().contains("login") || errorMsg.toLowerCase().contains("session");
        if (isSessionError) {
            sdkInitialised = false;
            initSdk();
            uiHandler.postDelayed(() -> { btnPay.setEnabled(true); showScreen(2); }, 2500);
            tapFare.setText("Re-logging in…");
        } else {
            showResultScreen(false, errorMsg, null, null);
        }
    }

    private void showResultScreen(boolean success, String errorMsg, String ticketNo, String txnId) {
        if (success) {
            boolean pending = "TK-PENDING".equals(ticketNo);
            String msg = pending ? "Saved - syncing" : "Issued: " + ticketNo;
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Payment failed: " + errorMsg, Toast.LENGTH_LONG).show();
        }
        showScreen(1);
    }

    private void triggerSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(this).enqueue(syncRequest);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private String extractTxnId(String responseJson) {
        if (responseJson == null) return "TXN-" + System.currentTimeMillis();
        try {
            JSONObject result = new JSONObject(responseJson).optJSONObject("result");
            if (result != null) {
                for (String key : new String[]{"txnId", "transactionId", "id"}) {
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
        screenQr.setVisibility(screen == 4 ? View.VISIBLE : View.GONE);
    }

    private void launchQrScanner() {
        com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
        options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE);
        options.setPrompt("Point camera at QR code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        qrLauncher.launch(options);
    }

    private void validateQrContent(String raw) {
        android.widget.TextView tvStatus = findViewById(R.id.tvQrStatus);
        android.widget.TextView tvDetail = findViewById(R.id.tvQrDetail);
        android.widget.LinearLayout card = findViewById(R.id.qrResultCard);
        android.widget.ImageView ivPhoto = findViewById(R.id.ivQrPhoto);
        if (ivPhoto != null) ivPhoto.setVisibility(View.GONE);
        card.setVisibility(View.VISIBLE);

        if (raw == null || raw.isEmpty()) {
            tvStatus.setText("INVALID");
            tvStatus.setTextColor(0xFFC62828);
            card.setBackgroundColor(0xFFFFEBEE);
            tvDetail.setText("No QR content provided.");
            return;
        }

        if (raw.trim().startsWith("{")) {
            validateJsonQr(raw.trim(), tvStatus, tvDetail, card);
            return;
        }

        // Expected: <sig>,<ticketId>,<userId>,<tripId>,<count>,<usedFlag>,<type>,<zone>,<fare_paise>,<colour>,<timestamp>,,,
        // Minimum 11 non-empty fields, may have trailing commas
        String[] parts = raw.split(",", -1);

        // Strip trailing empty fields for count check
        int nonEmpty = 0;
        for (String p : parts) if (!p.trim().isEmpty()) nonEmpty++;

        if (nonEmpty < 7) {
            tvStatus.setText("INVALID");
            tvStatus.setTextColor(0xFFC62828);
            card.setBackgroundColor(0xFFFFEBEE);
            tvDetail.setText("Invalid QR format.\nExpected: sig,ticketId,userId,tripId,count,usedFlag,type,zone,fare,...\nGot " + nonEmpty + " fields.");
            return;
        }

        // Parse fields (trim each)
        String signature = parts[0].trim();
        String ticketId  = parts.length > 1 ? parts[1].trim() : "—";
        String userId    = parts.length > 2 ? parts[2].trim() : "—";
        String tripId    = parts.length > 3 ? parts[3].trim() : "—";
        String count     = parts.length > 4 ? parts[4].trim() : "—";
        String usedFlag  = parts.length > 5 ? parts[5].trim() : "—";
        String type      = parts.length > 6 ? parts[6].trim() : "—";
        String zone      = parts.length > 7 ? parts[7].trim() : "—";
        String farePaise = parts.length > 8 ? parts[8].trim() : "0";
        String colour    = parts.length > 9 ? parts[9].trim() : "—";
        String timestamp = parts.length > 10 ? parts[10].trim() : "—";

        // Basic sanity: signature should be non-trivial, ticketId non-empty
        if (signature.isEmpty() || ticketId.isEmpty()) {
            tvStatus.setText("INVALID");
            tvStatus.setTextColor(0xFFC62828);
            card.setBackgroundColor(0xFFFFEBEE);
            tvDetail.setText("Missing signature or ticket ID.");
            return;
        }

        // Check usedFlag — 1 = already used
        boolean alreadyUsed = "1".equals(usedFlag);

        // Parse fare
        double fareRupees = 0;
        try { fareRupees = Double.parseDouble(farePaise); } catch (Exception ignored) {}

        // Format timestamp if numeric (epoch ms)
        String tsDisplay = timestamp;
        try {
            long epoch = Long.parseLong(timestamp);
            tsDisplay = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.US)
                .format(new java.util.Date(epoch));
        } catch (Exception ignored) {}

        if (alreadyUsed) {
            tvStatus.setText("ALREADY USED");
            tvStatus.setTextColor(0xFFE65100);
            card.setBackgroundColor(0xFFFFF3E0);
            return;
        }

        // Check local DB for duplicate scan on this device
        final String fTicketId = ticketId;
        final double fFare = fareRupees;
        final String fZone = zone;
        final String fUserId = userId;
        final String fTripId = tripId;
        final String fType = type;
        final String fColour = colour;
        final String fCount = count;
        final String fTs = tsDisplay;
        executor.execute(() -> {
            int dbCount = AppDatabase.get(this).scannedQrDao().exists(fTicketId);
            runOnUiThread(() -> {
                if (dbCount > 0) {
                    tvStatus.setText("ALREADY SCANNED");
                    tvStatus.setTextColor(0xFFE65100);
                    card.setBackgroundColor(0xFFFFF3E0);
                    tvDetail.setText("Ticket " + fTicketId + " already scanned on this device.\nCannot accept again.");
                } else {
                    tvStatus.setText("VALID");
                    tvStatus.setTextColor(0xFF2E7D32);
                    card.setBackgroundColor(0xFFE8F5E9);
                    tvDetail.setText(
                        "Ticket ID:  " + fTicketId + "\n" +
                        "User ID:    " + fUserId + "\n" +
                        "Trip ID:    " + fTripId + "\n" +
                        "Type:       " + fType + "\n" +
                        "Zone:       " + fZone + "\n" +
                        "Fare:       Rs." + String.format("%.2f", fFare) + "\n" +
                        "Colour:     " + fColour + "\n" +
                        "Count:      " + fCount + "\n" +
                        "Issued:     " + fTs
                    );
                    // Save to local scanned log
                    ScannedQrEntity qrLog = new ScannedQrEntity();
                    qrLog.ticketId = fTicketId;
                    qrLog.scannedAt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(new java.util.Date());
                    executor.execute(() -> AppDatabase.get(this).scannedQrDao().insert(qrLog));
                    postQrTicket(fTicketId, fFare, fZone);
                }
            });
        });
    }

    private void validateJsonQr(String raw,
                                android.widget.TextView tvStatus,
                                android.widget.TextView tvDetail,
                                android.widget.LinearLayout card) {
        try {
            JSONObject qr = new JSONObject(raw);
            String token        = qr.optString("token", "");
            String name         = qr.optString("name", "—");
            String aadhaarLast4 = qr.optString("aadhaarLast4", "****");
            String gender       = qr.optString("gender", "—");
            String scheme       = qr.optString("scheme", "—");
            String issuedAt     = qr.optString("issuedAt", "");
            String photoB64     = qr.optString("photo", "");

            if (token.isEmpty() || issuedAt.isEmpty()) {
                tvStatus.setText("INVALID");
                tvStatus.setTextColor(0xFFC62828);
                card.setBackgroundColor(0xFFFFEBEE);
                tvDetail.setText("Missing token or issuedAt.");
                return;
            }

            // Parse issuedAt — format: 2026-05-15T05:07:05.355Z
            long issuedMs;
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                issuedMs = sdf.parse(issuedAt).getTime();
            } catch (Exception e1) {
                // Fallback: without millis
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                    sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    issuedMs = sdf.parse(issuedAt).getTime();
                } catch (Exception e2) {
                    tvStatus.setText("INVALID");
                    tvStatus.setTextColor(0xFFC62828);
                    card.setBackgroundColor(0xFFFFEBEE);
                    tvDetail.setText("Cannot parse issuedAt: " + issuedAt);
                    return;
                }
            }

            long diff = Math.abs(System.currentTimeMillis() - issuedMs);
            if (diff > 30000) {
                tvStatus.setText("EXPIRED");
                tvStatus.setTextColor(0xFFC62828);
                card.setBackgroundColor(0xFFFFEBEE);
                tvDetail.setText("Pass expired. Age: " + (diff / 1000) + "s (max 10s)\nIssuedAt: " + issuedAt);
                return;
            }

            String ts = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
            tvStatus.setText("VALID FREE PASS");
            tvStatus.setTextColor(0xFF1B5E20);
            card.setBackgroundColor(0xFFE8F5E9);

            android.widget.ImageView ivPhoto2 = findViewById(R.id.ivQrPhoto);
            if (ivPhoto2 != null && !photoB64.isEmpty()) {
                try {
                    byte[] decoded = android.util.Base64.decode(photoB64, android.util.Base64.DEFAULT);
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                    if (bmp != null) { ivPhoto2.setImageBitmap(bmp); ivPhoto2.setVisibility(View.VISIBLE); }
                } catch (Exception ignored) {}
            }

            tvDetail.setText(
                "Name:    " + name + "\n" +
                "Gender:  " + gender + "\n" +
                "Aadhaar: ****" + aadhaarLast4 + "\n" +
                "Scheme:  " + scheme
            );
            postFreePassTicket(token, name, gender, aadhaarLast4, scheme, ts);

        } catch (Exception e) {
            tvStatus.setText("INVALID");
            tvStatus.setTextColor(0xFFC62828);
            card.setBackgroundColor(0xFFFFEBEE);
            tvDetail.setText("Malformed JSON: " + e.getMessage());
        }
    }

    private void postFreePassTicket(String token, String name, String gender, String aadhaarLast4, String scheme, String ts) {
        String conductorId = ConductorAuth.getLoggedInId(this);
        String txnId      = "FP-" + token;
        String shortRoute = WaybillActivity.activeRouteNo != null ? WaybillActivity.activeRouteNo : "—";
        String src = spinnerSource.getSelectedItem() != null ? spinnerSource.getSelectedItem().toString() : "—";
        String dst = spinnerDest.getSelectedItem()   != null ? spinnerDest.getSelectedItem().toString()   : "—";

        TicketEntity entity = new TicketEntity();
        entity.txnId = txnId; entity.route = shortRoute; entity.source = src;
        entity.destination = dst; entity.fare = "0"; entity.conductorId = conductorId;
        entity.timestamp = ts; entity.synced = false;

        executor.execute(() -> {
            AppDatabase.get(this).ticketDao().insert(entity);
            if (isOnline()) {
                try {
                    JSONObject body = new JSONObject();
                    body.put("txnId", txnId); body.put("route", shortRoute);
                    body.put("source", src); body.put("destination", dst);
                    body.put("fare", 0); body.put("conductorId", conductorId);
                    body.put("timestamp", ts); body.put("tripId", WaybillActivity.activeTripId);
                    body.put("waybillNo", WaybillActivity.activeWaybillNo);
                    body.put("paymentMode", "FREE_PASS"); body.put("passengerCount", 1);

                    URL url = new URL(BACKEND_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true); conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
                    conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));

                    int code = conn.getResponseCode();
                    String ticketNo = "TK-FP";
                    if (code == 200 || code == 201) {
                        String resp = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                        ticketNo = new JSONObject(resp).optString("ticketNo", "TK-FP");
                        TicketEntity[] pending = AppDatabase.get(this).ticketDao().getPending()
                            .stream().filter(t -> t.txnId.equals(txnId)).toArray(TicketEntity[]::new);
                        if (pending.length > 0) { pending[0].synced = true; pending[0].ticketNo = ticketNo; AppDatabase.get(this).ticketDao().update(pending[0]); }
                    } else {
                        android.util.Log.w("FREE_PASS", "backend returned " + code + ", printing anyway");
                    }
                    final String finalTicketNo = ticketNo;
                    uiHandler.post(() -> {
                        Toast.makeText(this, "Free pass: " + finalTicketNo, Toast.LENGTH_SHORT).show();
                        printFreePassTicket(finalTicketNo, name, gender, aadhaarLast4, scheme, shortRoute, ts);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "free pass post error", e);
                    uiHandler.post(() -> printFreePassTicket("TK-FP", name, gender, aadhaarLast4, scheme, shortRoute, ts));
                }
            } else {
                uiHandler.post(() -> printFreePassTicket("TK-FP", name, gender, aadhaarLast4, scheme, shortRoute, ts));
            }
        });
    }

    void printFreePassTicket(String ticketNo, String name, String gender, String aadhaarLast4, String scheme, String route, String ts) {
        executor.execute(() -> {
            try {
                com.pax.dal.IDAL dal = com.pax.neptunelite.api.NeptuneLiteUser.getInstance().getDal(this);
                com.pax.dal.IPrinter printer = dal.getPrinter();
                printer.init();
                android.graphics.Bitmap bmp = buildFreePassBitmap(ticketNo, name, gender, aadhaarLast4, scheme, route, ts, 384);
                printer.printBitmap(bmp);
                printer.step(60);
                int result = printer.start();
                if (result != 0) uiHandler.post(() -> Toast.makeText(this, "Printer error: " + result, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                android.util.Log.e("PRINTER", "free pass print failed", e);
                uiHandler.post(() -> Toast.makeText(this, "Print failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private android.graphics.Bitmap buildFreePassBitmap(String ticketNo, String name, String gender,
            String aadhaarLast4, String scheme, String route, String ts, int width) {
        int p = 14, lh = 36;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(width, 16 * lh + p * 2, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(bmp);
        c.drawColor(android.graphics.Color.WHITE);

        android.graphics.Paint center = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        center.setColor(android.graphics.Color.BLACK); center.setTextAlign(android.graphics.Paint.Align.CENTER);
        center.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        android.graphics.Paint left = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        left.setColor(android.graphics.Color.BLACK); left.setTextAlign(android.graphics.Paint.Align.LEFT);
        android.graphics.Paint right = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        right.setColor(android.graphics.Color.BLACK); right.setTextAlign(android.graphics.Paint.Align.RIGHT);
        android.graphics.Paint div = new android.graphics.Paint();
        div.setColor(android.graphics.Color.BLACK); div.setStrokeWidth(2f);
        android.graphics.Paint dotDiv = new android.graphics.Paint();
        dotDiv.setColor(android.graphics.Color.BLACK); dotDiv.setStrokeWidth(1f);
        dotDiv.setPathEffect(new android.graphics.DashPathEffect(new float[]{6, 4}, 0));

        String fleet   = WaybillActivity.activeFleetNo   != null ? WaybillActivity.activeFleetNo   : "—";
        String waybill = WaybillActivity.activeWaybillNo != null ? WaybillActivity.activeWaybillNo : "—";
        String tripNo  = String.valueOf(WaybillActivity.activeTripNo);
        String dateStr = ts, timeStr = "";
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
            java.util.Date dt = in.parse(ts);
            dateStr = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.US).format(dt);
            timeStr = new java.text.SimpleDateFormat("HH:mm:ss",   java.util.Locale.US).format(dt);
        } catch (Exception ignored) {}

        int y = p;
        center.setTextSize(28f); c.drawText("Free Women Ticket", width / 2f, y + 28, center); y += lh;
        center.setTextSize(18f); center.setTypeface(android.graphics.Typeface.DEFAULT);
        c.drawText("MTC, Chennai", width / 2f, y + 18, center); y += lh - 6;
        c.drawLine(p, y, width - p, y, div); y += 8;

        left.setTextSize(20f); right.setTextSize(20f);
        c.drawText("T No: " + ticketNo, p, y + 20, left); c.drawText(dateStr, width - p, y + 20, right); y += lh;
        c.drawText("Waybill: " + waybill, p, y + 20, left); c.drawText(timeStr, width - p, y + 20, right); y += lh - 4;
        c.drawLine(p, y, width - p, y, div); y += 8;

        c.drawText("Fleet: " + fleet, p, y + 20, left); c.drawText("Trip: " + tripNo, width - p, y + 20, right); y += lh - 4;
        c.drawLine(p, y, width - p, y, div); y += 8;

        center.setTextSize(22f); center.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("Route: " + route, width / 2f, y + 22, center); y += lh;
        c.drawLine(p, y, width - p, y, div); y += 8;

        left.setTextSize(20f); left.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        right.setTextSize(20f); right.setTypeface(android.graphics.Typeface.DEFAULT);
        c.drawText("Name:", p, y + 20, left); c.drawText(name, width - p, y + 20, right); y += lh;
        c.drawText("Gender:", p, y + 20, left); c.drawText(gender, width - p, y + 20, right); y += lh;
        c.drawText("Aadhaar:", p, y + 20, left); c.drawText("****" + aadhaarLast4, width - p, y + 20, right); y += lh;
        c.drawText("Scheme:", p, y + 20, left); c.drawText(scheme, width - p, y + 20, right); y += lh;
        c.drawLine(p, y, width - p, y, div); y += 8;

        center.setTextSize(28f); center.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("FREE  (FREE_PASS)", width / 2f, y + 28, center); y += lh + 6;

        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(p, y); path.lineTo(width - p, y);
        c.drawPath(path, dotDiv); y += 10;

        center.setTextSize(17f); center.setTypeface(android.graphics.Typeface.DEFAULT);
        c.drawText("Thank you for travelling with MTC", width / 2f, y + 17, center);
        return bmp;
    }

    private void postQrTicket(String qrTicketId, double fareRupees, String zone) {
        String conductorId = ConductorAuth.getLoggedInId(this);
        String timestamp   = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
        String shortRoute  = WaybillActivity.activeRouteNo != null ? WaybillActivity.activeRouteNo : "—";
        // Use current spinner values if available, else fall back to zone info
        String src  = spinnerSource.getSelectedItem() != null ? spinnerSource.getSelectedItem().toString() : "—";
        String dst  = spinnerDest.getSelectedItem()   != null ? spinnerDest.getSelectedItem().toString()   : ("Zone " + zone);
        String txnId = "QR-" + qrTicketId;
        double fareVal = fareRupees;

        // Save to local DB
        TicketEntity entity = new TicketEntity();
        entity.txnId       = txnId;
        entity.route       = shortRoute;
        entity.source      = src;
        entity.destination = dst;
        entity.fare        = String.valueOf(fareVal);
        entity.conductorId = conductorId;
        entity.timestamp   = timestamp;
        entity.synced      = false;

        executor.execute(() -> {
            AppDatabase.get(this).ticketDao().insert(entity);

            if (isOnline()) {
                try {
                    JSONObject body = new JSONObject();
                    body.put("txnId",       txnId);
                    body.put("route",       shortRoute);
                    body.put("source",      src);
                    body.put("destination", dst);
                    body.put("fare",        fareVal);
                    body.put("conductorId", conductorId);
                    body.put("timestamp",   timestamp);
                    body.put("tripId",         WaybillActivity.activeTripId);
                    body.put("waybillNo",      WaybillActivity.activeWaybillNo);
                    body.put("paymentMode",    "NCMC");
                    body.put("passengerCount", 1);

                    URL url = new URL(BACKEND_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));

                    int code = conn.getResponseCode();
                    String ticketNo = "TK-QR";
                    if (code == 200 || code == 201) {
                        String resp = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                        ticketNo = new JSONObject(resp).optString("ticketNo", "TK-QR");
                        TicketEntity[] pending = AppDatabase.get(this).ticketDao().getPending()
                            .stream().filter(t -> t.txnId.equals(txnId)).toArray(TicketEntity[]::new);
                        if (pending.length > 0) {
                            pending[0].synced   = true;
                            pending[0].ticketNo = ticketNo;
                            AppDatabase.get(this).ticketDao().update(pending[0]);
                        }
                    }
                    final String finalQrTicketNo = ticketNo;
                    uiHandler.post(() -> {
                        Toast.makeText(this, "Recorded: " + finalQrTicketNo, Toast.LENGTH_SHORT).show();
                        printTicket(finalQrTicketNo, shortRoute, src, dst, 1, fareVal, "QR", timestamp);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "QR ticket post error", e);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ── Thermal printer ──────────────────────────────────────────────────────
    void printTicket(String ticketNo, String route, String src, String dst, int pax, double fare, String mode, String ts) {
        executor.execute(() -> {
            try {
                com.pax.dal.IDAL dal = com.pax.neptunelite.api.NeptuneLiteUser.getInstance().getDal(this);
                com.pax.dal.IPrinter printer = dal.getPrinter();
                printer.init();

                // Build bitmap to print
                int width = 384; // 58mm roll = 384px at 8dots/mm
                android.graphics.Bitmap bmp = buildTicketBitmap(ticketNo, route, src, dst, pax, fare, mode, ts, width);
                printer.printBitmap(bmp);
                printer.step(60);
                int result = printer.start();
                android.util.Log.d("PRINTER", "print result=" + result);
                if (result != 0) {
                    uiHandler.post(() -> Toast.makeText(this, "Printer error: " + result, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                android.util.Log.e("PRINTER", "print failed", e);
                uiHandler.post(() -> Toast.makeText(this, "Print failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private android.graphics.Bitmap buildTicketBitmap(String ticketNo, String route, String src, String dst,
                                                       int pax, double fare, String mode, String ts, int width) {
        int p = 14;
        int lh = 36;
        int height = 18 * lh + p * 2;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(bmp);
        c.drawColor(android.graphics.Color.WHITE);

        android.graphics.Paint center = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        center.setColor(android.graphics.Color.BLACK);
        center.setTextAlign(android.graphics.Paint.Align.CENTER);
        center.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        android.graphics.Paint left = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        left.setColor(android.graphics.Color.BLACK);
        left.setTextAlign(android.graphics.Paint.Align.LEFT);
        left.setTypeface(android.graphics.Typeface.DEFAULT);

        android.graphics.Paint right = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        right.setColor(android.graphics.Color.BLACK);
        right.setTextAlign(android.graphics.Paint.Align.RIGHT);
        right.setTypeface(android.graphics.Typeface.DEFAULT);

        android.graphics.Paint div = new android.graphics.Paint();
        div.setColor(android.graphics.Color.BLACK);
        div.setStrokeWidth(2f);

        android.graphics.Paint dotDiv = new android.graphics.Paint();
        dotDiv.setColor(android.graphics.Color.BLACK);
        dotDiv.setStrokeWidth(1f);
        dotDiv.setPathEffect(new android.graphics.DashPathEffect(new float[]{6, 4}, 0));

        String fleet    = WaybillActivity.activeFleetNo   != null ? WaybillActivity.activeFleetNo   : "—";
        String waybill  = WaybillActivity.activeWaybillNo != null ? WaybillActivity.activeWaybillNo : "—";
        String tripNo   = String.valueOf(WaybillActivity.activeTripNo);

        // Parse timestamp into date + time
        String dateStr = ts, timeStr = "";
        try {
            java.text.SimpleDateFormat in  = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
            java.text.SimpleDateFormat d   = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.US);
            java.text.SimpleDateFormat t   = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
            java.util.Date dt = in.parse(ts);
            dateStr = d.format(dt); timeStr = t.format(dt);
        } catch (Exception ignored) {}

        int y = p;

        // ── Header ──────────────────────────────────────────────
        center.setTextSize(30f);
        c.drawText("MTC, CHENNAI", width / 2f, y + 30, center);
        y += lh;
        center.setTextSize(20f);
        center.setTypeface(android.graphics.Typeface.DEFAULT);
        c.drawText("Metropolitan Transport Corporation", width / 2f, y + 20, center);
        y += lh - 6;
        c.drawLine(p, y, width - p, y, div);
        y += 8;

        // ── Ticket No + Date + Time ──────────────────────────────
        left.setTextSize(22f); right.setTextSize(22f);
        c.drawText("T No: " + ticketNo, p, y + 22, left);
        c.drawText(dateStr, width - p, y + 22, right);
        y += lh;
        left.setTextSize(20f); right.setTextSize(20f);
        c.drawText("Waybill: " + waybill, p, y + 20, left);
        c.drawText(timeStr, width - p, y + 20, right);
        y += lh - 4;
        c.drawLine(p, y, width - p, y, div);
        y += 8;

        // ── Fleet + Trip ─────────────────────────────────────────
        left.setTextSize(20f); right.setTextSize(20f);
        c.drawText("Fleet: " + fleet, p, y + 20, left);
        c.drawText("Trip: " + tripNo, width - p, y + 20, right);
        y += lh - 4;
        c.drawLine(p, y, width - p, y, div);
        y += 8;

        // ── Route ────────────────────────────────────────────────
        center.setTextSize(24f);
        center.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("Route: " + route, width / 2f, y + 24, center);
        y += lh;

        // ── Journey ──────────────────────────────────────────────
        left.setTextSize(21f);
        left.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText(src, p, y + 21, left);
        right.setTextSize(21f);
        right.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText(dst, width - p, y + 21, right);
        y += lh - 6;
        left.setTextSize(19f);
        left.setTypeface(android.graphics.Typeface.DEFAULT);
        right.setTextSize(19f);
        right.setTypeface(android.graphics.Typeface.DEFAULT);
        c.drawText("(From)", p, y + 19, left);
        c.drawText("(To)", width - p, y + 19, right);
        y += lh - 4;
        c.drawLine(p, y, width - p, y, div);
        y += 8;

        // ── Fare breakdown ───────────────────────────────────────
        left.setTextSize(20f);
        right.setTextSize(20f);
        double total = fare * pax;
        c.drawText("Ad: " + pax + " x " + String.format("%.2f", fare), p, y + 20, left);
        c.drawText("= Rs." + String.format("%.2f", total), width - p, y + 20, right);
        y += lh;

        // ── Big fare + mode ──────────────────────────────────────
        center.setTextSize(30f);
        center.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("Rs. " + String.format("%.2f", total) + " (" + mode + ")", width / 2f, y + 30, center);
        y += lh + 6;

        // ── Dotted cut line ──────────────────────────────────────
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(p, y); path.lineTo(width - p, y);
        c.drawPath(path, dotDiv);
        y += 10;

        // ── Footer ───────────────────────────────────────────────
        center.setTextSize(17f);
        center.setTypeface(android.graphics.Typeface.DEFAULT);
        c.drawText("Thank you for travelling with MTC", width / 2f, y + 17, center);
        y += lh - 10;
        center.setTextSize(15f);
        c.drawText("Please retain ticket for inspection", width / 2f, y + 15, center);

        return bmp;
    }
}
