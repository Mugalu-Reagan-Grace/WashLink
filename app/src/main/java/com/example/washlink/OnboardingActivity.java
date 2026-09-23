package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import com.example.washlink.ui.customer.MainActivity;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class OnboardingActivity extends AppCompatActivity {

    private Button btn_get_started;
    private Button btn_sign_in;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Intent intent = new Intent(OnboardingActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

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
