package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

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

        totalAmountValue.setText("UGX 26,000");

        trackOrderButton.setOnClickListener(v -> {
            Toast.makeText(this, "Tracking is not available in demo mode", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(OrderConfirmedDropOffActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
