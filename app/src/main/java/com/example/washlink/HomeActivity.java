package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.models.UserAccount;

public class HomeActivity extends AppCompatActivity {

    private Button btn_book_service;
    private FrameLayout bellLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        AuthGuard.requireRole(this, UserAccount.ROLE_CUSTOMER, SignInActivity.class);

        btn_book_service = findViewById(R.id.btn_book_service);
        bellLayout = findViewById(R.id.iv_bell);

        btn_book_service.setOnClickListener(v -> bookServiceButtonClicked());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        // Wire quick 'View Details' to the tracking screen
        TextView viewDetails = findViewById(R.id.tv_view_details);
        if (viewDetails != null) {
            viewDetails.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, OrderTrackingActivity.class);
                startActivity(intent);
            });
        }

        findViewById(R.id.row_order_history).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.row_profile_settings).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        BottomNavHelper.bind(this);
    }

    private void bookServiceButtonClicked() {
        Intent intent = new Intent(HomeActivity.this, NearbyProvidersActivity.class);
        startActivity(intent);
    }
}
