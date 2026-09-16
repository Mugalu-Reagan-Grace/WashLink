package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class SignInActivity extends AppCompatActivity {

    private EditText email_et, password_et;
    private CheckBox remember_me_cb;
    private Button signInButton;
    private View googleButton;
    private TextView forgotPasswordText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_in);

        email_et = findViewById(R.id.et_email_login);
        password_et = findViewById(R.id.et_password_login);

        remember_me_cb = findViewById(R.id.cb_remember_me);

        signInButton = findViewById(R.id.btn_sign_in);
        googleButton = findViewById(R.id.btn_google_outlined);
        forgotPasswordText = findViewById(R.id.tv_forgot_password);

        signInButton.setOnClickListener(v -> signInButtonClicked());
        googleButton.setOnClickListener(v -> continueWithGoogleButtonClicked());
        forgotPasswordText.setOnClickListener(v -> forgotPasswordButtonClicked());
    }

    private void signInButtonClicked() {
        String user_email = email_et.getText().toString();
        String user_password = password_et.getText().toString();

        if (user_email.isEmpty() || user_password.isEmpty()) {
            Toast.makeText(this, "User email and password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean firstTimeSignup = getIntent() != null && getIntent().getBooleanExtra("first_time_signup", false);

        Intent intent;
        if (firstTimeSignup) {
            intent = new Intent(SignInActivity.this, SuccessActivity.class);
        } else {
            intent = new Intent(SignInActivity.this, HomeActivity.class);
        }

        startActivity(intent);
        finish();
    }

    private void continueWithGoogleButtonClicked() {
        Toast.makeText(this, "Google sign-in is not available in demo mode", Toast.LENGTH_SHORT).show();
    }

    private void forgotPasswordButtonClicked() {
        Toast.makeText(this, "Password recovery is not available in demo mode", Toast.LENGTH_SHORT).show();
    }
}
