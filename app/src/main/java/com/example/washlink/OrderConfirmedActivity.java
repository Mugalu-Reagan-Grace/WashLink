package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

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
        trackOrderButton = findViewById(R.id.btn_track_order);

        if (getIntent() != null) {
            String service = getIntent().getStringExtra("selected_service");
            String paymentMethod = getIntent().getStringExtra("selected_payment_method");
            if (service != null && !service.trim().isEmpty()) {
                serviceTypeValue.setText(service);
            }
            if (paymentMethod != null && !paymentMethod.trim().isEmpty()) {
                paymentMethodValue.setText(paymentMethod);
            }
        }

        totalAmountValue.setText(String.format(Locale.US, "UGX %,d", 26000));

        trackOrderButton.setOnClickListener(v -> {
            Toast.makeText(this, "Tracking is not available in demo mode", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(OrderConfirmedActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
