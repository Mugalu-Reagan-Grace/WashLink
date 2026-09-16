package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {

    private Button btn_get_started;
    private Button btn_sign_in;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);

        btn_get_started = findViewById(R.id.btn_get_started);
        btn_sign_in = findViewById(R.id.btn_sign_in);

        btn_get_started.setOnClickListener(v -> getStartedButtonClicked());
        btn_sign_in.setOnClickListener(v -> signInButtonClicked());

    }

    private void getStartedButtonClicked() {
        Intent intent = new Intent(OnboardingActivity.this, CreateAccountActivity.class);
        startActivity(intent);
    }

    private void signInButtonClicked() {
        Intent intent = new Intent(OnboardingActivity.this, SignInActivity.class);
        startActivity(intent);
    }
}
