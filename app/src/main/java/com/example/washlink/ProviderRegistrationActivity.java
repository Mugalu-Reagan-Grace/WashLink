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

public class ProviderRegistrationActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_registration);

        redirectIfAlreadySignedIn();

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        View create = findViewById(R.id.btn_create_business_account);
        if (create != null) create.setOnClickListener(v -> handleProviderRegistration());

        View signInPrompt = findViewById(R.id.tv_sign_in_prompt);
        if (signInPrompt != null) signInPrompt.setOnClickListener(v -> startActivity(new Intent(this, ProviderLoginActivity.class)));
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
                    Intent intent = new Intent(ProviderRegistrationActivity.this, ProviderDashboardActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    Intent intent = new Intent(ProviderRegistrationActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                // stay on registration screen if role lookup fails
            }
        });
    }

    private void handleProviderRegistration() {
        String businessName = getTextFromInputLayout(R.id.til_business_name);
        String ownerName = getTextFromInputLayout(R.id.til_owner_name);
        String email = getTextFromInputLayout(R.id.til_business_email);
        String phone = getTextFromInputLayout(R.id.til_phone);
        String address = getTextFromInputLayout(R.id.til_address);
        String password = getTextFromInputLayout(R.id.til_password);

        if (businessName.isEmpty() || ownerName.isEmpty() || email.isEmpty() || phone.isEmpty() || address.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all business details", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthRepository.getInstance().registerProvider(businessName, ownerName, email, phone, address, password,
                new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess(UserAccount user) {
                        Toast.makeText(ProviderRegistrationActivity.this, "Business account created", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(ProviderRegistrationActivity.this, ProviderDashboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ProviderRegistrationActivity.this, message, Toast.LENGTH_SHORT).show();
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
}
