package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

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
        TextView paymentStatusValue = findViewById(R.id.tv_payment_status_value);
        TextView scheduleValue = findViewById(R.id.tv_schedule_value);
        TextView providerValue = findViewById(R.id.tv_provider_value);
        TextView addressValue = findViewById(R.id.tv_address_value);

        if (getIntent() != null) {
            String bookingId = getIntent().getStringExtra("booking_id");
            if (orderIdValue != null && bookingId != null) {
                orderIdValue.setText("Order #" + bookingId);
            }
            String service = getIntent().getStringExtra("selected_service");
            String paymentMethod = getIntent().getStringExtra("selected_payment_method");
            String schedule = getIntent().getStringExtra("selected_date");
            String time = getIntent().getStringExtra("selected_time");
            String provider = getIntent().getStringExtra("provider_name");
            String address = getIntent().getStringExtra("selected_address");
            if (service != null && !service.trim().isEmpty()) {
                serviceTypeValue.setText(service);
            }
            if (paymentMethod != null && !paymentMethod.trim().isEmpty()) {
                paymentMethodValue.setText(paymentMethod);
            }
            if (schedule != null && time != null) scheduleValue.setText(schedule + " • " + time);
            if (provider != null && !provider.trim().isEmpty()) providerValue.setText(provider);
            if (address != null && !address.trim().isEmpty()) addressValue.setText(address);
            String paymentStatus = getIntent().getStringExtra("payment_status");
            if ("PAID".equalsIgnoreCase(paymentStatus)) {
                paymentStatusValue.setText(R.string.payment_paid);
                paymentStatusValue.setBackgroundResource(R.drawable.bg_payment_paid_pill);
            }
        }

        int total = getIntent() != null
                ? getIntent().getIntExtra("quote_total", 0) : 0;
        if (total <= 0) {
            total = BookingPricing.quote(10, true).total;
        }
        final int confirmedTotal = total;
        totalAmountValue.setText(BookingPricing.format(confirmedTotal));

        findViewById(R.id.btn_view_receipt).setOnClickListener(v ->
                showReceipt(totalAmountValue.getText().toString(), orderIdValue.getText().toString()));
        findViewById(R.id.btn_contact_support).setOnClickListener(v -> {
            Intent support = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("mailto:" + getString(R.string.profile_support_email)));
            support.putExtra(Intent.EXTRA_SUBJECT, "WashLink order support");
            if (support.resolveActivity(getPackageManager()) != null) {
                startActivity(support);
            } else {
                Toast.makeText(this, getString(R.string.profile_support_email),
                        Toast.LENGTH_LONG).show();
            }
        });

        trackOrderButton.setOnClickListener(v -> {
            Intent intent = new Intent(OrderConfirmedActivity.this, OrderTrackingActivity.class);
            intent.putExtra("booking_id", getIntent().getStringExtra("booking_id"));
            startActivity(intent);
            finish();
        });
    }

    private void showReceipt(String total, String orderId) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.view_receipt)
                .setMessage(orderId + "\n" + getString(R.string.total_amount) + ": " + total)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
