package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class ProviderLoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_login);

        redirectIfAlreadySignedIn();

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        View signIn = findViewById(R.id.btn_provider_sign_in);
        if (signIn != null) signIn.setOnClickListener(v -> handleProviderLogin());

        View registerPrompt = findViewById(R.id.tv_register_prompt);
        if (registerPrompt != null) registerPrompt.setOnClickListener(v -> startActivity(new Intent(this, ProviderRegistrationActivity.class)));

        View forgotPassword = findViewById(R.id.tv_forgot_password);
        if (forgotPassword != null) {
            forgotPassword.setOnClickListener(v -> sendProviderPasswordReset());
        }
    }

    private void redirectIfAlreadySignedIn() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }

        AuthRepository.getInstance().getCurrentSession(new AuthRepository.RoleCallback() {
            @Override
            public void onResult(UserAccount user) {
                if (user == null) {
                    return;
                }
                if (UserAccount.ROLE_PROVIDER.equals(user.getRole())) {
                    Intent intent = new Intent(ProviderLoginActivity.this, ProviderDashboardActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    Intent intent = new Intent(ProviderLoginActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                // stay on provider login screen if role lookup fails
            }
        });
    }

    private void handleProviderLogin() {
        String email = getTextFromInputLayout(R.id.til_email);
        String password = getTextFromInputLayout(R.id.til_password);

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Business email and password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthRepository.getInstance().login(email, password, UserAccount.ROLE_PROVIDER,
                new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess(UserAccount user) {
                        Toast.makeText(ProviderLoginActivity.this, "Welcome back", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(ProviderLoginActivity.this, ProviderDashboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ProviderLoginActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String getTextFromInputLayout(int layoutId) {
        TextInputLayout layout = findViewById(layoutId);
        if (layout == null) return "";
        TextInputEditText input = (TextInputEditText) layout.getEditText();
        if (input == null) return "";
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void sendProviderPasswordReset() {
        String email = getTextFromInputLayout(R.id.til_email);
        if (email.isEmpty()) {
            Toast.makeText(this, "Enter your business email first", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Password reset email sent", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Unable to send reset email", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
