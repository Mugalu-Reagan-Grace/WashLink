package com.example.washlink;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import android.widget.TextView;

public class OrderTrackingActivity extends AppCompatActivity {
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_order_tracking);

        // Wire bottom nav so Tracking shows as active
        BottomNavHelper.bind(this);

        String bookingId = getIntent().getStringExtra("booking_id");
        if (bookingId != null && !bookingId.isEmpty()) {
            TextView id = findViewById(R.id.tv_order_id);
            TextView status = findViewById(R.id.tv_order_status);
            registration = BookingServiceFacade.getBookingService().addBookingListener(bookingId,
                    new BookingService.BookingListener() {
                        @Override
                        public void onBookingLoaded(Booking booking) {
                            id.setText("Order #" + booking.getId());
                            status.setText(booking.getStatus().replace('_', ' '));
                        }

                        @Override
                        public void onError(String message) {
                            status.setText("Unavailable");
                        }
                    });
        }

        MaterialButton call = findViewById(R.id.btn_call_provider);
        if (call != null) {
            call.setOnClickListener(v -> {
                // Open dialer with provider sample number (user can confirm before calling)
                Intent dial = new Intent(Intent.ACTION_DIAL);
                dial.setData(Uri.parse("tel:+256700000000"));
                startActivity(dial);
            });
        }

    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }
}
