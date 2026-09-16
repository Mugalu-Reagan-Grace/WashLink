package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class OrderSummaryActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private MaterialButton proceedPaymentButton;
    private TextView serviceTypeValueText;
    private TextView dateTimeValueText;
    private TextView addressValueText;
    private TextView itemsValueText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_order_summary);

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        proceedPaymentButton = findViewById(R.id.btn_proceed_payment);
        serviceTypeValueText = findViewById(R.id.tv_service_type_value);
        dateTimeValueText = findViewById(R.id.tv_datetime_value);
        addressValueText = findViewById(R.id.tv_address_value);
        itemsValueText = findViewById(R.id.tv_items_value_inline);

        String serviceName = getIntent() != null ? getIntent().getStringExtra("selected_service") : null;
        String address = getIntent() != null ? getIntent().getStringExtra("selected_address") : null;
        String date = getIntent() != null ? getIntent().getStringExtra("selected_date") : null;
        String time = getIntent() != null ? getIntent().getStringExtra("selected_time") : null;
        String contact = getIntent() != null ? getIntent().getStringExtra("selected_contact") : null;
        int itemCount = getIntent() != null ? getIntent().getIntExtra("item_count", 15) : 15;

        if (serviceName == null || serviceName.trim().isEmpty()) {
            serviceName = "Pickup & Delivery";
        }
        if (address == null || address.trim().isEmpty()) {
            address = "Home address";
        }
        if (date == null || date.trim().isEmpty()) {
            date = "Today";
        }
        if (time == null || time.trim().isEmpty()) {
            time = "10:00 AM";
        }
        if (contact == null || contact.trim().isEmpty()) {
            contact = "Call";
        }

        final String selectedService = serviceName;
        final String selectedAddress = address;
        final String selectedDate = date;
        final String selectedTime = time;
        final String selectedContact = contact;
        final int selectedItemCount = itemCount;

        serviceTypeValueText.setText(selectedService);
        dateTimeValueText.setText(selectedDate + " • " + selectedTime + " • " + selectedContact);
        addressValueText.setText(selectedAddress);
        itemsValueText.setText(String.valueOf(selectedItemCount));

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(OrderSummaryActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        proceedPaymentButton.setOnClickListener(v -> {
            Intent intent = new Intent(OrderSummaryActivity.this, PaymentActivity.class);
            intent.putExtra("selected_service", selectedService);
            intent.putExtra("selected_address", selectedAddress);
            intent.putExtra("selected_date", selectedDate);
            intent.putExtra("selected_time", selectedTime);
            intent.putExtra("selected_contact", selectedContact);
            intent.putExtra("item_count", selectedItemCount);
            startActivity(intent);
        });
    }
}
