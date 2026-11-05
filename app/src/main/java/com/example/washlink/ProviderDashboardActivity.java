package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.data.BookingPricing;
import com.example.washlink.data.BookingService;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.data.PushTokenManager;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.example.washlink.models.UserAccount;
import com.example.washlink.ui.ThemeManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProviderDashboardActivity extends WashLinkActivity {
    private ListenerRegistration bookingsRegistration;
    private com.google.firebase.firestore.ListenerRegistration profileRegistration;
    private String providerId;
    private LinearLayout recentBookings;
    private LinearLayout activityChart;
    private LinearLayout customerList;
    private int rangeDays = 30;
    private List<Booking> currentBookings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.setActiveRole(this, ThemeManager.ROLE_PROVIDER);
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
        findViewById(R.id.iv_chat_inbox).setOnClickListener(v ->
                startActivity(new Intent(this, ChatInboxActivity.class)));
        recentBookings = findViewById(R.id.ll_dashboard_bookings);
        activityChart = findViewById(R.id.ll_provider_activity_chart);
        customerList = findViewById(R.id.provider_customer_list);
        bindRangeFilter(R.id.dashboard_filter_7_days, 7);
        bindRangeFilter(R.id.dashboard_filter_30_days, 30);
        bindRangeFilter(R.id.dashboard_filter_90_days, 90);
        updateRangeFilterStyles();
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
                        currentBookings = bookings;
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
        if (profileRegistration != null) profileRegistration.remove();
        profileRegistration = FirebaseFirestore.getInstance().collection("providers")
                .document(providerId).addSnapshotListener((document, error) -> {
                    if (error != null || document == null || !document.exists()) {
                        TextView approval = findViewById(R.id.tv_provider_approval);
                        if (approval != null) {
                            approval.setText(R.string.provider_approval_unavailable);
                            approval.setTextColor(getColor(R.color.pending_text));
                        }
                        return;
                    }
                    Object name = document.get("businessName");
                    TextView business = findViewById(R.id.tv_business_name);
                    if (business != null && name instanceof String && !((String) name).trim().isEmpty()) {
                        business.setText((String) name);
                    }
                    TextView approval = findViewById(R.id.tv_provider_approval);
                    if (approval != null) {
                        boolean approved = Boolean.TRUE.equals(document.get("isApproved"));
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
                });
    }

    private void renderBookings(List<Booking> bookings) {
        long now = System.currentTimeMillis();
        long rangeStart = now - rangeDays * 24L * 60L * 60L * 1000L;
        Calendar startOfToday = Calendar.getInstance();
        startOfToday.set(Calendar.HOUR_OF_DAY, 0);
        startOfToday.set(Calendar.MINUTE, 0);
        startOfToday.set(Calendar.SECOND, 0);
        startOfToday.set(Calendar.MILLISECOND, 0);
        int todayCount = 0;
        int pendingCount = 0;
        double periodEarnings = 0;
        List<Booking> periodBookings = new ArrayList<>();
        for (Booking booking : bookings) {
            if (booking.getCreatedAt() >= startOfToday.getTimeInMillis()
                    && booking.getCreatedAt() <= now) todayCount++;
            OrderStatus status = OrderStatus.fromString(booking.getStatus());
            if (status == OrderStatus.BOOKED) pendingCount++;
            if (booking.getCreatedAt() >= rangeStart && booking.getCreatedAt() <= now) {
                periodBookings.add(booking);
            }
            if (status == OrderStatus.DELIVERED && booking.getUpdatedAt() >= rangeStart
                    && booking.getUpdatedAt() <= now) {
                periodEarnings += verifiedRevenue(booking);
            }
        }

        TextView bookingsValue = findViewById(R.id.tv_stat_bookings_value);
        TextView pendingValue = findViewById(R.id.tv_stat_pending_value);
        TextView earningsValue = findViewById(R.id.tv_stat_earnings_value);
        if (bookingsValue != null) bookingsValue.setText(String.valueOf(todayCount));
        if (pendingValue != null) pendingValue.setText(String.valueOf(pendingCount));
        if (earningsValue != null) earningsValue.setText(BookingPricing.format((int) periodEarnings));
        TextView earningsLabel = findViewById(R.id.tv_stat_earnings_label);
        if (earningsLabel != null) earningsLabel.setText("Collected · last " + rangeDays + " days");
        TextView activityTitle = findViewById(R.id.tv_provider_activity_title);
        if (activityTitle != null) activityTitle.setText("Order activity · last " + rangeDays + " days");
        TextView activityTotal = findViewById(R.id.tv_provider_activity_total);
        if (activityTotal != null) activityTotal.setText(periodBookings.size()
                + (periodBookings.size() == 1 ? " order" : " orders"));
        TextView customerRange = findViewById(R.id.tv_provider_customers_range);
        if (customerRange != null) customerRange.setText("Last " + rangeDays + " days");
        renderActivityChart(periodBookings, rangeStart, now);
        renderFrequentCustomers(periodBookings);

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
                    R.layout.activity_provider_dashboard_booking_item, recentBookings, false);
            bindRecentBooking(row, booking);
            recentBookings.addView(row);
        }
    }

            private void bindRangeFilter(int viewId, int days) {
                findViewById(viewId).setOnClickListener(v -> {
                    rangeDays = days;
                    updateRangeFilterStyles();
                    if (bookingsRegistration != null) {
                        // Re-rendering uses the latest live snapshot retained by the listener.
                        renderBookingsSnapshot();
                    }
                });
            }

            private void renderBookingsSnapshot() {
                if (currentBookings != null) renderBookings(currentBookings);
            }

            private void updateRangeFilterStyles() {
                int[] ids = {R.id.dashboard_filter_7_days, R.id.dashboard_filter_30_days,
                        R.id.dashboard_filter_90_days};
                int[] ranges = {7, 30, 90};
                for (int i = 0; i < ids.length; i++) {
                    TextView filter = findViewById(ids[i]);
                    boolean selected = ranges[i] == rangeDays;
                    filter.setBackgroundResource(selected
                            ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
                    filter.setTextColor(ContextCompat.getColor(this,
                            selected ? R.color.on_accent : R.color.chip_unselected_text));
                    filter.setSelected(selected);
                }
            }

            private void renderActivityChart(List<Booking> periodBookings, long rangeStart, long now) {
                activityChart.removeAllViews();
                int[] totals = new int[7];
                int[] pending = new int[7];
                for (Booking booking : periodBookings) {
                    int index = (int) ((booking.getCreatedAt() - rangeStart)
                            / (double) Math.max(1L, now - rangeStart) * totals.length);
                    index = Math.max(0, Math.min(totals.length - 1, index));
                    totals[index]++;
                    if (OrderStatus.fromString(booking.getStatus()) == OrderStatus.BOOKED) pending[index]++;
                }
                int maxTotal = 1;
                for (int total : totals) maxTotal = Math.max(maxTotal, total);
                SimpleDateFormat labelFormat = new SimpleDateFormat(
                        rangeDays <= 7 ? "EEE" : "d MMM", Locale.getDefault());
                int blue = ContextCompat.getColor(this, R.color.laundr_blue);
                int amber = ContextCompat.getColor(this, R.color.pending_text);
                for (int i = 0; i < totals.length; i++) {
                    LinearLayout column = new LinearLayout(this);
                    column.setOrientation(LinearLayout.VERTICAL);
                    column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                    LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                    column.setLayoutParams(columnParams);

                    TextView count = new TextView(this);
                    count.setText(String.valueOf(totals[i]));
                    count.setTextSize(10);
                    count.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    count.setGravity(Gravity.CENTER);
                    column.addView(count, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 20));

                    FrameLayout barFrame = new FrameLayout(this);
                    LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 104);
                    frameParams.gravity = Gravity.CENTER_HORIZONTAL;
                    column.addView(barFrame, frameParams);
                    int barHeight = totals[i] == 0 ? 2 : Math.max(5, totals[i] * 82 / maxTotal);
                    int barWidth = (int) (12 * getResources().getDisplayMetrics().density);
                    FrameLayout.LayoutParams totalParams = new FrameLayout.LayoutParams(
                            barWidth, barHeight, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                    View totalBar = new View(this);
                    totalBar.setBackgroundColor(blue);
                    barFrame.addView(totalBar, totalParams);
                    if (pending[i] > 0) {
                        int pendingHeight = Math.max(2, barHeight * pending[i] / totals[i]);
                        View pendingBar = new View(this);
                        pendingBar.setBackgroundColor(amber);
                        barFrame.addView(pendingBar, new FrameLayout.LayoutParams(
                                barWidth, pendingHeight, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
                    }

                    long bucketTime = rangeStart + (long) ((now - rangeStart) / 7.0 * (i + 0.5));
                    TextView label = new TextView(this);
                    label.setText(labelFormat.format(new Date(bucketTime)));
                    label.setTextSize(9);
                    label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    label.setGravity(Gravity.CENTER);
                    column.addView(label, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 22));
                    activityChart.addView(column);
                }
            }

            private void renderFrequentCustomers(List<Booking> periodBookings) {
                customerList.removeAllViews();
                Map<String, CustomerSummary> summaries = new HashMap<>();
                for (Booking booking : periodBookings) {
                    String name = booking.getCustomerName() == null
                            || booking.getCustomerName().trim().isEmpty()
                            ? "Customer" : booking.getCustomerName().trim();
                    String key = booking.getCustomerId() == null || booking.getCustomerId().trim().isEmpty()
                            ? name : booking.getCustomerId();
                    CustomerSummary summary = summaries.get(key);
                    if (summary == null) {
                        summary = new CustomerSummary(name);
                        summaries.put(key, summary);
                    }
                    summary.orders++;
                    summary.revenue += verifiedRevenue(booking);
                }
                List<CustomerSummary> ranked = new ArrayList<>(summaries.values());
                Collections.sort(ranked, new Comparator<CustomerSummary>() {
                    @Override
                    public int compare(CustomerSummary first, CustomerSummary second) {
                        int byOrders = Integer.compare(second.orders, first.orders);
                        return byOrders != 0 ? byOrders : first.name.compareToIgnoreCase(second.name);
                    }
                });
                if (ranked.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText("No customer bookings in this period.");
                    empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    empty.setTextSize(12);
                    empty.setPadding(0, 14, 0, 8);
                    customerList.addView(empty);
                    return;
                }
                int limit = Math.min(5, ranked.size());
                for (int i = 0; i < limit; i++) {
                    CustomerSummary customer = ranked.get(i);
                    LinearLayout row = new LinearLayout(this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, 12, 0, 12);
                    TextView rank = new TextView(this);
                    rank.setText(String.valueOf(i + 1));
                    rank.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    rank.setTextSize(11);
                    rank.setGravity(Gravity.CENTER);
                    row.addView(rank, new LinearLayout.LayoutParams(dp(28), -2));
                    LinearLayout details = new LinearLayout(this);
                    details.setOrientation(LinearLayout.VERTICAL);
                    TextView customerName = new TextView(this);
                    customerName.setText(customer.name);
                    customerName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    customerName.setTextSize(12);
                    customerName.setMaxLines(1);
                    customerName.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    TextView orderCount = new TextView(this);
                    orderCount.setText(customer.orders + (customer.orders == 1 ? " booking" : " bookings"));
                    orderCount.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    orderCount.setTextSize(10);
                    details.addView(customerName);
                    details.addView(orderCount);
                    row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView revenue = new TextView(this);
                    revenue.setText(BookingPricing.format((int) customer.revenue));
                    revenue.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    revenue.setTextSize(10);
                    row.addView(revenue);
                    customerList.addView(row);
                }
            }

            private double verifiedRevenue(Booking booking) {
                if ("cash".equalsIgnoreCase(booking.getPaymentProvider())) return booking.getTotal();
                if (("flutterwave".equalsIgnoreCase(booking.getPaymentProvider())
                        || "pesapal".equalsIgnoreCase(booking.getPaymentProvider()))
                        && "PAID".equalsIgnoreCase(booking.getPaymentStatus())) {
                    return booking.getSubtotal();
                }
                return 0;
            }

            private int dp(int value) {
                return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
            }

            private static class CustomerSummary {
                final String name;
                int orders;
                double revenue;

                CustomerSummary(String name) {
                    this.name = name;
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
        if (profileRegistration != null) profileRegistration.remove();
        super.onDestroy();
    }
}
