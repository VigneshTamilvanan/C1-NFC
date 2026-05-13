package com.test.mvinfc;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.pax.neptunelite.api.NeptuneLiteUser;
import com.pax.dal.IDAL;
import com.pax.dal.IPicc;
import com.pax.dal.entity.EPiccType;
import com.pax.dal.entity.PiccCardInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import dalvik.system.DexClassLoader;

public class NfcTestActivity extends Activity {

    private static final String TAG = "NfcTest";
    private TextView tvLog;
    private Handler handler = new Handler(Looper.getMainLooper());
    private IPicc picc;
    private IDAL dal;
    private NeptuneLiteUser neptuneLiteUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        tvLog = new TextView(this);
        tvLog.setTextSize(13);
        tvLog.setText("Ready.\n");

        Button btnTest = new Button(this);
        btnTest.setText("1. INIT NeptuneLite + piccOpen");
        btnTest.setOnClickListener(v -> runTest());

        Button btnDetect = new Button(this);
        btnDetect.setText("2. DETECT CARD (tap card after init)");
        btnDetect.setOnClickListener(v -> detectCard());

        layout.addView(btnTest);
        layout.addView(btnDetect);
        layout.addView(tvLog);
        scroll.addView(layout);
        setContentView(scroll);
    }

    private void log(String msg) {
        Log.d(TAG, msg);
        handler.post(() -> tvLog.setText(tvLog.getText() + "\n" + msg));
    }

    private void extractSo() {
        // Extract libDeviceConfig.so from assets to filesDir, then force-load it
        // before NeptuneLite's internal DexClassLoader tries System.loadLibrary()
        try {
            File dest = new File(getFilesDir(), "libDeviceConfig.so");
            if (!dest.exists()) {
                InputStream in = getAssets().open("libDeviceConfig.so");
                FileOutputStream out = new FileOutputStream(dest);
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                in.close(); out.close();
                log("libDeviceConfig.so extracted");
            }
            System.load(dest.getAbsolutePath());
            log("libDeviceConfig.so loaded");
        } catch (Exception e) {
            log("extract/load warning: " + e.getMessage());
        }
    }

    private void injectNativeLibPath(Object dal, String libDir) {
        try {
            ClassLoader cl = dal.getClass().getClassLoader();
            Field pathListField = cl.getClass().getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(cl);

            Field nativeLibDirsField = pathList.getClass().getDeclaredField("nativeLibraryDirectories");
            nativeLibDirsField.setAccessible(true);
            java.io.File[] existing = (java.io.File[]) nativeLibDirsField.get(pathList);
            java.io.File[] updated = new java.io.File[existing.length + 1];
            updated[0] = new java.io.File(libDir);
            System.arraycopy(existing, 0, updated, 1, existing.length);
            nativeLibDirsField.set(pathList, updated);
            log("native lib path injected: " + libDir);
        } catch (Exception e) {
            log("inject warning: " + e.getMessage());
        }
    }

    private void runTest() {
        log("--- NeptuneLite PICC Test ---");
        new Thread(() -> {
            try {
                extractSo();
                log("NeptuneLiteUser.getInstance()...");
                neptuneLiteUser = NeptuneLiteUser.getInstance();

                log("getDal()...");
                dal = neptuneLiteUser.getDal(this);
                // Inject our filesDir into the internal DexClassLoader's native lib path
                injectNativeLibPath(dal, getFilesDir().getAbsolutePath());
                if (dal == null) {
                    log("❌ DAL is null");
                    return;
                }
                log("✅ DAL: " + dal.getClass().getName());

                log("getPicc(INTERNAL)...");
                picc = dal.getPicc(EPiccType.INTERNAL);
                if (picc == null) {
                    log("❌ IPicc is null");
                    return;
                }
                log("✅ IPicc obtained");

                log("piccOpen()...");
                picc.open();
                log("✅ piccOpen() SUCCESS — reader powered on!");

            } catch (Exception e) {
                log("❌ " + e.getClass().getSimpleName() + ": " + e.getMessage());
                Log.e(TAG, "init error", e);
            }
        }).start();
    }

    private void detectCard() {
        if (picc == null) {
            log("❌ Run step 1 first");
            return;
        }
        log("--- Tap card now ---");
        new Thread(() -> {
            try {
                // ISO14443_AB covers Mifare/NFC tags; EMV_AB covers Apple Pay / contactless EMV
                log("piccDetect(EDetectMode.EMV_AB)...");
                PiccCardInfo info = picc.detect(com.pax.dal.entity.EDetectMode.EMV_AB);
                if (info == null) {
                    log("❌ No card detected");
                } else {
                    log("✅ CARD DETECTED!");
                    log("  CardType: " + info.getCardType());
                    byte[] uid = info.getSerialInfo();
                    if (uid != null && uid.length > 0) {
                        StringBuilder sb = new StringBuilder("  UID: ");
                        for (byte b : uid) sb.append(String.format("%02X ", b));
                        log(sb.toString());
                    }
                }
                picc.close();
                log("piccClose() done");
            } catch (Exception e) {
                log("❌ " + e.getClass().getSimpleName() + ": " + e.getMessage());
                Log.e(TAG, "detect error", e);
            }
        }).start();
    }
}
