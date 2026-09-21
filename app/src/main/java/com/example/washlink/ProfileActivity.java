package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        ImageView backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        FrameLayout bellLayout = findViewById(R.id.iv_bell);
        if (bellLayout != null) {
            bellLayout.setOnClickListener(v -> {
                    Intent intent = new Intent(ProfileActivity.this, NotificationsActivity.class);
                    startActivity(intent);
                });
        }

        LinearLayout savedAddresses = findViewById(R.id.row_saved_addresses);
        if (savedAddresses != null) {
            savedAddresses.setOnClickListener(v ->
                    Toast.makeText(this, "Saved addresses are not available in demo mode", Toast.LENGTH_SHORT).show());
        }

        LinearLayout password = findViewById(R.id.row_password);
        if (password != null) {
            password.setOnClickListener(v ->
                    Toast.makeText(this, "Password settings are not available in demo mode", Toast.LENGTH_SHORT).show());
        }

        LinearLayout paymentMethods = findViewById(R.id.row_payment_methods);
        if (paymentMethods != null) {
            paymentMethods.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileActivity.this, PaymentActivity.class);
                startActivity(intent);
            });
        }

        LinearLayout orderHistory = findViewById(R.id.row_order_history);
        if (orderHistory != null) {
            orderHistory.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileActivity.this, HistoryActivity.class);
                startActivity(intent);
            });
        }

        com.google.android.material.button.MaterialButton logoutButton = findViewById(R.id.btn_logout);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(ProfileActivity.this, SignInActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }

        BottomNavHelper.bind(this);
    }
}
