package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class ProviderDashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_provider_dashboard);

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        // Wire provider bottom nav (highlight dashboard)
        ProviderNavBarHelper.bind(this, ProviderNavBarHelper.TAB_DASHBOARD);

        // Populate dynamic stats and recent booking (sample data for now)
        android.widget.TextView tvBookings = findViewById(R.id.tv_stat_bookings_value);
        android.widget.TextView tvPending = findViewById(R.id.tv_stat_pending_value);
        android.widget.TextView tvEarnings = findViewById(R.id.tv_stat_earnings_value);
        android.widget.TextView tvRating = findViewById(R.id.tv_stat_rating_value);
        android.widget.TextView tvBusiness = findViewById(R.id.tv_business_name);

        if (tvBookings != null) tvBookings.setText(String.valueOf(8));
        if (tvPending != null) tvPending.setText(String.valueOf(3));
        if (tvEarnings != null) tvEarnings.setText("$342");
        if (tvRating != null) tvRating.setText("4.8");
        if (tvBusiness != null) tvBusiness.setText(getString(R.string.default_business_name));

        // Recent booking sample
        android.widget.TextView tvCustomer1 = findViewById(R.id.tv_customer_name_1);
        android.widget.TextView tvService1 = findViewById(R.id.tv_service_1);
        if (tvCustomer1 != null) tvCustomer1.setText(getString(R.string.sample_customer_name_1));
        if (tvService1 != null) tvService1.setText(getString(R.string.sample_service_1));
    }
}
