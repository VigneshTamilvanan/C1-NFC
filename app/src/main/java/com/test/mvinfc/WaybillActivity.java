package com.test.mvinfc;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WaybillActivity extends Activity {

    private static final String TAG = "WaybillActivity";
    private static final String BASE_URL = "https://movingtech-etm.onrender.com/api/transit";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private LinearLayout tripListContainer;
    private TextView tvWaybillInfo, tvStatus;
    private Button btnCloseWaybill;

    private String waybillNo;
    private JSONArray trips;

    // Passed from MainActivity when trip starts
    public static String activeTripId  = null;
    public static int    activeTripNo  = 0;
    public static String activeWaybillNo = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!ConductorAuth.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Chennai One — ETM");
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(Color.parseColor("#1A237E"));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnLogout = new Button(this);
        btnLogout.setText("Logout");
        btnLogout.setTextSize(12);
        btnLogout.setBackgroundColor(Color.parseColor("#B71C1C"));
        btnLogout.setTextColor(Color.WHITE);
        btnLogout.setOnClickListener(v -> {
            ConductorAuth.logout(this);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        header.addView(tvTitle);
        header.addView(btnLogout);

        TextView tvConductor = new TextView(this);
        tvConductor.setText(ConductorAuth.getLoggedInId(this) + " — " + ConductorAuth.getLoggedInName(this));
        tvConductor.setTextSize(13);
        tvConductor.setTextColor(Color.parseColor("#555555"));

        tvWaybillInfo = new TextView(this);
        tvWaybillInfo.setTextSize(13);
        tvWaybillInfo.setTextColor(Color.parseColor("#444444"));
        tvWaybillInfo.setPadding(0, 16, 0, 8);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(14);
        tvStatus.setTextColor(Color.parseColor("#888888"));
        tvStatus.setText("Loading waybill…");

        tripListContainer = new LinearLayout(this);
        tripListContainer.setOrientation(LinearLayout.VERTICAL);

        btnCloseWaybill = new Button(this);
        btnCloseWaybill.setText("CLOSE WAYBILL");
        btnCloseWaybill.setBackgroundColor(Color.parseColor("#B71C1C"));
        btnCloseWaybill.setTextColor(Color.WHITE);
        btnCloseWaybill.setVisibility(View.GONE);
        btnCloseWaybill.setOnClickListener(v -> closeWaybill());

        root.addView(header);
        root.addView(tvConductor);
        root.addView(tvWaybillInfo);
        root.addView(tvStatus);
        root.addView(tripListContainer);
        root.addView(btnCloseWaybill);
        scroll.addView(root);
        setContentView(scroll);

        fetchWaybill();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh after returning from booking
        if (waybillNo != null) fetchWaybill();
    }

    private void fetchWaybill() {
        String staffNo = ConductorAuth.getLoggedInId(this);
        tvStatus.setText("Fetching waybill…");
        tripListContainer.removeAllViews();

        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/waybill/conductor/" + staffNo);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();

                if (code == 404) {
                    uiHandler.post(() -> tvStatus.setText(
                        "No waybill assigned for today.\nContact your depot supervisor."));
                    return;
                }
                if (code != 200) {
                    uiHandler.post(() -> tvStatus.setText("Server error: " + code));
                    return;
                }

                String body = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                JSONObject wb = new JSONObject(body);
                waybillNo = wb.getString("waybill_no");
                activeWaybillNo = waybillNo;
                trips = wb.optJSONArray("trips");

                uiHandler.post(() -> renderWaybill(wb));

            } catch (Exception e) {
                Log.e(TAG, "fetch error", e);
                uiHandler.post(() -> tvStatus.setText("Network error — check connection"));
            }
        });
    }

    private void renderWaybill(JSONObject wb) {
        try {
            tvWaybillInfo.setText(
                "Waybill: " + wb.optString("waybill_no") +
                "  |  Route: " + wb.optString("route_no") +
                "  |  Fleet: " + wb.optString("fleet_no") +
                "\nShift: " + wb.optString("shift") +
                "  |  Driver: " + wb.optString("driver_name")
            );
            tvStatus.setText("Trips today:");

            tripListContainer.removeAllViews();
            boolean allClosed = true;
            boolean anyActive = false;

            if (trips == null || trips.length() == 0) {
                tvStatus.setText("No trips assigned in this waybill.");
                return;
            }

            for (int i = 0; i < trips.length(); i++) {
                JSONObject trip = trips.getJSONObject(i);
                String tripId  = trip.optString("trip_id");
                int    tripNo  = trip.optInt("trip_no");
                String status  = trip.optString("status", "pending");
                double revenue = trip.optDouble("total_amount", 0);
                int    pax     = trip.optInt("passenger_count", 0);

                if (!"closed".equals(status)) allClosed = false;
                if ("active".equals(status))  anyActive = true;

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 12, 0, 12);

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvTrip = new TextView(this);
                tvTrip.setText("Trip " + tripNo);
                tvTrip.setTextSize(16);
                tvTrip.setTextColor(Color.parseColor("#1A237E"));
                tvTrip.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvTripInfo = new TextView(this);
                String statusLabel = "pending".equals(status) ? "Not started" :
                                     "active".equals(status)  ? "🟢 In progress" : "✅ Closed";
                tvTripInfo.setText(statusLabel + ("closed".equals(status) ? "  |  " + pax + " pax  |  ₹" + (int)revenue : ""));
                tvTripInfo.setTextSize(13);
                tvTripInfo.setTextColor(Color.parseColor("#666666"));

                info.addView(tvTrip);
                info.addView(tvTripInfo);

                Button btn = new Button(this);
                if ("pending".equals(status)) {
                    btn.setText("BEGIN");
                    btn.setBackgroundColor(Color.parseColor("#2E7D32"));
                    btn.setTextColor(Color.WHITE);
                    btn.setEnabled(!anyActive); // only one trip active at a time
                    int finalI = i;
                    btn.setOnClickListener(v -> beginTrip(tripId, tripNo));
                } else if ("active".equals(status)) {
                    btn.setText("END TRIP");
                    btn.setBackgroundColor(Color.parseColor("#E65100"));
                    btn.setTextColor(Color.WHITE);
                    btn.setOnClickListener(v -> endTrip(tripId));
                } else {
                    btn.setText("CLOSED");
                    btn.setEnabled(false);
                    btn.setBackgroundColor(Color.parseColor("#9E9E9E"));
                    btn.setTextColor(Color.WHITE);
                }

                row.addView(info);
                row.addView(btn);

                // Divider
                View divider = new View(this);
                divider.setBackgroundColor(Color.parseColor("#DDDDDD"));
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divider.setLayoutParams(dp);

                tripListContainer.addView(row);
                tripListContainer.addView(divider);
            }

            btnCloseWaybill.setVisibility(allClosed ? View.VISIBLE : View.GONE);

        } catch (Exception e) {
            Log.e(TAG, "render error", e);
        }
    }

    private void beginTrip(String tripId, int tripNo) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("waybillNo", waybillNo);
                body.put("tripNo", tripNo);

                URL url = new URL(BASE_URL + "/trip/start");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.getOutputStream().write(body.toString().getBytes());

                int code = conn.getResponseCode();
                if (code == 200 || code == 201) {
                    activeTripId  = tripId;
                    activeTripNo  = tripNo;
                    uiHandler.post(() -> {
                        // Launch booking
                        startActivity(new Intent(this, MainActivity.class));
                    });
                } else {
                    uiHandler.post(() -> Toast.makeText(this, "Failed to start trip", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "beginTrip error", e);
                uiHandler.post(() -> Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void endTrip(String tripId) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("tripId", tripId);

                URL url = new URL(BASE_URL + "/trip/end");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.getOutputStream().write(body.toString().getBytes());

                int code = conn.getResponseCode();
                if (code == 200 || code == 201) {
                    String resp = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                    JSONObject summary = new JSONObject(resp).optJSONObject("summary");
                    activeTripId = null;
                    activeTripNo = 0;
                    uiHandler.post(() -> {
                        fetchWaybill(); // refresh list
                        if (summary != null) {
                            Toast.makeText(this,
                                "Trip closed — " + summary.optString("passenger_count","0") +
                                " pax  |  ₹" + summary.optString("total_amount","0"),
                                Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    uiHandler.post(() -> Toast.makeText(this, "Failed to end trip", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "endTrip error", e);
                uiHandler.post(() -> Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void closeWaybill() {
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/waybill/" + waybillNo + "/close");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.getOutputStream().write("{}".getBytes());

                int code = conn.getResponseCode();
                if (code == 200 || code == 201) {
                    String resp = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                    JSONObject s = new JSONObject(resp).optJSONObject("summary");
                    uiHandler.post(() -> {
                        tvStatus.setText("✅ Waybill closed");
                        btnCloseWaybill.setVisibility(View.GONE);
                        if (s != null) {
                            Toast.makeText(this,
                                "Waybill closed\nTotal: ₹" + s.optString("total_revenue","0") +
                                "  |  Pax: " + s.optString("total_passengers","0"),
                                Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    uiHandler.post(() -> Toast.makeText(this, "Failed to close waybill", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "closeWaybill error", e);
                uiHandler.post(() -> Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
