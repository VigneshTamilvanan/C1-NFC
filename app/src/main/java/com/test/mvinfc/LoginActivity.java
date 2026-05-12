package com.test.mvinfc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.graphics.Color;

public class LoginActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ConductorAuth.isLoggedIn(this)) {
            startMain();
            return;
        }

        setContentView(R.layout.activity_login);

        EditText inputId  = findViewById(R.id.inputConductorId);
        EditText inputPin = findViewById(R.id.inputPin);
        TextView error    = findViewById(R.id.loginError);
        Button   btnLogin = findViewById(R.id.btnLogin);

        Button btnNfcTest = new Button(this);
        btnNfcTest.setText("NFC TEST (Dev)");
        btnNfcTest.setBackgroundColor(Color.parseColor("#607D8B"));
        btnNfcTest.setTextColor(Color.WHITE);
        ((android.widget.LinearLayout) btnLogin.getParent()).addView(btnNfcTest);
        btnNfcTest.setOnClickListener(v ->
            startActivity(new Intent(this, NfcTestActivity.class)));

        btnLogin.setOnClickListener(v -> {
            String id  = inputId.getText().toString().trim();
            String pin = inputPin.getText().toString().trim();
            error.setText("");

            if (id.isEmpty() || pin.isEmpty()) {
                error.setText("Enter Conductor ID and PIN");
                return;
            }
            if (ConductorAuth.login(this, id, pin)) {
                startMain();
            } else {
                error.setText("Invalid Conductor ID or PIN");
                inputPin.setText("");
            }
        });
    }

    private void startMain() {
        startActivity(new Intent(this, WaybillActivity.class));
        finish();
    }
}
