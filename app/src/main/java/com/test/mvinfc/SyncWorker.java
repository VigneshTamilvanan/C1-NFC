package com.test.mvinfc;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppDatabase db = AppDatabase.get(getApplicationContext());
        List<TicketEntity> pending = db.ticketDao().getPending();
        Log.d(TAG, "Syncing " + pending.size() + " pending tickets");

        boolean allOk = true;
        for (TicketEntity t : pending) {
            try {
                JSONObject body = new JSONObject();
                body.put("txnId",       t.txnId);
                body.put("route",       t.route);
                body.put("source",      t.source);
                body.put("destination", t.destination);
                body.put("fare",        t.fare);
                body.put("conductorId", t.conductorId);
                body.put("timestamp",   t.timestamp);

                URL url = new URL(MainActivity.BACKEND_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) { os.write(payload); }

                int code = conn.getResponseCode();
                if (code == 200 || code == 201) {
                    String resp = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                    JSONObject respObj = new JSONObject(resp);
                    t.ticketNo = respObj.optString("ticketNo", "TK-?????");
                    t.synced = true;
                    db.ticketDao().update(t);
                    Log.d(TAG, "Synced txn " + t.txnId + " → " + t.ticketNo);
                } else {
                    Log.w(TAG, "Sync failed for " + t.txnId + " code=" + code);
                    allOk = false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync error for " + t.txnId, e);
                allOk = false;
            }
        }
        return allOk ? Result.success() : Result.retry();
    }
}
