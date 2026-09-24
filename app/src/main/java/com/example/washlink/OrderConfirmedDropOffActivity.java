package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.example.washlink.data.BookingPricing;

public class OrderConfirmedDropOffActivity extends AppCompatActivity {

    private MaterialButton trackOrderButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_order_confirmed_dropoff);

        LinearLayout serviceTypeRow = findViewById(R.id.row_service_type);
        LinearLayout paymentMethodRow = findViewById(R.id.row_payment_method);
        LinearLayout totalAmountRow = findViewById(R.id.row_total_amount);
        TextView serviceTypeValue = (TextView) serviceTypeRow.getChildAt(1);
        TextView paymentMethodValue = (TextView) paymentMethodRow.getChildAt(1);
        TextView totalAmountValue = (TextView) totalAmountRow.getChildAt(1);
        trackOrderButton = findViewById(R.id.btn_track_order);
        TextView paymentStatusValue = findViewById(R.id.tv_payment_status_value);
        TextView scheduleValue = findViewById(R.id.tv_schedule_value);
        TextView providerValue = findViewById(R.id.tv_provider_value);
        TextView addressValue = findViewById(R.id.tv_address_value);

        if (getIntent() != null) {
            String service = getIntent().getStringExtra("selected_service");
            String paymentMethod = getIntent().getStringExtra("selected_payment_method");
            String bookingId = getIntent().getStringExtra("booking_id");
            String date = getIntent().getStringExtra("selected_date");
            String time = getIntent().getStringExtra("selected_time");
            String provider = getIntent().getStringExtra("provider_name");
            if (service != null && !service.trim().isEmpty()) {
                serviceTypeValue.setText(service);
            }
            if (paymentMethod != null && !paymentMethod.trim().isEmpty()) {
                paymentMethodValue.setText(paymentMethod);
            }
            if (bookingId != null && !bookingId.trim().isEmpty()) {
                ((TextView) ((LinearLayout) findViewById(R.id.row_order_id)).getChildAt(1))
                        .setText("Order #" + bookingId);
            }
            if (date != null && time != null) scheduleValue.setText(date + " • " + time);
            if (provider != null && !provider.trim().isEmpty()) providerValue.setText(provider);
            String providerAddress = getIntent().getStringExtra("provider_address");
            addressValue.setText(providerAddress == null || providerAddress.trim().isEmpty()
                    ? getString(R.string.provider_dropoff_location) : providerAddress);
            if ("PAID".equalsIgnoreCase(getIntent().getStringExtra("payment_status"))) {
                paymentStatusValue.setText(R.string.payment_paid);
                paymentStatusValue.setBackgroundResource(R.drawable.bg_payment_paid_pill);
            }
            int total = getIntent().getIntExtra("quote_total", 0);
            totalAmountValue.setText(BookingPricing.format(
                    total > 0 ? total : BookingPricing.quote(10, false).total));
        }

        trackOrderButton.setOnClickListener(v -> {
            Intent intent = new Intent(OrderConfirmedDropOffActivity.this, OrderTrackingActivity.class);
            intent.putExtra("booking_id", getIntent().getStringExtra("booking_id"));
            startActivity(intent);
            finish();
        });
        findViewById(R.id.btn_view_receipt).setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.view_receipt)
                        .setMessage(((TextView) ((LinearLayout) findViewById(R.id.row_order_id)).getChildAt(1)).getText()
                                + "\n" + getString(R.string.total_amount) + ": " + totalAmountValue.getText())
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
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
    }
}
