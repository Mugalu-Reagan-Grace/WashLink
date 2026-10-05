package com.example.washlink;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.data.BookingPricing;
import com.example.washlink.data.BookingService;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.example.washlink.models.UserAccount;

import java.util.Calendar;
import java.util.List;

public class ProviderReportsActivity extends AppCompatActivity {
    private ListenerRegistration registration;
    private List<Booking> bookings;
    private int rangeDays = 30;
    private TextView state;
    private String providerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);
        setContentView(R.layout.activity_provider_reports);
        findViewById(R.id.btn_report_back).setOnClickListener(v -> finish());
        ProviderNavBarHelper.bind(this, -1);
        state = findViewById(R.id.tv_report_state);
        bindFilter(R.id.filter_7_days, 7);
        bindFilter(R.id.filter_30_days, 30);
        bindFilter(R.id.filter_all_time, 0);
        updateFilterStyles();

        providerId = AuthRepository.getInstance().getCurrentUserId();
        if (providerId == null) {
            showState("Provider account is unavailable.");
            return;
        }
        listenForBookings();
    }

    private void listenForBookings() {
        if (registration != null) registration.remove();
        showState("Loading report data...");
        registration = BookingService.getInstance().addProviderBookingsListener(providerId,
                new BookingService.ProviderBookingsListener() {
                    @Override
                    public void onBookingsChanged(List<Booking> result) {
                        bookings = result;
                        renderReport();
                    }

                    @Override
                    public void onError(String message) {
                        showState("Could not load reports. Tap to retry.");
                        state.setOnClickListener(v -> listenForBookings());
                    }
                });
    }

    private void bindFilter(int viewId, int days) {
        findViewById(viewId).setOnClickListener(v -> {
            rangeDays = days;
            updateFilterStyles();
            renderReport();
        });
    }

    private void updateFilterStyles() {
        int[] ids = {R.id.filter_7_days, R.id.filter_30_days, R.id.filter_all_time};
        int[] ranges = {7, 30, 0};
        for (int i = 0; i < ids.length; i++) {
            TextView filter = findViewById(ids[i]);
            boolean selected = ranges[i] == rangeDays;
            filter.setBackgroundResource(selected
                    ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            filter.setTextColor(ContextCompat.getColor(this,
                    selected ? R.color.white : R.color.chip_unselected_text));
        }
    }

    private void renderReport() {
        if (bookings == null) return;
        state.setVisibility(View.GONE);
        long cutoff = 0;
        if (rangeDays > 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, -rangeDays);
            cutoff = calendar.getTimeInMillis();
        }

        int orders = 0;
        int delivered = 0;
        int pending = 0;
        int cancelled = 0;
        double revenue = 0;
        for (Booking booking : bookings) {
            boolean inRange = rangeDays == 0 || booking.getCreatedAt() >= cutoff;
            if (!inRange) continue;
            orders++;
            OrderStatus status = OrderStatus.fromString(booking.getStatus());
            if (status == OrderStatus.BOOKED) pending++;
            if (status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) cancelled++;
            if (status == OrderStatus.DELIVERED) {
                delivered++;
                if ("cash".equalsIgnoreCase(booking.getPaymentProvider())) {
                    revenue += booking.getTotal();
                } else if ("flutterwave".equalsIgnoreCase(booking.getPaymentProvider())
                        && "PAID".equalsIgnoreCase(booking.getPaymentStatus())) {
                    revenue += booking.getSubtotal();
                }
            }
        }

        ((TextView) findViewById(R.id.tv_report_period)).setText(rangeDays == 0
                ? "All-time performance" : "Last " + rangeDays + " days");
        ((TextView) findViewById(R.id.tv_report_bookings)).setText(String.valueOf(orders));
        ((TextView) findViewById(R.id.tv_report_completed)).setText(String.valueOf(delivered));
        ((TextView) findViewById(R.id.tv_report_pending)).setText(String.valueOf(pending));
        ((TextView) findViewById(R.id.tv_report_cancelled)).setText(String.valueOf(cancelled));
        ((TextView) findViewById(R.id.tv_report_revenue))
                .setText(BookingPricing.format((int) revenue));
    }

    private void showState(String message) {
        state.setText(message);
        state.setVisibility(View.VISIBLE);
        state.setClickable(false);
        state.setOnClickListener(null);
    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }
}
