package com.example.washlink;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class OrderTrackingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_order_tracking);

        // Wire bottom nav so Tracking shows as active
        BottomNavHelper.bind(this);

        ImageView call = findViewById(R.id.btn_call_provider);
        if (call != null) {
            call.setOnClickListener(v -> {
                // Open dialer with provider sample number (user can confirm before calling)
                Intent dial = new Intent(Intent.ACTION_DIAL);
                dial.setData(Uri.parse("tel:+256700000000"));
                startActivity(dial);
            });
        }
    }
}
