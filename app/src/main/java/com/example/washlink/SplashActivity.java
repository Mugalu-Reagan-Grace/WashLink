package com.example.washlink;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::goToNextScreen, 2000);
    }

    private void goToNextScreen() {
        Intent intent = new Intent(SplashActivity.this, OnboardingActivity.class);
        startActivity(intent);
        finish();
    }
}
