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

        int dp8  = Math.round(8  * getResources().getDisplayMetrics().density);
        int dp12 = Math.round(12 * getResources().getDisplayMetrics().density);
        int dp16 = Math.round(16 * getResources().getDisplayMetrics().density);
        int dp20 = Math.round(20 * getResources().getDisplayMetrics().density);
        int dp24 = Math.round(24 * getResources().getDisplayMetrics().density);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp16, dp20, dp16, dp20);
        root.setBackgroundColor(Color.parseColor("#ECEFF1"));

        // ── Header bar ─────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(Color.parseColor("#1A237E"));
        header.setPadding(dp16, dp12, dp16, dp12);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Moving Tech ETM");
        tvTitle.setTextSize(22);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnLogout = new Button(this);
        btnLogout.setText("Logout");
        btnLogout.setTextSize(14);
        btnLogout.setBackgroundColor(Color.parseColor("#B71C1C"));
        btnLogout.setTextColor(Color.WHITE);
        btnLogout.setPadding(dp16, dp8, dp16, dp8);
        btnLogout.setOnClickListener(v -> {
            ConductorAuth.logout(this);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        header.addView(tvTitle);
        header.addView(btnLogout);

        // ── Conductor info card ─────────────────────────────────────────────
        LinearLayout conductorCard = new LinearLayout(this);
        conductorCard.setOrientation(LinearLayout.VERTICAL);
        conductorCard.setBackgroundColor(Color.WHITE);
        conductorCard.setPadding(dp16, dp12, dp16, dp12);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp12, 0, 0);
        conductorCard.setLayoutParams(cardLp);

        TextView tvConductor = new TextView(this);
        tvConductor.setText(ConductorAuth.getLoggedInId(this) + "  —  " + ConductorAuth.getLoggedInName(this));
        tvConductor.setTextSize(18);
        tvConductor.setTextColor(Color.parseColor("#1A237E"));
        tvConductor.setTypeface(null, android.graphics.Typeface.BOLD);

        tvWaybillInfo = new TextView(this);
        tvWaybillInfo.setTextSize(15);
        tvWaybillInfo.setTextColor(Color.parseColor("#444444"));
        tvWaybillInfo.setPadding(0, dp8, 0, 0);

        conductorCard.addView(tvConductor);
        conductorCard.addView(tvWaybillInfo);

        // ── Status / section label ──────────────────────────────────────────
        tvStatus = new TextView(this);
        tvStatus.setTextSize(16);
        tvStatus.setTextColor(Color.parseColor("#607D8B"));
        tvStatus.setText("Loading waybill…");
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.setMargins(0, dp16, 0, dp8);
        tvStatus.setLayoutParams(statusLp);

        // ── Trip list container ─────────────────────────────────────────────
        tripListContainer = new LinearLayout(this);
        tripListContainer.setOrientation(LinearLayout.VERTICAL);

        // ── Close waybill button ────────────────────────────────────────────
        btnCloseWaybill = new Button(this);
        btnCloseWaybill.setText("CLOSE WAYBILL");
        btnCloseWaybill.setTextSize(16);
        btnCloseWaybill.setBackgroundColor(Color.parseColor("#B71C1C"));
        btnCloseWaybill.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        closeLp.setMargins(0, dp16, 0, 0);
        btnCloseWaybill.setLayoutParams(closeLp);
        btnCloseWaybill.setVisibility(View.GONE);
        btnCloseWaybill.setOnClickListener(v -> closeWaybill());

        root.addView(header);
        root.addView(conductorCard);
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
                "\nRoute: " + wb.optString("route_no") +
                "   Fleet: " + wb.optString("fleet_no") +
                "\nShift: " + wb.optString("shift") +
                "   Driver: " + wb.optString("driver_name")
            );
            tvStatus.setText("TRIPS TODAY");

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

                int dpx8  = Math.round(8  * getResources().getDisplayMetrics().density);
                int dpx12 = Math.round(12 * getResources().getDisplayMetrics().density);
                int dpx16 = Math.round(16 * getResources().getDisplayMetrics().density);

                // Card wrapper
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setBackgroundColor(Color.WHITE);
                card.setPadding(dpx16, dpx16, dpx16, dpx16);
                card.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams cardMargin = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cardMargin.setMargins(0, 0, 0, dpx8);
                card.setLayoutParams(cardMargin);

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvTrip = new TextView(this);
                tvTrip.setText("Trip " + tripNo);
                tvTrip.setTextSize(20);
                tvTrip.setTextColor(Color.parseColor("#1A237E"));
                tvTrip.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvTripInfo = new TextView(this);
                String statusLabel = "pending".equals(status) ? "Not started" :
                                     "active".equals(status)  ? "🟢 In progress" : "✅ Closed";
                tvTripInfo.setText(statusLabel + ("closed".equals(status) ? "   " + pax + " pax   ₹" + (int)revenue : ""));
                tvTripInfo.setTextSize(16);
                tvTripInfo.setTextColor(Color.parseColor("#555555"));
                tvTripInfo.setPadding(0, dpx8, 0, 0);

                info.addView(tvTrip);
                info.addView(tvTripInfo);

                Button btn = new Button(this);
                btn.setTextSize(16);
                btn.setTypeface(null, android.graphics.Typeface.BOLD);
                btn.setPadding(dpx16, dpx12, dpx16, dpx12);
                if ("pending".equals(status)) {
                    btn.setText("BEGIN");
                    btn.setBackgroundColor(Color.parseColor("#2E7D32"));
                    btn.setTextColor(Color.WHITE);
                    btn.setEnabled(!anyActive);
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

                card.addView(info);
                card.addView(btn);
                tripListContainer.addView(card);
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
