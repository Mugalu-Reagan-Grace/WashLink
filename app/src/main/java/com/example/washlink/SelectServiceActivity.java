package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

public class SelectServiceActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private ConstraintLayout dropOffCard;
    private ConstraintLayout pickupDeliveryCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_select_service);

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        dropOffCard = findViewById(R.id.card_drop_off);
        pickupDeliveryCard = findViewById(R.id.card_pickup_delivery);

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(SelectServiceActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        BottomNavHelper.bind(this);

        dropOffCard.setOnClickListener(v -> openNextScreen("Drop Off"));
        pickupDeliveryCard.setOnClickListener(v -> openNextScreen("Pickup & Delivery"));
    }

    private void openNextScreen(String serviceName) {
        Intent intent;

        if ("Drop Off".equals(serviceName)) {
            intent = new Intent(SelectServiceActivity.this, DropOffActivity.class);
        } else {
            intent = new Intent(SelectServiceActivity.this, NearbyProvidersActivity.class);
        }

        intent.putExtra("selected_service", serviceName);
        startActivity(intent);
    }
}
