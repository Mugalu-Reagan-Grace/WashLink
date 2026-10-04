package com.example.washlink;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

public class OrderTrackingActivity extends AppCompatActivity {
    private ListenerRegistration registration;
    private String providerPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_customer_tracking);
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Wire bottom nav so Tracking shows as active
        BottomNavHelper.bind(this);

        TextView orderId = findViewById(R.id.tv_order_id);
        TextView status = findViewById(R.id.tv_order_status);
        MaterialButton call = findViewById(R.id.btn_call_provider);
        View cancel = findViewById(R.id.btn_cancel_booking);
        if (cancel != null) cancel.setVisibility(android.view.View.GONE);
        if (call != null) {
            call.setEnabled(false);
            call.setOnClickListener(v -> {
                if (providerPhone == null || providerPhone.trim().isEmpty()) {
                    android.widget.Toast.makeText(this, "Provider contact is unavailable.",
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + providerPhone));
                if (dial.resolveActivity(getPackageManager()) != null) startActivity(dial);
            });
        }

        String bookingId = getIntent().getStringExtra("booking_id");

        if (bookingId == null || bookingId.isEmpty()) {
            if (orderId != null) orderId.setText("No order selected");
            if (status != null) status.setText("No active booking");
            if (call != null) call.setVisibility(android.view.View.GONE);
            return;
        }

        loadBooking(bookingId, orderId, status, call, cancel);
    }

    private void loadBooking(String bookingId, TextView orderId, TextView status,
                             MaterialButton call, View cancel) {
        if (registration != null) registration.remove();
        providerPhone = null;
        if (status != null) {
            status.setText("Loading order updates...");
            status.setOnClickListener(null);
        }
        if (call != null) {
            call.setEnabled(false);
            call.setVisibility(android.view.View.GONE);
        }
        if (cancel != null) cancel.setVisibility(android.view.View.GONE);
        registration = BookingServiceFacade.getBookingService().addBookingListener(bookingId,
                new BookingService.BookingListener() {
                    @Override
                    public void onBookingLoaded(Booking booking) {
                        if (isFinishing() || isDestroyed()) return;
                        if (booking == null) {
                            if (status != null) status.setText("Unavailable");
                            return;
                        }
                        OrderTrackingRenderer.render(OrderTrackingActivity.this,
                                findViewById(android.R.id.content), booking);
                        if (cancel != null) BookingCancellationHelper.bind(
                                OrderTrackingActivity.this, cancel, booking);
                        loadProviderContact(booking, call);
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        if (orderId != null) orderId.setText("Could not load this order");
                        if (status != null) {
                            status.setText("Tap to retry loading order updates");
                            status.setOnClickListener(v ->
                                    loadBooking(bookingId, orderId, status, call, cancel));
                        }
                    }
                });
    }

    private void loadProviderContact(Booking booking, MaterialButton call) {
        if (booking.getProviderId() == null || booking.getProviderId().trim().isEmpty()) {
            setContactUnavailable(call, booking, "Provider details unavailable");
            return;
        }
        FirebaseFirestore.getInstance().collection("providers").document(booking.getProviderId()).get()
                .addOnSuccessListener(document -> {
                    if (isFinishing() || isDestroyed()) return;
                    Object phone = document.get("phone");
                    if (phone instanceof String && !((String) phone).trim().isEmpty()) {
                        providerPhone = (String) phone;
                        if (call != null) {
                            call.setText(R.string.call_provider);
                            call.setVisibility(android.view.View.VISIBLE);
                            call.setEnabled(true);
                            call.setOnClickListener(v -> {
                                Intent dial = new Intent(Intent.ACTION_DIAL,
                                        Uri.parse("tel:" + providerPhone));
                                if (dial.resolveActivity(getPackageManager()) != null) {
                                    startActivity(dial);
                                }
                            });
                        }
                    } else {
                        setContactUnavailable(call, booking, "Provider contact unavailable");
                    }
                })
                .addOnFailureListener(error ->
                        setContactUnavailable(call, booking, "Contact unavailable. Tap to retry"));
    }

    private void setContactUnavailable(MaterialButton call, Booking booking, String message) {
        if (call == null || isFinishing() || isDestroyed()) return;
        call.setText(message);
        call.setVisibility(android.view.View.VISIBLE);
        call.setEnabled(message.contains("retry"));
        call.setOnClickListener(v -> loadProviderContact(booking, call));
    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }
}
