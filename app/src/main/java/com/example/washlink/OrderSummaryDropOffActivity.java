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

public class OrderSummaryDropOffActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private MaterialButton proceedPaymentButton;
    private TextView serviceTypeValueText;
    private TextView dateTimeValueText;
    private TextView itemsValueText;
    private TextView weightValueText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_order_summary_dropoff);

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        proceedPaymentButton = findViewById(R.id.btn_proceed_payment);
        serviceTypeValueText = findViewById(R.id.tv_service_type_value);
        dateTimeValueText = findViewById(R.id.tv_datetime_value);
        itemsValueText = findViewById(R.id.tv_items_value_inline);
        weightValueText = findViewById(R.id.tv_weight_value_inline);

        String serviceName = getIntent() != null ? getIntent().getStringExtra("selected_service") : null;
        String selectedDate = getIntent() != null ? getIntent().getStringExtra("selected_date") : null;
        String selectedTime = getIntent() != null ? getIntent().getStringExtra("selected_time") : null;
        int itemCount = getIntent() != null ? getIntent().getIntExtra("item_count", 15) : 15;

        if (serviceName == null || serviceName.trim().isEmpty()) {
            serviceName = "Drop Off";
        }
        if (selectedDate == null || selectedDate.trim().isEmpty()) {
            selectedDate = "Tue, Jun 7";
        }
        if (selectedTime == null || selectedTime.trim().isEmpty()) {
            selectedTime = "10:00 AM";
        }

        serviceTypeValueText.setText(serviceName);
        dateTimeValueText.setText(selectedDate + " • " + selectedTime);
        itemsValueText.setText(String.valueOf(itemCount));
        weightValueText.setText(getString(R.string.weight_range));

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(OrderSummaryDropOffActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        proceedPaymentButton.setOnClickListener(v -> {
            Intent intent = new Intent(OrderSummaryDropOffActivity.this, DropOffPaymentActivity.class);
            if (getIntent() != null) {
                intent.putExtras(getIntent());
            }
            intent.putExtra("item_count", itemCount);
            startActivity(intent);
        });
    }
}
