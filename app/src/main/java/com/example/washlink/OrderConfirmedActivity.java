package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.example.washlink.data.BookingPricing;

import java.util.Locale;

public class OrderConfirmedActivity extends AppCompatActivity {

    private MaterialButton trackOrderButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_order_confirmed);

        BottomNavHelper.bind(this);

        TextView serviceTypeValue = findViewById(R.id.tv_service_type_value);
        TextView totalAmountValue = findViewById(R.id.tv_total_amount_value);
        TextView paymentMethodValue = findViewById(R.id.tv_payment_method_value);
        TextView orderIdValue = findViewById(R.id.tv_order_id);
        trackOrderButton = findViewById(R.id.btn_track_order);

        if (getIntent() != null) {
            String bookingId = getIntent().getStringExtra("booking_id");
            if (orderIdValue != null && bookingId != null) {
                orderIdValue.setText("Order #" + bookingId);
            }
            String service = getIntent().getStringExtra("selected_service");
            String paymentMethod = getIntent().getStringExtra("selected_payment_method");
            if (service != null && !service.trim().isEmpty()) {
                serviceTypeValue.setText(service);
            }
            if (paymentMethod != null && !paymentMethod.trim().isEmpty()) {
                paymentMethodValue.setText(paymentMethod);
            }
        }

        int total = getIntent() != null
                ? getIntent().getIntExtra("quote_total", 0) : 0;
        if (total <= 0) {
            total = BookingPricing.quote(10, true).total;
        }
        totalAmountValue.setText(BookingPricing.format(total));

        trackOrderButton.setOnClickListener(v -> {
            Intent intent = new Intent(OrderConfirmedActivity.this, OrderTrackingActivity.class);
            intent.putExtra("booking_id", getIntent().getStringExtra("booking_id"));
            startActivity(intent);
            finish();
        });
    }
}
