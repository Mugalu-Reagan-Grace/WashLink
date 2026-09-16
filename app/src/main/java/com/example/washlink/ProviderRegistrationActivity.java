package com.example.washlink;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class ProviderRegistrationActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_registration);

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        View create = findViewById(R.id.btn_create_business_account);
        if (create != null) create.setOnClickListener(v -> startActivity(new android.content.Intent(this, ProviderDashboardActivity.class)));

        View signInPrompt = findViewById(R.id.tv_sign_in_prompt);
        if (signInPrompt != null) signInPrompt.setOnClickListener(v -> startActivity(new android.content.Intent(this, ProviderLoginActivity.class)));
    }
}
