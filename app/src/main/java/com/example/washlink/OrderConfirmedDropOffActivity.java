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
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);

        LinearLayout serviceTypeRow = findViewById(R.id.row_service_type);
        LinearLayout paymentMethodRow = findViewById(R.id.row_payment_method);
        LinearLayout totalAmountRow = findViewById(R.id.row_total_amount);
        TextView serviceTypeValue = serviceTypeRow != null && serviceTypeRow.getChildCount() > 1 ? (TextView) serviceTypeRow.getChildAt(1) : null;
        TextView paymentMethodValue = paymentMethodRow != null && paymentMethodRow.getChildCount() > 1 ? (TextView) paymentMethodRow.getChildAt(1) : null;
        TextView totalAmountValue = totalAmountRow != null && totalAmountRow.getChildCount() > 1 ? (TextView) totalAmountRow.getChildAt(1) : null;
        trackOrderButton = findViewById(R.id.btn_track_order);
        TextView paymentStatusValue = findViewById(R.id.tv_payment_status_value);
        TextView scheduleValue = findViewById(R.id.tv_schedule_value);
        TextView providerValue = findViewById(R.id.tv_provider_value);
        TextView addressValue = findViewById(R.id.tv_address_value);

        if (getIntent() != null) {
            String service = getIntent().getStringExtra("selected_service");
            String laundryService = getIntent().getStringExtra("selected_laundry_service");
            String paymentMethod = getIntent().getStringExtra("selected_payment_method");
            String bookingId = getIntent().getStringExtra("booking_id");
            String date = getIntent().getStringExtra("selected_date");
            String time = getIntent().getStringExtra("selected_time");
            String provider = getIntent().getStringExtra("provider_name");
            if (serviceTypeValue != null && service != null && !service.trim().isEmpty()) {
                serviceTypeValue.setText(laundryService == null || laundryService.trim().isEmpty()
                        ? service : laundryService);
            }
            if (paymentMethodValue != null && paymentMethod != null && !paymentMethod.trim().isEmpty()) {
                paymentMethodValue.setText(paymentMethod);
            }
            if (bookingId != null && !bookingId.trim().isEmpty()) {
                LinearLayout orderIdRow = findViewById(R.id.row_order_id);
                if (orderIdRow != null && orderIdRow.getChildCount() > 1) {
                    ((TextView) orderIdRow.getChildAt(1)).setText("Order #" + bookingId);
                }
            }
            if (date != null && time != null && scheduleValue != null) scheduleValue.setText(date + " • " + time);
            if (providerValue != null && provider != null && !provider.trim().isEmpty()) providerValue.setText(provider);
            String providerAddress = getIntent().getStringExtra("provider_address");
            if (addressValue != null) {
                addressValue.setText(providerAddress == null || providerAddress.trim().isEmpty()
                        ? getString(R.string.provider_dropoff_location) : providerAddress);
            }
            PaymentStatusRenderer.render(paymentStatusValue,
                    getIntent().getStringExtra("payment_status"));
            int total = getIntent().getIntExtra("quote_total", 0);
            if (totalAmountValue != null) {
                if (total <= 0) {
                    int itemCount = getIntent().getIntExtra("item_count", 15);
                    int weightKg = getIntent().getIntExtra("weight_kg",
                            BookingPricing.estimateWeightKg(itemCount));
                    double rate = getIntent().getDoubleExtra(
                            "price_per_kg", BookingPricing.PRICE_PER_KG);
                    total = BookingPricing.quote(weightKg, false, rate).total;
                }
                totalAmountValue.setText(BookingPricing.format(total));
            }
        }

        trackOrderButton.setOnClickListener(v -> {
            Intent intent = new Intent(OrderConfirmedDropOffActivity.this, OrderTrackingActivity.class);
            intent.putExtra("booking_id", getIntent().getStringExtra("booking_id"));
            startActivity(intent);
            finish();
        });
        findViewById(R.id.btn_view_receipt).setOnClickListener(v -> {
            String orderIdText = "";
            LinearLayout orderIdRow = findViewById(R.id.row_order_id);
            if (orderIdRow != null && orderIdRow.getChildCount() > 1) {
                CharSequence text = ((TextView) orderIdRow.getChildAt(1)).getText();
                if (text != null) {
                    orderIdText = text.toString();
                }
            }
            String totalText = totalAmountValue != null && totalAmountValue.getText() != null
                    ? totalAmountValue.getText().toString() : "";
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.view_receipt)
                    .setMessage(orderIdText + "\n" + getString(R.string.total_amount) + ": " + totalText)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
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
    }
}
