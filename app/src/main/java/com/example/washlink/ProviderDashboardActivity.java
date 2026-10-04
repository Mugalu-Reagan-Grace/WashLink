package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
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
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class ProviderDashboardActivity extends AppCompatActivity {
    private ListenerRegistration bookingsRegistration;
    private String providerId;
    private LinearLayout recentBookings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_provider_dashboard);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);
        NotificationPermissionHelper.requestIfNeeded(this);
        PushTokenManager.registerCurrentUser();

        // Wire provider bottom nav (highlight dashboard)
        ProviderNavBarHelper.bind(this, ProviderNavBarHelper.TAB_DASHBOARD);

        findViewById(R.id.tv_view_all).setOnClickListener(v ->
                startActivity(new Intent(this, BookingRequestsActivity.class)));
        findViewById(R.id.btn_provider_reports).setOnClickListener(v ->
                startActivity(new Intent(this, ProviderReportsActivity.class)));
        recentBookings = findViewById(R.id.ll_dashboard_bookings);
        TextView rating = findViewById(R.id.tv_stat_rating_value);
        if (rating != null) rating.setText("—");

        providerId = AuthRepository.getInstance().getCurrentUserId();
        if (providerId == null) {
            showBookingsState("Sign in as a provider to load dashboard data.");
            return;
        }
        loadBusinessName(providerId);
        listenForBookings();
    }

    private void listenForBookings() {
        if (bookingsRegistration != null) {
            bookingsRegistration.remove();
            bookingsRegistration = null;
        }
        showBookingsState("Loading provider bookings...");
        bookingsRegistration = BookingService.getInstance().addProviderBookingsListener(
                providerId, new BookingService.ProviderBookingsListener() {
                    @Override
                    public void onBookingsChanged(List<Booking> bookings) {
                        renderBookings(bookings);
                    }

                    @Override
                    public void onError(String message) {
                        showBookingsState("Could not load dashboard data. Tap to retry.");
                        TextView state = findViewById(R.id.tv_dashboard_bookings_state);
                        if (state != null) {
                            state.setClickable(true);
                            state.setOnClickListener(v -> listenForBookings());
                        }
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
                    TextView approval = findViewById(R.id.tv_provider_approval);
                    if (approval != null) {
                        boolean approved = !Boolean.FALSE.equals(document.get("isApproved"));
                        boolean isOpen = !Boolean.FALSE.equals(document.get("isOpen"));
                        approval.setText(!approved
                                ? R.string.provider_approval_pending
                                : isOpen ? R.string.provider_status_accepting
                                : R.string.provider_status_paused);
                        approval.setTextColor(getColor(!approved
                                ? R.color.pending_text
                                : isOpen ? R.color.success_green : R.color.text_secondary));
                    }
                    Object ratingValue = document.get("rating");
                    Object reviewCount = document.get("reviewCount");
                    TextView rating = findViewById(R.id.tv_stat_rating_value);
                    TextView reviews = findViewById(R.id.tv_stat_rating_reviews);
                    if (rating != null && ratingValue instanceof Number
                            && reviewCount instanceof Number
                            && ((Number) reviewCount).intValue() > 0) {
                        rating.setText(String.format(java.util.Locale.getDefault(), "%.1f",
                                ((Number) ratingValue).doubleValue()));
                        if (reviews != null) {
                            reviews.setText(((Number) reviewCount).intValue() == 1
                                    ? getString(R.string.provider_single_review)
                                    : getString(R.string.provider_review_count,
                                    ((Number) reviewCount).intValue()));
                        }
                    } else if (reviews != null) {
                        reviews.setText(R.string.provider_no_reviews);
                    }
                })
                .addOnFailureListener(error -> showBookingsState(
                        "Could not load provider profile: " + error.getMessage()));
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
        for (Booking booking : bookings) {
            if (booking.getCreatedAt() >= startOfToday.getTimeInMillis()
                    && booking.getCreatedAt() <= now) todayCount++;
            OrderStatus status = OrderStatus.fromString(booking.getStatus());
            if (status == OrderStatus.BOOKED) pendingCount++;
            if (status == OrderStatus.DELIVERED
                    && booking.getUpdatedAt() >= weekAgo.getTimeInMillis()) {
                if ("cash".equalsIgnoreCase(booking.getPaymentProvider())) {
                    weeklyEarnings += booking.getTotal();
                } else if ("flutterwave".equalsIgnoreCase(booking.getPaymentProvider())
                        && "PAID".equalsIgnoreCase(booking.getPaymentStatus())) {
                    weeklyEarnings += booking.getSubtotal();
                }
            }
        }

        TextView bookingsValue = findViewById(R.id.tv_stat_bookings_value);
        TextView pendingValue = findViewById(R.id.tv_stat_pending_value);
        TextView earningsValue = findViewById(R.id.tv_stat_earnings_value);
        if (bookingsValue != null) bookingsValue.setText(String.valueOf(todayCount));
        if (pendingValue != null) pendingValue.setText(String.valueOf(pendingCount));
        if (earningsValue != null) earningsValue.setText(BookingPricing.format((int) weeklyEarnings));

        TextView state = findViewById(R.id.tv_dashboard_bookings_state);
        recentBookings.removeAllViews();
        if (bookings.isEmpty()) {
            if (state != null) {
                state.setText(R.string.provider_no_recent_bookings);
                state.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (state != null) state.setVisibility(View.GONE);
        int visibleCount = Math.min(5, bookings.size());
        for (int i = 0; i < visibleCount; i++) {
            Booking booking = bookings.get(i);
            View row = getLayoutInflater().inflate(
                    R.layout.item_provider_dashboard_booking, recentBookings, false);
            bindRecentBooking(row, booking);
            recentBookings.addView(row);
        }
    }

    private void showBookingsState(String message) {
        TextView state = findViewById(R.id.tv_dashboard_bookings_state);
        recentBookings.removeAllViews();
        if (state != null) {
            state.setText(message);
            state.setVisibility(View.VISIBLE);
            state.setClickable(false);
            state.setOnClickListener(null);
        }
    }

    private void bindRecentBooking(View row, Booking booking) {
        TextView customer = row.findViewById(R.id.tv_customer);
        TextView service = row.findViewById(R.id.tv_booking_service);
        TextView total = row.findViewById(R.id.tv_booking_total);
        TextView schedule = row.findViewById(R.id.tv_booking_schedule);
        TextView status = row.findViewById(R.id.tv_booking_status);

        customer.setText(booking.getCustomerName() == null
                || booking.getCustomerName().trim().isEmpty()
                ? getString(R.string.provider_booking_customer_fallback)
                : booking.getCustomerName());
        service.setText(booking.getServiceName() == null
                || booking.getServiceName().trim().isEmpty()
                ? getString(R.string.provider_booking_service_fallback)
                : booking.getServiceName());
        total.setText(BookingPricing.format((int) booking.getTotal()));
        String scheduled = booking.getScheduledDateTime();
        schedule.setText(scheduled == null || scheduled.trim().isEmpty()
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(booking.getCreatedAt()))
                : scheduled);

        OrderStatus orderStatus = OrderStatus.fromString(booking.getStatus());
        status.setText(orderStatus.getDisplayName());
        int statusColor = orderStatus == OrderStatus.BOOKED
                ? R.color.pending_text
                : orderStatus == OrderStatus.DELIVERED
                ? R.color.success_green
                : orderStatus == OrderStatus.REJECTED || orderStatus == OrderStatus.CANCELLED
                ? R.color.reject_red
                : R.color.status_processing_text;
        status.setTextColor(getColor(statusColor));

        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, UpdateOrderStatusActivity.class);
            intent.putExtra("booking_id", booking.getId());
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        if (bookingsRegistration != null) bookingsRegistration.remove();
        super.onDestroy();
    }
}
