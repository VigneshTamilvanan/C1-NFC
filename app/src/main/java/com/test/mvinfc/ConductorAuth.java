package com.test.mvinfc;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConductorAuth {

    private static final String PREFS     = "conductor_session";
    private static final String KEY_ID    = "logged_in_id";
    private static final String KEY_NAME  = "logged_in_name";

    // id -> {name, pin_sha256}
    private static final Map<String, String[]> CONDUCTORS = new LinkedHashMap<>();
    static {
        // Format: conductorId -> { displayName, SHA-256(PIN) }
        // PINs: C001=1234, C002=2345, C003=3456, C004=4567, C005=5678
        CONDUCTORS.put("C001", new String[]{"Ravi Kumar",    sha256("1234")});
        CONDUCTORS.put("C002", new String[]{"Meena Devi",    sha256("2345")});
        CONDUCTORS.put("C003", new String[]{"Arjun Selvam",  sha256("3456")});
        CONDUCTORS.put("C004", new String[]{"Priya Nair",    sha256("4567")});
        CONDUCTORS.put("C005", new String[]{"Karthik Raj",   sha256("5678")});
    }

    public static boolean login(Context ctx, String conductorId, String pin) {
        String[] record = CONDUCTORS.get(conductorId.trim().toUpperCase());
        if (record == null) return false;
        if (!sha256(pin).equals(record[1])) return false;
        SharedPreferences.Editor ed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        ed.putString(KEY_ID, conductorId.trim().toUpperCase());
        ed.putString(KEY_NAME, record[0]);
        ed.apply();
        return true;
    }

    public static void logout(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static boolean isLoggedIn(Context ctx) {
        return getLoggedInId(ctx) != null;
    }

    public static String getLoggedInId(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ID, null);
    }

    public static String getLoggedInName(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
