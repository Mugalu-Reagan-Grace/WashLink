package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import com.example.washlink.data.AuthGuard;
import com.example.washlink.models.UserAccount;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminManagementActivity extends WashLinkActivity {
    private static final String SECTION_USERS = "users";
    private static final String SECTION_PROVIDERS = "providers";
    private static final String SECTION_BOOKINGS = "bookings";
    private static final String SECTION_OVERVIEW = "overview";
    private static final String SECTION_REPORTS = "reports";

    private LinearLayout items;
    private TextView state;
    private View reportFilters;
    private View reportStatusGroup;
    private Spinner reportPeriod;
    private Spinner reportStatus;
    private List<DocumentSnapshot> reportBookings;
    private Map<String, DocumentSnapshot> reportProviders;
    private List<DocumentSnapshot> overviewBookings;
    private int overviewUsers;
    private int overviewProviderCount;
    private int overviewPendingProviders;
    private String section = SECTION_OVERVIEW;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_management);
        AuthGuard.requireRole(this, UserAccount.ROLE_ADMIN, SignInActivity.class);

        items = findViewById(R.id.admin_items);
        state = findViewById(R.id.tv_admin_state);
        reportFilters = findViewById(R.id.admin_report_filters);
        reportStatusGroup = findViewById(R.id.admin_report_status_group);
        reportPeriod = findViewById(R.id.admin_report_period);
        reportStatus = findViewById(R.id.admin_report_status);
        findViewById(R.id.btn_admin_overview).setOnClickListener(v -> loadSection(SECTION_OVERVIEW));
        findViewById(R.id.btn_admin_users).setOnClickListener(v -> loadSection(SECTION_USERS));
        findViewById(R.id.btn_admin_providers).setOnClickListener(v -> loadSection(SECTION_PROVIDERS));
        findViewById(R.id.btn_admin_bookings).setOnClickListener(v -> loadSection(SECTION_BOOKINGS));
        findViewById(R.id.btn_admin_reports).setOnClickListener(v -> loadSection(SECTION_REPORTS));
        findViewById(R.id.btn_admin_refresh).setOnClickListener(v -> loadSection(section));
        setupReportFilters();
        findViewById(R.id.btn_admin_logout).setOnClickListener(v -> {
            com.example.washlink.ui.ThemeManager.resetSignedOut(this);
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, SignInActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        loadSection(section);
    }

    private void loadSection(String requestedSection) {
        section = requestedSection;
        items.removeAllViews();
        boolean showingReport = SECTION_REPORTS.equals(section);
        boolean showingOverview = SECTION_OVERVIEW.equals(section);
        reportFilters.setVisibility(showingReport || showingOverview ? View.VISIBLE : View.GONE);
        reportStatusGroup.setVisibility(showingReport ? View.VISIBLE : View.GONE);
        showState("Loading " + section + "...");
        if (SECTION_REPORTS.equals(section)) {
            loadAdminReport();
            return;
        }
        Query query = FirebaseFirestore.getInstance().collection(section);
        if (SECTION_OVERVIEW.equals(section)) {
            loadOverview();
            return;
        }
        if (SECTION_BOOKINGS.equals(section)) {
            query = query.orderBy("createdAt", Query.Direction.DESCENDING).limit(100);
        }

        query.get(SECTION_PROVIDERS.equals(section) ? Source.SERVER : Source.DEFAULT)
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        showState("No " + section + " found.");
                        return;
                    }
                    if (SECTION_BOOKINGS.equals(section)) addBookingSummary(snapshot.getDocuments());
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (SECTION_USERS.equals(section)) addUserRow(document);
                        else if (SECTION_PROVIDERS.equals(section)) addProviderRow(document);
                        else addBookingRow(document);
                    }
                    state.setVisibility(View.GONE);
                })
                .addOnFailureListener(error -> {
                    String details = error.getLocalizedMessage();
                    showState("Could not load " + section
                            + (details == null || details.trim().isEmpty() ? "" : ": " + details)
                            + ". Tap to retry.");
                    state.setClickable(true);
                    state.setOnClickListener(v -> loadSection(section));
                });
    }

    private void setupReportFilters() {
        String[] periods = {"Last 7 days", "Last 30 days", "Last 90 days", "Loaded history (up to 1,000)"};
        String[] statuses = {"All statuses", "Booked", "Accepted", "Picked Up", "Washing",
                "Drying", "Ready", "Out for Delivery", "Delivered", "Rejected", "Cancelled"};
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, periods);
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reportPeriod.setAdapter(periodAdapter);
        reportPeriod.setSelection(1);

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, statuses);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reportStatus.setAdapter(statusAdapter);

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (SECTION_REPORTS.equals(section) && reportBookings != null && reportProviders != null) {
                    renderAdminReport();
                } else if (SECTION_OVERVIEW.equals(section) && overviewBookings != null) {
                    renderAdminOverview();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        reportPeriod.setOnItemSelectedListener(filterListener);
        reportStatus.setOnItemSelectedListener(filterListener);
    }

    private void loadAdminReport() {
        reportBookings = null;
        reportProviders = null;
        showState("Loading platform report...");
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection(SECTION_BOOKINGS)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(1000).get()
                .addOnSuccessListener(bookings -> firestore.collection(SECTION_PROVIDERS)
                        .limit(500).get(Source.SERVER)
                        .addOnSuccessListener(providers -> {
                            if (!SECTION_REPORTS.equals(section)) return;
                            reportBookings = bookings.getDocuments();
                            reportProviders = new HashMap<>();
                            for (DocumentSnapshot provider : providers.getDocuments()) {
                                reportProviders.put(provider.getId(), provider);
                            }
                            renderAdminReport();
                        })
                        .addOnFailureListener(error -> showReportLoadError(error)))
                .addOnFailureListener(error -> showReportLoadError(error));
    }

    private void showReportLoadError(Exception error) {
        reportBookings = null;
        reportProviders = null;
        showState("Could not load platform report. Tap to retry.");
        state.setClickable(true);
        state.setOnClickListener(v -> loadAdminReport());
        android.util.Log.e("AdminManagement", "Could not load platform report", error);
    }

    private void renderAdminReport() {
        if (reportBookings == null || reportProviders == null) return;
        items.removeAllViews();
        long now = System.currentTimeMillis();
        int rangeDays = reportPeriod.getSelectedItemPosition() == 0 ? 7
                : reportPeriod.getSelectedItemPosition() == 1 ? 30
                : reportPeriod.getSelectedItemPosition() == 2 ? 90 : 0;
        long rangeStart = rangeDays == 0 ? 0 : now - rangeDays * 24L * 60L * 60L * 1000L;
        String selectedStatus = statusFromPosition(reportStatus.getSelectedItemPosition());
        List<DocumentSnapshot> matchingBookings = new ArrayList<>();
        for (DocumentSnapshot booking : reportBookings) {
            long createdAt = timestampMillis(booking.get("createdAt"));
            String status = text(booking.get("status"), "UNKNOWN").toUpperCase();
            if (createdAt <= now && createdAt >= rangeStart
                    && ("ALL".equals(selectedStatus) || selectedStatus.equals(status))) {
                matchingBookings.add(booking);
            }
        }

        int completed = 0;
        double verifiedCash = 0;
        double verifiedOnline = 0;
        int pending = 0;
        int inProgress = 0;
        int closed = 0;
        Map<String, ProviderReportRow> providerRows = new HashMap<>();
        for (DocumentSnapshot booking : matchingBookings) {
            String status = text(booking.get("status"), "UNKNOWN").toUpperCase();
            boolean delivered = "DELIVERED".equals(status);
            if (delivered) completed++;
            if ("BOOKED".equals(status)) pending++;
            else if (isInProgress(status)) inProgress++;
            else if ("CANCELLED".equals(status) || "REJECTED".equals(status)) closed++;

            double verifiedRevenue = verifiedRevenue(booking);
            if (delivered && "cash".equalsIgnoreCase(text(booking.get("paymentProvider"), ""))) {
                verifiedCash += verifiedRevenue;
            } else if (delivered && verifiedRevenue > 0) {
                verifiedOnline += verifiedRevenue;
            }

            String providerId = text(booking.get("providerId"), "unknown");
            ProviderReportRow row = providerRows.get(providerId);
            if (row == null) {
                DocumentSnapshot provider = reportProviders.get(providerId);
                String businessName = provider == null ? text(booking.get("providerName"), "Unknown provider")
                        : text(provider.get("businessName"), text(booking.get("providerName"), "Unknown provider"));
                row = new ProviderReportRow(providerId, businessName);
                providerRows.put(providerId, row);
            }
            row.bookings++;
            if (delivered) row.completed++;
            if ("BOOKED".equals(status)) row.pending++;
            row.revenue += verifiedRevenue;
        }

        TextView context = reportText("Showing " + matchingBookings.size()
                + " bookings from the latest 1,000 loaded records.");
        context.setTextColor(getColor(R.color.text_secondary));
        items.addView(context);
        addReportCard("Platform summary",
                "Verified revenue: " + money(verifiedCash + verifiedOnline)
                        + "\nBookings: " + matchingBookings.size()
                        + "\nCompleted: " + completed
                        + "\nAverage order: " + money(matchingBookings.isEmpty() ? 0
                        : averageOrderTotal(matchingBookings)));
        addReportCard("Verified payment collections",
                "Cash collected: " + money(verifiedCash)
                        + "\nVerified online: " + money(verifiedOnline)
                        + "\nTotal verified revenue: " + money(verifiedCash + verifiedOnline)
                        + "\nOnline collections use provider subtotal; service fees are excluded.");
        addReportCard("Booking outcomes",
                "Waiting for provider: " + pending
                        + "\nIn progress: " + inProgress
                        + "\nDelivered: " + completed
                        + "\nCancelled / rejected: " + closed);

        List<ProviderReportRow> sortedProviders = new ArrayList<>(providerRows.values());
        Collections.sort(sortedProviders, (first, second) -> {
            int revenueOrder = Double.compare(second.revenue, first.revenue);
            return revenueOrder != 0 ? revenueOrder : Integer.compare(second.bookings, first.bookings);
        });
        addReportHeader("Provider performance");
        if (sortedProviders.isEmpty()) {
            addReportCard("No provider bookings", "No provider bookings match this report.");
        } else {
            int limit = Math.min(sortedProviders.size(), 20);
            for (int i = 0; i < limit; i++) {
                ProviderReportRow row = sortedProviders.get(i);
                addReportCard(row.name, row.bookings + " bookings · " + row.completed
                        + " completed · " + row.pending + " waiting response\nVerified revenue: "
                        + money(row.revenue));
            }
        }

        addReportHeader("Booking details");
        if (matchingBookings.isEmpty()) {
            addReportCard("No matching bookings", "Try another date range or status.");
        } else {
            int limit = Math.min(matchingBookings.size(), 50);
            for (int i = 0; i < limit; i++) addReportBooking(matchingBookings.get(i));
            if (matchingBookings.size() > limit) {
                addReportCard("Showing 50 of " + matchingBookings.size(),
                        "The report summary includes all matching bookings.");
            }
        }
        state.setVisibility(View.GONE);
    }

    private String statusFromPosition(int position) {
        if (position <= 0) return "ALL";
        return new String[]{"BOOKED", "ACCEPTED", "PICKED_UP", "WASHING", "DRYING",
                "READY", "OUT_FOR_DELIVERY", "DELIVERED", "REJECTED", "CANCELLED"}[position - 1];
    }

    private long timestampMillis(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        if (value instanceof Date) return ((Date) value).getTime();
        if (value instanceof Number) return ((Number) value).longValue();
        return 0;
    }

    private boolean isInProgress(String status) {
        return "ACCEPTED".equals(status) || "PICKED_UP".equals(status) || "WASHING".equals(status)
                || "DRYING".equals(status) || "READY".equals(status)
                || "OUT_FOR_DELIVERY".equals(status);
    }

    private double verifiedRevenue(DocumentSnapshot booking) {
        if (!"DELIVERED".equalsIgnoreCase(text(booking.get("status"), ""))) return 0;
        String provider = text(booking.get("paymentProvider"), "");
        String payment = text(booking.get("paymentStatus"), "");
        Object amount = "cash".equalsIgnoreCase(provider) ? booking.get("total")
                : ("flutterwave".equalsIgnoreCase(provider)
                || "pesapal".equalsIgnoreCase(provider)) && "PAID".equalsIgnoreCase(payment)
                ? booking.get("subtotal") : null;
        return amount instanceof Number ? ((Number) amount).doubleValue() : 0;
    }

    private double averageOrderTotal(List<DocumentSnapshot> bookings) {
        double total = 0;
        for (DocumentSnapshot booking : bookings) {
            Object amount = booking.get("total");
            if (amount instanceof Number) total += ((Number) amount).doubleValue();
        }
        return total / bookings.size();
    }

    private String money(double amount) {
        return com.example.washlink.data.BookingPricing.format((int) Math.round(amount));
    }

    private TextView reportText(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(getColor(R.color.text_primary));
        text.setTextSize(15);
        text.setLineSpacing(4, 1.0f);
        return text;
    }

    private void addReportHeader(String title) {
        TextView header = reportText(title);
        header.setTextSize(18);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(4, 16, 4, 8);
        items.addView(header);
    }

    private void addReportCard(String title, String details) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 14, 16, 14);
        card.setBackgroundResource(R.drawable.bg_card_bordered);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 10;
        card.setLayoutParams(params);
        TextView titleView = reportText(title);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView detailView = reportText(details);
        detailView.setTextColor(getColor(R.color.text_secondary));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = 6;
        card.addView(titleView);
        card.addView(detailView, detailParams);
        items.addView(card);
    }

    private void addReportBooking(DocumentSnapshot booking) {
        String status = text(booking.get("status"), "UNKNOWN").replace('_', ' ');
        String paymentStatus = text(booking.get("paymentStatus"), "PENDING");
        addReportCard("Order #" + booking.getId(),
                text(booking.get("customerName"), "Unknown customer")
                        + " · " + text(booking.get("providerName"), "Unknown provider")
                        + "\n" + status + " · " + paymentStatus
                        + " · " + money(booking.get("total") instanceof Number
                        ? ((Number) booking.get("total")).doubleValue() : 0)
                        + "\nVerified revenue: " + money(verifiedRevenue(booking)));
    }

    private static final class ProviderReportRow {
        final String id;
        final String name;
        int bookings;
        int completed;
        int pending;
        double revenue;

        ProviderReportRow(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private void loadOverview() {
        overviewBookings = null;
        showState("Loading overview...");
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection(SECTION_USERS).limit(500).get()
                .addOnSuccessListener(users -> firestore.collection(SECTION_PROVIDERS)
                        .limit(500).get(Source.SERVER)
                        .addOnSuccessListener(providers -> firestore.collection(SECTION_BOOKINGS)
                                .orderBy("createdAt", Query.Direction.DESCENDING).limit(1000).get()
                                                .addOnSuccessListener(bookings -> {
                                                    if (!SECTION_OVERVIEW.equals(section)) return;
                                                    overviewUsers = users.size();
                                                    overviewProviderCount = providers.size();
                                                    overviewPendingProviders = 0;
                                                    for (DocumentSnapshot provider : providers.getDocuments()) {
                                                        if (!Boolean.TRUE.equals(provider.get("isApproved"))) overviewPendingProviders++;
                                                    }
                                                    overviewBookings = bookings.getDocuments();
                                                    renderAdminOverview();
                                })
                                .addOnFailureListener(this::showLoadError))
                        .addOnFailureListener(this::showLoadError))
                .addOnFailureListener(this::showLoadError);
    }

    private void renderAdminOverview() {
        if (overviewBookings == null) return;
        items.removeAllViews();
        int rangePosition = reportPeriod.getSelectedItemPosition();
        int rangeDays = rangePosition == 0 ? 7 : rangePosition == 1 ? 30
                : rangePosition == 2 ? 90 : 0;
        long now = System.currentTimeMillis();
        long rangeStart = rangeDays == 0 ? 0 : now - rangeDays * 24L * 60L * 60L * 1000L;
        List<DocumentSnapshot> periodBookings = new ArrayList<>();
        for (DocumentSnapshot booking : overviewBookings) {
            long createdAt = timestampMillis(booking.get("createdAt"));
            if (createdAt <= now && createdAt >= rangeStart) periodBookings.add(booking);
        }

        int open = 0;
        int completed = 0;
        double collected = 0;
        Map<String, Integer> statusCounts = new HashMap<>();
        Map<String, Integer> customerCounts = new HashMap<>();
        Map<String, Integer> providerCounts = new HashMap<>();
        Map<String, String> customerNames = new HashMap<>();
        Map<String, String> providerNames = new HashMap<>();
        for (DocumentSnapshot booking : periodBookings) {
            String status = text(booking.get("status"), "UNKNOWN").toUpperCase();
            statusCounts.put(status, statusCounts.containsKey(status) ? statusCounts.get(status) + 1 : 1);
            if (!"DELIVERED".equals(status) && !"CANCELLED".equals(status)
                    && !"REJECTED".equals(status)) open++;
            if ("DELIVERED".equals(status)) completed++;
            collected += verifiedRevenue(booking);
            addPersonCount(customerCounts, customerNames, booking, "customerId", "customerName");
            addPersonCount(providerCounts, providerNames, booking, "providerId", "providerName");
        }

        addOverviewMetric("Collected revenue",
                money(collected) + " · Verified revenue");
        addOverviewMetric("Total orders", periodBookings.size()
                + " bookings in the selected period");
        addOverviewMetric("In progress", open + " open orders");
        addOverviewMetric("Completed orders", completed + " delivered · "
                + customerCounts.size() + " customers · " + providerCounts.size() + " providers");
        addOverviewMetric("Marketplace accounts", overviewUsers + " users · " + overviewProviderCount
                + " providers · " + overviewPendingProviders + " awaiting approval");

        addReportHeader("Order status");
        if (statusCounts.isEmpty()) {
            addReportCard("No orders in this period", "Choose another reporting period or refresh.");
        } else {
            List<Map.Entry<String, Integer>> statuses = new ArrayList<>(statusCounts.entrySet());
            Collections.sort(statuses, (first, second) -> Integer.compare(second.getValue(), first.getValue()));
            for (int i = 0; i < Math.min(statuses.size(), 5); i++) {
                Map.Entry<String, Integer> entry = statuses.get(i);
                addReportCard(entry.getKey().replace('_', ' '), entry.getValue() + " orders");
            }
        }

        addReportHeader("Top customers");
        addTopPeople(customerCounts, customerNames, "No customers have booked in this period.");
        addReportHeader("Top providers");
        addTopPeople(providerCounts, providerNames, "No providers have received bookings in this period.");
        addReportHeader("Recent orders");
        if (overviewBookings.isEmpty()) {
            addReportCard("No bookings yet", "New customer orders will appear here.");
        } else {
            for (int i = 0; i < Math.min(overviewBookings.size(), 8); i++) {
                addReportBooking(overviewBookings.get(i));
            }
        }
        state.setVisibility(View.GONE);
    }

    private void addOverviewMetric(String title, String details) {
        addReportCard(title, details);
    }

    private void addPersonCount(Map<String, Integer> counts, Map<String, String> names,
                                DocumentSnapshot booking, String idField, String nameField) {
        String id = text(booking.get(idField), "");
        if (id.isEmpty()) return;
        counts.put(id, counts.containsKey(id) ? counts.get(id) + 1 : 1);
        if (!names.containsKey(id)) names.put(id, text(booking.get(nameField), "Unknown"));
    }

    private void addTopPeople(Map<String, Integer> counts, Map<String, String> names, String emptyText) {
        if (counts.isEmpty()) {
            addReportCard("No activity", emptyText);
            return;
        }
        List<String> ids = new ArrayList<>(counts.keySet());
        Collections.sort(ids, (first, second) -> Integer.compare(counts.get(second), counts.get(first)));
        for (int i = 0; i < Math.min(ids.size(), 5); i++) {
            String id = ids.get(i);
            addReportCard(names.get(id), counts.get(id) + " bookings");
        }
    }

    private void addBookingSummary(java.util.List<DocumentSnapshot> documents) {
        int open = 0;
        int delivered = 0;
        double collectedRevenue = 0;
        for (DocumentSnapshot document : documents) {
            String status = text(document.get("status"), "");
            if (!"DELIVERED".equals(status) && !"CANCELLED".equals(status)
                    && !"REJECTED".equals(status)) open++;
            if ("DELIVERED".equals(status)) {
                delivered++;
                String provider = text(document.get("paymentProvider"), "");
                String payment = text(document.get("paymentStatus"), "");
                Object amount = "cash".equalsIgnoreCase(provider) ? document.get("total")
                        : ("flutterwave".equalsIgnoreCase(provider)
                        || "pesapal".equalsIgnoreCase(provider)) && "PAID".equalsIgnoreCase(payment)
                        ? document.get("subtotal") : null;
                if (amount instanceof Number) collectedRevenue += ((Number) amount).doubleValue();
            }
        }
        TextView summary = new TextView(this);
        summary.setText("Latest 100 bookings · " + documents.size() + " loaded\n"
                + open + " in progress · " + delivered + " delivered\nCollected revenue: "
                + com.example.washlink.data.BookingPricing.format((int) collectedRevenue));
        summary.setTextColor(getColor(R.color.text_primary));
        summary.setTextSize(15);
        summary.setPadding(16, 12, 16, 16);
        items.addView(summary);
    }

    private void addBookingRow(DocumentSnapshot document) {
        LinearLayout row = createRow();
        String status = text(document.get("status"), "Unknown");
        String payment = text(document.get("paymentStatus"), "Unpaid");
        TextView details = new TextView(this);
        details.setText("Order #" + document.getId()
                + "\nCustomer: " + text(document.get("customerName"), "Unknown")
                + " · Provider: " + text(document.get("providerName"), "Unknown")
                + "\n" + status + " · " + payment
                + " · " + com.example.washlink.data.BookingPricing.format(
                        document.get("total") instanceof Number
                                ? ((Number) document.get("total")).intValue() : 0));
        details.setTextColor(getColor(R.color.text_primary));
        row.addView(details);
        items.addView(row);
    }

    private void addUserRow(DocumentSnapshot document) {
        String uid = document.getId();
        String role = text(document.get("role"), "Unknown role");
        boolean suspended = Boolean.TRUE.equals(document.get("isSuspended"));
        LinearLayout row = createRow();
        TextView description = new TextView(this);
        description.setText(text(document.get("name"), "Unnamed account")
                + "\n" + text(document.get("email"), "No email")
                + "\n" + role + (suspended ? " · Suspended" : " · Active"));
        row.addView(description, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (!UserAccount.ROLE_ADMIN.equals(role) && !uid.equals(
                FirebaseAuth.getInstance().getCurrentUser() == null
                        ? null : FirebaseAuth.getInstance().getCurrentUser().getUid())) {
            MaterialButton action = new MaterialButton(this);
            action.setText(suspended ? "Restore" : "Suspend");
            action.setOnClickListener(v -> updateUserSuspension(uid, !suspended));
            row.addView(action);
        }
        items.addView(row);
    }

    private void addProviderRow(DocumentSnapshot document) {
        String uid = document.getId();
        boolean approved = Boolean.TRUE.equals(document.get("isApproved"));
        LinearLayout row = createRow();
        TextView description = new TextView(this);
        description.setText(text(document.get("businessName"), "Unnamed provider")
                + "\n" + text(document.get("address"), "No business address")
                + "\n" + (approved ? "Approved" : "Pending approval"));
        row.addView(description, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton action = new MaterialButton(this);
        action.setText(approved ? "Unapprove" : "Approve");
        action.setOnClickListener(v -> updateProviderApproval(uid, !approved));
        row.addView(action);
        items.addView(row);
    }

    private void updateUserSuspension(String uid, boolean suspended) {
        setActionsEnabled(false);
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("isSuspended", suspended)
                .addOnSuccessListener(unused -> loadSection(section))
                .addOnFailureListener(error -> showUpdateError(error));
    }

    private void updateProviderApproval(String uid, boolean approved) {
        setActionsEnabled(false);
        FirebaseFirestore.getInstance().collection("providers").document(uid)
                .update("isApproved", approved)
                .addOnSuccessListener(unused -> loadSection(section))
                .addOnFailureListener(error -> showUpdateError(error));
    }

    private LinearLayout createRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(12, 8, 4, 8);
        row.setBackgroundResource(R.drawable.bg_card_bordered);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 12;
        row.setLayoutParams(params);
        return row;
    }

    private void setActionsEnabled(boolean enabled) {
        for (int i = 0; i < items.getChildCount(); i++) {
            View child = items.getChildAt(i);
            for (int j = 0; j < ((LinearLayout) child).getChildCount(); j++) {
                ((LinearLayout) child).getChildAt(j).setEnabled(enabled);
            }
        }
    }

    private void showUpdateError(Exception error) {
        Toast.makeText(this, "Could not save account status: " + error.getMessage(),
                Toast.LENGTH_LONG).show();
        loadSection(section);
    }

    private void showState(String message) {
        state.setText(message);
        state.setVisibility(View.VISIBLE);
        state.setClickable(false);
        state.setOnClickListener(null);
    }

    private void showLoadError(Exception error) {
        showState("Could not load overview. Tap to retry.");
        state.setClickable(true);
        state.setOnClickListener(v -> loadSection(SECTION_OVERVIEW));
    }

    private String text(Object value, String fallback) {
        return value instanceof String && !((String) value).trim().isEmpty()
                ? (String) value : fallback;
    }
}
