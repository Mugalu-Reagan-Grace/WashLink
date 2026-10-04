package com.example.washlink;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.FlutterwavePaymentClient;
import com.example.washlink.models.Booking;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class PaymentReturnActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);
        TextView message = new TextView(this);
        message.setText("Verifying your payment securely…");
        message.setPadding(32, 48, 32, 48);
        setContentView(message);

        Uri callback = getIntent() == null ? null : getIntent().getData();
        String bookingId = callback == null ? null : callback.getQueryParameter("bookingId");
        String transactionId = callback == null
                ? null : callback.getQueryParameter("transaction_id");
        if (FirebaseAuth.getInstance().getCurrentUser() == null
                || bookingId == null || !bookingId.matches("[A-Za-z0-9]{10,40}")) {
            Toast.makeText(this, "Could not identify the payment booking. Sign in and check order history.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (transactionId == null || !transactionId.matches("\\d{1,30}")) {
            Toast.makeText(this, "Payment was not confirmed. Your booking remains unpaid.",
                    Toast.LENGTH_LONG).show();
            openBookingConfirmation(bookingId);
            return;
        }

        FlutterwavePaymentClient.verify(bookingId, transactionId,
                status -> {
                    if (!"PAID".equals(status)) {
                        Toast.makeText(this, "Payment is not confirmed yet. Check your order status shortly.",
                                Toast.LENGTH_LONG).show();
                    }
                    openBookingConfirmation(bookingId);
                },
                error -> {
                    Toast.makeText(this, "Could not verify payment: " + error,
                            Toast.LENGTH_LONG).show();
                    openBookingConfirmation(bookingId);
                });
    }

    private void openBookingConfirmation(String bookingId) {
        FirebaseFirestore.getInstance().collection("bookings").document(bookingId).get()
                .addOnSuccessListener(snapshot -> {
                    Booking booking = snapshot.toObject(Booking.class);
                    if (booking == null) {
                        Toast.makeText(this, "Booking not found.", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    booking.setId(snapshot.getId());
                    String[] schedule = booking.getScheduledDateTime() == null
                            ? new String[0] : booking.getScheduledDateTime().split("\\s+•\\s+", 2);
                    boolean dropoff = Booking.SERVICE_TYPE_DROPOFF.equals(booking.getServiceType());
                    Intent intent = new Intent(this, dropoff
                            ? OrderConfirmedDropOffActivity.class : OrderConfirmedActivity.class);
                    intent.putExtra("booking_id", booking.getId());
                    intent.putExtra("selected_service", dropoff ? "Drop Off" : "Pickup & Delivery");
                    intent.putExtra("selected_laundry_service", booking.getServiceName());
                    intent.putExtra("selected_payment_method", booking.getPaymentMethod());
                    intent.putExtra("payment_status", booking.getPaymentStatus());
                    intent.putExtra("selected_date", schedule.length > 0 ? schedule[0] : "");
                    intent.putExtra("selected_time", schedule.length > 1 ? schedule[1] : "");
                    intent.putExtra("provider_name", booking.getProviderName());
                    intent.putExtra("provider_address", booking.getAddress());
                    intent.putExtra("selected_address", booking.getAddress());
                    intent.putExtra("quote_total", (int) Math.round(booking.getTotal()));
                    intent.putExtra("item_count", booking.getItemCount());
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(error -> {
                    Toast.makeText(this, "Could not load booking: " + error.getLocalizedMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
    }
}
