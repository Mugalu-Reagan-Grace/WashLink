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
            String laundryService = getIntent().getStringExtra("selected_laundry_service");
            String paymentMethod = getIntent().getStringExtra("selected_payment_method");
            String schedule = getIntent().getStringExtra("selected_date");
            String time = getIntent().getStringExtra("selected_time");
            String provider = getIntent().getStringExtra("provider_name");
            String address = getIntent().getStringExtra("selected_address");
            if (service != null && !service.trim().isEmpty()) {
                serviceTypeValue.setText(laundryService == null || laundryService.trim().isEmpty()
                        ? service : laundryService);
            }
            if (paymentMethod != null && !paymentMethod.trim().isEmpty()) {
                paymentMethodValue.setText(paymentMethod);
            }
            if (schedule != null && time != null) scheduleValue.setText(schedule + " • " + time);
            if (provider != null && !provider.trim().isEmpty()) providerValue.setText(provider);
            if (address != null && !address.trim().isEmpty()) addressValue.setText(address);
            String paymentStatus = getIntent().getStringExtra("payment_status");
            PaymentStatusRenderer.render(paymentStatusValue, paymentStatus);
        }

        int total = getIntent() != null
                ? getIntent().getIntExtra("quote_total", 0) : 0;
        if (total <= 0) {
            int itemCount = getIntent().getIntExtra("item_count", 15);
            int weightKg = getIntent().getIntExtra("weight_kg",
                    BookingPricing.estimateWeightKg(itemCount));
            double rate = getIntent().getDoubleExtra("price_per_kg", BookingPricing.PRICE_PER_KG);
            total = BookingPricing.quote(weightKg, true, rate).total;
        }
        final int confirmedTotal = total;
        totalAmountValue.setText(BookingPricing.format(confirmedTotal));

        findViewById(R.id.btn_view_receipt).setOnClickListener(v -> {
            String receiptTotal = "";
            if (totalAmountValue != null && totalAmountValue.getText() != null) {
                receiptTotal = totalAmountValue.getText().toString();
            }
            String receiptOrderId = "";
            TextView orderIdView = findViewById(R.id.tv_order_id);
            if (orderIdView != null && orderIdView.getText() != null) {
                receiptOrderId = orderIdView.getText().toString();
            }
            showReceipt(receiptTotal, receiptOrderId);
        });
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
