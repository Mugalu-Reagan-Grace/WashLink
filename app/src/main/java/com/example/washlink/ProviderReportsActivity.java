package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.data.BookingPricing;
import com.example.washlink.data.BookingService;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.example.washlink.models.UserAccount;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProviderReportsActivity extends WashLinkActivity {
    private ListenerRegistration registration;
    private List<Booking> bookings;
    private int rangeDays = 30;
    private int reportType;
    private TextView state;
    private TextView resultCount;
    private LinearLayout reportRows;
    private View paymentBreakdown;
    private EditText searchInput;
    private Spinner paymentFilter;
    private Spinner statusFilter;
    private Spinner sortFilter;
    private List<Booking> visibleBookings = new ArrayList<>();
    private String providerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);
        setContentView(R.layout.activity_provider_reports);
        findViewById(R.id.btn_report_back).setOnClickListener(v -> finish());
        ProviderNavBarHelper.bind(this, -1);
        state = findViewById(R.id.tv_report_state);
        resultCount = findViewById(R.id.tv_report_result_count);
        reportRows = findViewById(R.id.ll_report_rows);
        paymentBreakdown = findViewById(R.id.report_payment_card);
        searchInput = findViewById(R.id.et_report_search);
        paymentFilter = findViewById(R.id.spinner_report_payment);
        statusFilter = findViewById(R.id.spinner_report_status);
        sortFilter = findViewById(R.id.spinner_report_sort);
        bindFilter(R.id.filter_7_days, 7);
        bindFilter(R.id.filter_30_days, 30);
        bindFilter(R.id.filter_all_time, 0);
        bindReportTab(R.id.report_tab_earnings, 0);
        bindReportTab(R.id.report_tab_orders, 1);
        bindReportTab(R.id.report_tab_services, 2);
        bindReportTab(R.id.report_tab_customers, 3);
        setupFilters();
        updateFilterStyles();

        providerId = AuthRepository.getInstance().getCurrentUserId();
        if (providerId == null) {
            showState("Provider account is unavailable.");
            return;
        }
        listenForBookings();
    }

    private void setupFilters() {
        setSpinnerItems(paymentFilter, new String[]{"All payment methods", "Cash", "Verified online"});
        setSpinnerItems(statusFilter, new String[]{"All statuses", "Booked", "Accepted", "Picked up",
                "Washing", "Drying", "Ready", "Out for delivery", "Delivered", "Rejected", "Cancelled"});
        updateSortOptions();
        android.widget.AdapterView.OnItemSelectedListener listener =
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                               int position, long id) {
                        renderReport();
                    }

                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) { }
                };
        paymentFilter.setOnItemSelectedListener(listener);
        statusFilter.setOnItemSelectedListener(listener);
        sortFilter.setOnItemSelectedListener(listener);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderReport();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        findViewById(R.id.btn_report_export).setOnClickListener(v -> shareReport());
    }

    private void setSpinnerItems(Spinner spinner, String[] labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void listenForBookings() {
        if (registration != null) registration.remove();
        showState("Loading report data...");
        registration = BookingService.getInstance().addProviderBookingsListener(providerId,
                new BookingService.ProviderBookingsListener() {
                    @Override
                    public void onBookingsChanged(List<Booking> result) {
                        bookings = result;
                        state.setVisibility(View.GONE);
                        renderReport();
                    }

                    @Override
                    public void onError(String message) {
                        showState("Could not load reports: " + message + " Tap to retry.");
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

    private void bindReportTab(int viewId, int type) {
        findViewById(viewId).setOnClickListener(v -> {
            reportType = type;
            updateReportTabStyles();
            updateSortOptions();
            renderReport();
        });
    }

    private void updateSortOptions() {
        String[] options = reportType >= 2
                ? new String[]{"Most bookings", "Name A-Z", "Highest revenue"}
                : new String[]{"Newest", "Oldest", "Highest amount"};
        int selected = sortFilter == null ? 0 : sortFilter.getSelectedItemPosition();
        setSpinnerItems(sortFilter, options);
        if (selected < options.length) sortFilter.setSelection(selected);
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
                    selected ? R.color.on_accent : R.color.chip_unselected_text));
            filter.setSelected(selected);
        }
    }

    private void updateReportTabStyles() {
        int[] ids = {R.id.report_tab_earnings, R.id.report_tab_orders,
                R.id.report_tab_services, R.id.report_tab_customers};
        for (int i = 0; i < ids.length; i++) {
            TextView tab = findViewById(ids[i]);
            boolean selected = i == reportType;
            tab.setBackgroundResource(selected
                    ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            tab.setTextColor(ContextCompat.getColor(this,
                    selected ? R.color.on_accent : R.color.chip_unselected_text));
            tab.setSelected(selected);
        }
        paymentBreakdown.setVisibility(reportType == 0 ? View.VISIBLE : View.GONE);
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

        List<Booking> period = new ArrayList<>();
        int delivered = 0;
        int pending = 0;
        int cancelled = 0;
        double cashRevenue = 0;
        double onlineRevenue = 0;
        for (Booking booking : bookings) {
            if (rangeDays > 0 && booking.getCreatedAt() < cutoff) continue;
            period.add(booking);
            OrderStatus status = OrderStatus.fromString(booking.getStatus());
            if (status == OrderStatus.BOOKED) pending++;
            if (status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) cancelled++;
            if (status == OrderStatus.DELIVERED) {
                delivered++;
                if ("cash".equalsIgnoreCase(booking.getPaymentProvider())) {
                    cashRevenue += booking.getTotal();
                } else if (("flutterwave".equalsIgnoreCase(booking.getPaymentProvider())
                        || "pesapal".equalsIgnoreCase(booking.getPaymentProvider()))
                        && "PAID".equalsIgnoreCase(booking.getPaymentStatus())) {
                    onlineRevenue += booking.getSubtotal();
                }
            }
        }
        double revenue = cashRevenue + onlineRevenue;
        ((TextView) findViewById(R.id.tv_report_period)).setText(rangeDays == 0
                ? "All-time performance" : "Last " + rangeDays + " days");
        ((TextView) findViewById(R.id.tv_report_bookings)).setText(String.valueOf(period.size()));
        ((TextView) findViewById(R.id.tv_report_completed)).setText(String.valueOf(delivered));
        ((TextView) findViewById(R.id.tv_report_pending)).setText(String.valueOf(pending));
        ((TextView) findViewById(R.id.tv_report_cancelled)).setText(String.valueOf(cancelled));
        ((TextView) findViewById(R.id.tv_report_revenue))
                .setText(BookingPricing.format((int) revenue));
        ((TextView) findViewById(R.id.tv_report_cash_revenue))
                .setText(BookingPricing.format((int) cashRevenue));
        ((TextView) findViewById(R.id.tv_report_online_revenue))
                .setText(BookingPricing.format((int) onlineRevenue));

        List<Booking> filtered = new ArrayList<>();
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.getDefault());
        int payment = paymentFilter.getSelectedItemPosition();
        int selectedStatus = statusFilter.getSelectedItemPosition();
        for (Booking booking : period) {
            if (payment == 1 && !"cash".equalsIgnoreCase(booking.getPaymentProvider())) continue;
            if (payment == 2 && !"flutterwave".equalsIgnoreCase(booking.getPaymentProvider())
                    && !"pesapal".equalsIgnoreCase(booking.getPaymentProvider())) continue;
            if (selectedStatus > 0 && !OrderStatus.fromString(booking.getStatus())
                    .getDisplayName().equalsIgnoreCase(statusFilter.getSelectedItem().toString())) continue;
            if (reportType == 0 && verifiedRevenue(booking) <= 0) continue;
            if (!query.isEmpty() && !matchesSearch(booking, query)) continue;
            filtered.add(booking);
        }
        Collections.sort(filtered, new Comparator<Booking>() {
            @Override
            public int compare(Booking first, Booking second) {
                int sort = sortFilter.getSelectedItemPosition();
                if (sort == 2) {
                    return Double.compare(bookingRevenue(second), bookingRevenue(first));
                }
                return sort == 1
                        ? Long.compare(first.getCreatedAt(), second.getCreatedAt())
                        : Long.compare(second.getCreatedAt(), first.getCreatedAt());
            }
        });
        visibleBookings = filtered;
        ((TextView) findViewById(R.id.tv_report_result_count)).setText(
                filtered.size() + (filtered.size() == 1 ? " record" : " records"));
        renderRows(filtered);
    }

    private void renderRows(List<Booking> filtered) {
        reportRows.removeAllViews();
        if (reportType == 2) {
            renderGroupedRows(filtered, false);
            return;
        }
        if (reportType == 3) {
            renderGroupedRows(filtered, true);
            return;
        }
        if (filtered.isEmpty()) {
            addEmptyReportMessage();
            return;
        }
        for (Booking booking : filtered) {
            String id = booking.getId() == null ? "Booking" : "#" + booking.getId();
            String date = DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(booking.getCreatedAt());
            String payment = booking.getPaymentProvider() == null
                    ? "Payment unknown" : booking.getPaymentProvider();
            String detail = booking.getCustomerName() + " · " + safe(booking.getServiceName())
                    + " · " + payment + " · " + date;
            addReportRow(id, detail, BookingPricing.format((int) (reportType == 0
                    ? verifiedRevenue(booking) : booking.getTotal())),
                    OrderStatus.fromString(booking.getStatus()).getDisplayName());
        }
    }

    private void renderGroupedRows(List<Booking> filtered, boolean customers) {
        Map<String, ReportSummary> summaries = new HashMap<>();
        for (Booking booking : filtered) {
            String name = customers ? safe(booking.getCustomerName()) : safe(booking.getServiceName());
            if (name.isEmpty()) name = customers ? "Customer" : "Laundry service";
            String key = customers && booking.getCustomerId() != null
                    ? booking.getCustomerId() : name;
            ReportSummary summary = summaries.get(key);
            if (summary == null) {
                summary = new ReportSummary(name);
                summaries.put(key, summary);
            }
            summary.count++;
            summary.items += booking.getItemCount();
            summary.revenue += verifiedRevenue(booking);
        }
        List<ReportSummary> ranked = new ArrayList<>(summaries.values());
        Collections.sort(ranked, (first, second) -> {
            int sort = sortFilter.getSelectedItemPosition();
            if (sort == 2) return Double.compare(second.revenue, first.revenue);
            if (sort == 1) return first.name.compareToIgnoreCase(second.name);
            return Integer.compare(second.count, first.count);
        });
        if (ranked.isEmpty()) {
            addEmptyReportMessage();
            return;
        }
        for (ReportSummary summary : ranked) {
            String detail = summary.count + (summary.count == 1 ? " booking" : " bookings");
            if (customers) detail += " · " + summary.items + " items";
            addReportRow(summary.name, detail, BookingPricing.format((int) summary.revenue), null);
        }
    }

    private void addReportRow(String title, String subtitle, String amount, String statusText) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackgroundResource(R.drawable.bg_card_bordered);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        titleView.setTextSize(13);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        subtitleView.setTextSize(10);
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(titleView);
        details.addView(subtitleView);
        row.addView(details, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout side = new LinearLayout(this);
        side.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        side.setOrientation(LinearLayout.VERTICAL);
        TextView amountView = new TextView(this);
        amountView.setText(amount);
        amountView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        amountView.setTextSize(12);
        amountView.setTypeface(null, android.graphics.Typeface.BOLD);
        side.addView(amountView);
        if (statusText != null) {
            TextView status = new TextView(this);
            status.setText(statusText);
            status.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            status.setTextSize(9);
            status.setGravity(Gravity.END);
            side.addView(status);
        }
        row.addView(side);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        reportRows.addView(row, params);
    }

    private void addEmptyReportMessage() {
        TextView empty = new TextView(this);
        empty.setText("No report records match these filters.");
        empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(28), dp(16), dp(28));
        reportRows.addView(empty);
    }

    private boolean matchesSearch(Booking booking, String query) {
        return safe(booking.getId()).toLowerCase(Locale.getDefault()).contains(query)
                || safe(booking.getCustomerName()).toLowerCase(Locale.getDefault()).contains(query)
                || safe(booking.getServiceName()).toLowerCase(Locale.getDefault()).contains(query)
                || safe(booking.getStatus()).toLowerCase(Locale.getDefault()).contains(query)
                || safe(booking.getPaymentProvider()).toLowerCase(Locale.getDefault()).contains(query);
    }

    private double verifiedRevenue(Booking booking) {
        if (OrderStatus.fromString(booking.getStatus()) != OrderStatus.DELIVERED) return 0;
        if ("cash".equalsIgnoreCase(booking.getPaymentProvider())) return booking.getTotal();
        if (("flutterwave".equalsIgnoreCase(booking.getPaymentProvider())
                || "pesapal".equalsIgnoreCase(booking.getPaymentProvider()))
                && "PAID".equalsIgnoreCase(booking.getPaymentStatus())) return booking.getSubtotal();
        return 0;
    }

    private double bookingRevenue(Booking booking) {
        return reportType == 0 ? verifiedRevenue(booking) : booking.getTotal();
    }

    private void shareReport() {
        if (visibleBookings.isEmpty()) {
            Toast.makeText(this, "There are no report records to share.", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder csv;
        if (reportType >= 2) {
            boolean customers = reportType == 3;
            csv = new StringBuilder(customers
                    ? "Customer,Bookings,Items,Verified revenue (UGX)\n"
                    : "Service,Bookings,Items,Verified revenue (UGX)\n");
            Map<String, ReportSummary> summaries = new HashMap<>();
            for (Booking booking : visibleBookings) {
                String name = customers ? safe(booking.getCustomerName()) : safe(booking.getServiceName());
                if (name.isEmpty()) name = customers ? "Customer" : "Laundry service";
                String key = customers && booking.getCustomerId() != null
                        ? booking.getCustomerId() : name;
                ReportSummary summary = summaries.get(key);
                if (summary == null) {
                    summary = new ReportSummary(name);
                    summaries.put(key, summary);
                }
                summary.count++;
                summary.items += booking.getItemCount();
                summary.revenue += verifiedRevenue(booking);
            }
            for (ReportSummary summary : summaries.values()) {
                csv.append(csvValue(summary.name)).append(',')
                        .append(summary.count).append(',')
                        .append(summary.items).append(',')
                        .append(summary.revenue).append('\n');
            }
        } else {
            csv = new StringBuilder("Booking,Date,Customer,Service,Payment,Total,Status\n");
        for (Booking booking : visibleBookings) {
            csv.append(csvValue(booking.getId())).append(',')
                    .append(csvValue(DateFormat.getDateInstance(DateFormat.MEDIUM)
                            .format(booking.getCreatedAt()))).append(',')
                    .append(csvValue(booking.getCustomerName())).append(',')
                    .append(csvValue(booking.getServiceName())).append(',')
                    .append(csvValue(booking.getPaymentProvider())).append(',')
                    .append(booking.getTotal()).append(',')
                    .append(csvValue(booking.getStatus())).append('\n');
        }
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_SUBJECT, "WashLink provider report");
        share.putExtra(Intent.EXTRA_TEXT, csv.toString());
        startActivity(Intent.createChooser(share, "Share report"));
    }

    private String csvValue(String value) {
        return "\"" + safe(value).replace("\"", "\"\"") + "\"";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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

    private static class ReportSummary {
        final String name;
        int count;
        int items;
        double revenue;

        ReportSummary(String name) {
            this.name = name;
        }
    }
}
