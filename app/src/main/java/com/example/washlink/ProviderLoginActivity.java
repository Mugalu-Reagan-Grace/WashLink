package com.example.washlink;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class ProviderLoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_login);

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        View signIn = findViewById(R.id.btn_provider_sign_in);
        if (signIn != null) signIn.setOnClickListener(v -> startActivity(new android.content.Intent(this, ProviderDashboardActivity.class)));

        View registerPrompt = findViewById(R.id.tv_register_prompt);
        if (registerPrompt != null) registerPrompt.setOnClickListener(v -> startActivity(new android.content.Intent(this, ProviderRegistrationActivity.class)));
    }
}
