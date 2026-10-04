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
import com.example.washlink.data.BookingPricing;

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
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        proceedPaymentButton = findViewById(R.id.btn_proceed_payment);
        serviceTypeValueText = findViewById(R.id.tv_service_type_value);
        dateTimeValueText = findViewById(R.id.tv_datetime_value);
        itemsValueText = findViewById(R.id.tv_items_value_inline);
        weightValueText = findViewById(R.id.tv_weight_value_inline);
        int itemCount = getIntent() != null ? getIntent().getIntExtra("item_count", 15) : 15;
        int weightKg = getIntent() != null ? getIntent().getIntExtra("weight_kg", BookingPricing.estimateWeightKg(itemCount)) : BookingPricing.estimateWeightKg(itemCount);
        double pricePerKg = getIntent().getDoubleExtra("price_per_kg", BookingPricing.PRICE_PER_KG);
        BookingPricing.Quote quote = BookingPricing.quote(weightKg, false, pricePerKg);
        ((TextView) findViewById(R.id.tv_base_service_value))
                .setText(BookingPricing.format(quote.laundry));
        ((TextView) findViewById(R.id.tv_subtotal_value))
                .setText(BookingPricing.format(quote.laundry));
        ((TextView) findViewById(R.id.tv_tax_value))
                .setText(BookingPricing.format(quote.serviceFee));
        ((TextView) findViewById(R.id.tv_total_value))
                .setText(BookingPricing.format(quote.total));

        String serviceName = getIntent() != null ? getIntent().getStringExtra("selected_service") : null;
        String selectedDate = getIntent() != null ? getIntent().getStringExtra("selected_date") : null;
        String selectedTime = getIntent() != null ? getIntent().getStringExtra("selected_time") : null;

        if (serviceName == null || serviceName.trim().isEmpty()) {
            serviceName = "Drop Off";
        }
        String laundryService = getIntent().getStringExtra("selected_laundry_service");
        if (laundryService != null && !laundryService.trim().isEmpty()) serviceName = laundryService;
        if (selectedDate == null || selectedDate.trim().isEmpty()) {
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault());
            selectedDate = df.format(java.util.Calendar.getInstance().getTime());
        }
        if (selectedTime == null || selectedTime.trim().isEmpty()) {
            java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());
            selectedTime = tf.format(java.util.Calendar.getInstance().getTime());
        }

        serviceTypeValueText.setText(serviceName);
        dateTimeValueText.setText(selectedDate + " • " + selectedTime);
        itemsValueText.setText(String.valueOf(itemCount));
        weightValueText.setText(weightKg + " kg");

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
            intent.putExtra("weight_kg", weightKg);
            startActivity(intent);
        });
    }
}
