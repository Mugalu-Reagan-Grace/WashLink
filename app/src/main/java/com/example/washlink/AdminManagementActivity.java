package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.models.UserAccount;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class AdminManagementActivity extends AppCompatActivity {
    private static final String SECTION_USERS = "users";
    private static final String SECTION_PROVIDERS = "providers";
    private static final String SECTION_BOOKINGS = "bookings";

    private LinearLayout items;
    private TextView state;
    private String section = SECTION_USERS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_management);
        AuthGuard.requireRole(this, UserAccount.ROLE_ADMIN, SignInActivity.class);

        items = findViewById(R.id.admin_items);
        state = findViewById(R.id.tv_admin_state);
        findViewById(R.id.btn_admin_users).setOnClickListener(v -> loadSection(SECTION_USERS));
        findViewById(R.id.btn_admin_providers).setOnClickListener(v -> loadSection(SECTION_PROVIDERS));
        findViewById(R.id.btn_admin_bookings).setOnClickListener(v -> loadSection(SECTION_BOOKINGS));
        findViewById(R.id.btn_admin_refresh).setOnClickListener(v -> loadSection(section));
        findViewById(R.id.btn_admin_logout).setOnClickListener(v -> {
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
        showState("Loading " + section + "...");
        Query query = FirebaseFirestore.getInstance().collection(section);
        if (SECTION_BOOKINGS.equals(section)) {
            query = query.orderBy("createdAt", Query.Direction.DESCENDING).limit(100);
        }
        query.get()
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
                    showState("Could not load " + section + ". Tap to retry.");
                    state.setClickable(true);
                    state.setOnClickListener(v -> loadSection(section));
                });
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
                        : "flutterwave".equalsIgnoreCase(provider) && "PAID".equalsIgnoreCase(payment)
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
        boolean approved = !Boolean.FALSE.equals(document.get("isApproved"));
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

    private String text(Object value, String fallback) {
        return value instanceof String && !((String) value).trim().isEmpty()
                ? (String) value : fallback;
    }
}
