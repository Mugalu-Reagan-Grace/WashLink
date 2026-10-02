package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.data.BookingPricing;
import com.example.washlink.data.BookingService;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.data.PushTokenManager;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.example.washlink.models.UserAccount;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.List;

public class ProviderDashboardActivity extends AppCompatActivity {
    private ListenerRegistration bookingsRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_provider_dashboard);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);
        NotificationPermissionHelper.requestIfNeeded(this);
        PushTokenManager.registerCurrentUser();

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        // Wire provider bottom nav (highlight dashboard)
        ProviderNavBarHelper.bind(this, ProviderNavBarHelper.TAB_DASHBOARD);

        findViewById(R.id.tv_view_all).setOnClickListener(v ->
                startActivity(new Intent(this, BookingRequestsActivity.class)));
        TextView rating = findViewById(R.id.tv_stat_rating_value);
        if (rating != null) rating.setText("—");

        String providerId = AuthRepository.getInstance().getCurrentUserId();
        if (providerId == null) {
            showBookingsState("Sign in as a provider to load dashboard data.");
            return;
        }
        loadBusinessName(providerId);
        showBookingsState("Loading provider bookings...");
        bookingsRegistration = BookingService.getInstance().addProviderBookingsListener(
                providerId, new BookingService.ProviderBookingsListener() {
                    @Override
                    public void onBookingsChanged(List<Booking> bookings) {
                        renderBookings(bookings);
                    }

                    @Override
                    public void onError(String message) {
                        showBookingsState("Could not load dashboard data. " + message);
                    }
                });
    }

    private void loadBusinessName(String providerId) {
        FirebaseFirestore.getInstance().collection("providers").document(providerId).get()
                .addOnSuccessListener(document -> {
                    Object name = document.get("businessName");
                    TextView business = findViewById(R.id.tv_business_name);
                    if (business != null && name instanceof String && !((String) name).trim().isEmpty()) {
                        business.setText((String) name);
                    }
                });
    }

    private void renderBookings(List<Booking> bookings) {
        long now = System.currentTimeMillis();
        Calendar startOfToday = Calendar.getInstance();
        startOfToday.set(Calendar.HOUR_OF_DAY, 0);
        startOfToday.set(Calendar.MINUTE, 0);
        startOfToday.set(Calendar.SECOND, 0);
        startOfToday.set(Calendar.MILLISECOND, 0);
        Calendar weekAgo = Calendar.getInstance();
        weekAgo.add(Calendar.DAY_OF_YEAR, -7);

        int todayCount = 0;
        int pendingCount = 0;
        double weeklyEarnings = 0;
        Booking recent = bookings.isEmpty() ? null : bookings.get(0);
        for (Booking booking : bookings) {
            if (booking.getCreatedAt() >= startOfToday.getTimeInMillis()
                    && booking.getCreatedAt() <= now) todayCount++;
            OrderStatus status = OrderStatus.fromString(booking.getStatus());
            if (status == OrderStatus.BOOKED) pendingCount++;
            if (status == OrderStatus.DELIVERED
                    && booking.getUpdatedAt() >= weekAgo.getTimeInMillis()) {
                weeklyEarnings += booking.getTotal();
            }
        }

        TextView bookingsValue = findViewById(R.id.tv_stat_bookings_value);
        TextView pendingValue = findViewById(R.id.tv_stat_pending_value);
        TextView earningsValue = findViewById(R.id.tv_stat_earnings_value);
        if (bookingsValue != null) bookingsValue.setText(String.valueOf(todayCount));
        if (pendingValue != null) pendingValue.setText(String.valueOf(pendingCount));
        if (earningsValue != null) earningsValue.setText(BookingPricing.format((int) weeklyEarnings));

        View row = findViewById(R.id.booking_row_1);
        TextView state = findViewById(R.id.tv_dashboard_bookings_state);
        if (recent == null) {
            if (row != null) row.setVisibility(View.GONE);
            if (state != null) {
                state.setText("No bookings yet.");
                state.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (row != null) {
            row.setVisibility(View.VISIBLE);
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, UpdateOrderStatusActivity.class);
                intent.putExtra("booking_id", recent.getId());
                startActivity(intent);
            });
        }
        if (state != null) state.setVisibility(View.GONE);
        TextView customer = findViewById(R.id.tv_customer_name_1);
        TextView service = findViewById(R.id.tv_service_1);
        TextView status = findViewById(R.id.tv_status_1);
        if (customer != null) customer.setText(recent.getCustomerName() == null
                ? "Customer booking" : recent.getCustomerName());
        if (service != null) service.setText(recent.getServiceName() == null
                ? "Laundry service" : recent.getServiceName());
        if (status != null) status.setText(OrderStatus.fromString(recent.getStatus()).getDisplayName());
    }

    private void showBookingsState(String message) {
        View row = findViewById(R.id.booking_row_1);
        TextView state = findViewById(R.id.tv_dashboard_bookings_state);
        if (row != null) row.setVisibility(View.GONE);
        if (state != null) {
            state.setText(message);
            state.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        if (bookingsRegistration != null) bookingsRegistration.remove();
        super.onDestroy();
    }
}
