package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class PickupConfirmationActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private MaterialButton confirmPickupButton;
    private CheckBox pickupFeeCheckBox;
    private TextView itemCountText;
    private ImageView minusButton;
    private ImageView plusButton;

    private int itemCount = 15;
    private String selectedService = "Pickup & Delivery";
    private String selectedAddress = "Home address";
    private String selectedDate = "Today";
    private String selectedTime = "10:00 AM";
    private String selectedContact = "Call";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pickup_confirmation);

        if (getIntent() != null) {
            selectedService = getIntent().getStringExtra("selected_service");
            selectedAddress = getIntent().getStringExtra("selected_address");
            selectedDate = getIntent().getStringExtra("selected_date");
            selectedTime = getIntent().getStringExtra("selected_time");
            selectedContact = getIntent().getStringExtra("selected_contact");
        }

        if (selectedService == null || selectedService.trim().isEmpty()) {
            selectedService = "Pickup & Delivery";
        }
        if (selectedAddress == null || selectedAddress.trim().isEmpty()) {
            selectedAddress = "Home address";
        }
        if (selectedDate == null || selectedDate.trim().isEmpty()) {
            selectedDate = "Today";
        }
        if (selectedTime == null || selectedTime.trim().isEmpty()) {
            selectedTime = "10:00 AM";
        }
        if (selectedContact == null || selectedContact.trim().isEmpty()) {
            selectedContact = "Call";
        }

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        confirmPickupButton = findViewById(R.id.btn_confirm_pickup);
        pickupFeeCheckBox = findViewById(R.id.cb_pickup_fee);
        itemCountText = findViewById(R.id.tv_item_count);
        minusButton = findViewById(R.id.btn_minus);
        plusButton = findViewById(R.id.btn_plus);

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(PickupConfirmationActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        minusButton.setOnClickListener(v -> {
            itemCount = Math.max(1, itemCount - 1);
            itemCountText.setText(String.valueOf(itemCount));
        });

        plusButton.setOnClickListener(v -> {
            itemCount = Math.min(50, itemCount + 1);
            itemCountText.setText(String.valueOf(itemCount));
        });

        confirmPickupButton.setOnClickListener(v -> {
            if (!pickupFeeCheckBox.isChecked()) {
                Toast.makeText(this, "Please confirm the pickup fee", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(PickupConfirmationActivity.this, OrderSummaryActivity.class);
            intent.putExtra("selected_service", selectedService);
            intent.putExtra("selected_address", selectedAddress);
            intent.putExtra("selected_date", selectedDate);
            intent.putExtra("selected_time", selectedTime);
            intent.putExtra("selected_contact", selectedContact);
            intent.putExtra("item_count", itemCount);
            startActivity(intent);
        });
    }
}
