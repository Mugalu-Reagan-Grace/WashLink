package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.data.BookingService;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.example.washlink.models.UserAccount;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProviderRidersActivity extends WashLinkActivity {
    private FirebaseFirestore firestore;
    private String providerId;
    private LinearLayout rows;
    private TextView state;
    private TextView riderCount;
    private EditText searchInput;
    private Spinner statusFilter;
    private Spinner sortOrder;
    private MaterialButton viewToggle;
    private ListenerRegistration bookingsRegistration;
    private com.google.firebase.firestore.ListenerRegistration ridersRegistration;
    private List<DocumentSnapshot> allRiders = new ArrayList<>();
    private List<Booking> bookings = new ArrayList<>();
    private boolean gridView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);
        setContentView(R.layout.activity_provider_riders);
        firestore = FirebaseFirestore.getInstance();
        providerId = AuthRepository.getInstance().getCurrentUserId();
        ProviderNavBarHelper.bind(this, -1);
        rows = findViewById(R.id.rider_items);
        state = findViewById(R.id.tv_rider_state);
        riderCount = findViewById(R.id.tv_rider_count);
        searchInput = findViewById(R.id.et_rider_search);
        statusFilter = findViewById(R.id.spinner_rider_filter);
        sortOrder = findViewById(R.id.spinner_rider_sort);
        viewToggle = findViewById(R.id.btn_rider_view);
        findViewById(R.id.btn_rider_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_rider).setOnClickListener(v -> showRiderDialog(null));
        findViewById(R.id.btn_export_riders).setOnClickListener(v -> shareRiders());

        setupSpinners();
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderRiders();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        viewToggle.setOnClickListener(v -> {
            gridView = !gridView;
            viewToggle.setText(gridView ? "List" : "Grid");
            renderRiders();
        });
        if (providerId != null) {
            listenForRiders();
            listenForBookings();
        } else {
            showState("Provider account is unavailable.");
        }
    }

    private void setupSpinners() {
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"All riders", "Active", "Inactive"});
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusFilter.setAdapter(filterAdapter);
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Newest", "Name A-Z"});
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortOrder.setAdapter(sortAdapter);
        android.widget.AdapterView.OnItemSelectedListener listener =
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                                                         View view, int position, long id) {
                        renderRiders();
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
                };
        statusFilter.setOnItemSelectedListener(listener);
        sortOrder.setOnItemSelectedListener(listener);
    }

    private void listenForRiders() {
        if (ridersRegistration != null) ridersRegistration.remove();
        state.setText("Loading riders...");
        state.setVisibility(View.VISIBLE);
        ridersRegistration = firestore.collection("providers").document(providerId)
                .collection("riders").addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        showState("Could not load riders: " + error.getMessage());
                        return;
                    }
                    allRiders = new ArrayList<>();
                    int activeCount = 0;
                    if (snapshot != null) {
                        allRiders.addAll(snapshot.getDocuments());
                        for (DocumentSnapshot rider : allRiders) {
                            if (Boolean.TRUE.equals(rider.getBoolean("isActive"))) activeCount++;
                        }
                    }
                    riderCount.setText(getString(R.string.provider_rider_count,
                            allRiders.size(), activeCount));
                    renderRiders();
                });
    }

    private void listenForBookings() {
        bookingsRegistration = BookingService.getInstance().addProviderBookingsListener(providerId,
                new BookingService.ProviderBookingsListener() {
                    @Override
                    public void onBookingsChanged(List<Booking> result) {
                        bookings = result;
                        renderRiders();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ProviderRidersActivity.this,
                                "Could not load rider assignments: " + message,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void renderRiders() {
        if (rows == null || allRiders == null) return;
        rows.removeAllViews();
        List<DocumentSnapshot> filtered = new ArrayList<>();
        String query = searchInput == null ? "" : searchInput.getText().toString().trim()
                .toLowerCase(Locale.getDefault());
        int filter = statusFilter == null ? 0 : statusFilter.getSelectedItemPosition();
        for (DocumentSnapshot rider : allRiders) {
            boolean active = Boolean.TRUE.equals(rider.getBoolean("isActive"));
            if (filter == 1 && !active || filter == 2 && active) continue;
            String name = rider.getString("name");
            String phone = rider.getString("phone");
            if (!query.isEmpty() && !(safe(name).toLowerCase(Locale.getDefault()).contains(query)
                    || safe(phone).toLowerCase(Locale.getDefault()).contains(query))) continue;
            filtered.add(rider);
        }
        if (sortOrder != null && sortOrder.getSelectedItemPosition() == 1) {
            Collections.sort(filtered, new Comparator<DocumentSnapshot>() {
                @Override
                public int compare(DocumentSnapshot first, DocumentSnapshot second) {
                    return safe(first.getString("name")).compareToIgnoreCase(
                            safe(second.getString("name")));
                }
            });
        } else {
            Collections.sort(filtered, new Comparator<DocumentSnapshot>() {
                @Override
                public int compare(DocumentSnapshot first, DocumentSnapshot second) {
                    return Long.compare(createdAt(second), createdAt(first));
                }
            });
        }
        if (allRiders.isEmpty()) {
            showState("No riders yet. Add a rider to assign deliveries.");
            return;
        }
        if (filtered.isEmpty()) {
            showState("No riders match your search or availability filter.");
            return;
        }
        state.setVisibility(View.GONE);
        if (gridView) {
            LinearLayout gridRow = null;
            for (int i = 0; i < filtered.size(); i++) {
                if (i % 2 == 0) {
                    gridRow = new LinearLayout(this);
                    gridRow.setOrientation(LinearLayout.HORIZONTAL);
                    rows.addView(gridRow, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                }
                addRiderRow(filtered.get(i), gridRow, true);
            }
        } else {
            for (DocumentSnapshot rider : filtered) addRiderRow(rider, rows, false);
        }
    }

    private void addRiderRow(DocumentSnapshot rider, LinearLayout parent, boolean grid) {
        String name = rider.getString("name");
        String phone = rider.getString("phone");
        boolean active = Boolean.TRUE.equals(rider.getBoolean("isActive"));
        View row = getLayoutInflater().inflate(R.layout.activity_provider_rider_item, parent, false);
        String displayName = name == null || name.trim().isEmpty() ? "Unnamed rider" : name.trim();
        TextView initial = row.findViewById(R.id.tv_rider_initial);
        TextView nameView = row.findViewById(R.id.tv_rider_name);
        TextView phoneView = row.findViewById(R.id.tv_rider_phone);
        TextView statusView = row.findViewById(R.id.tv_rider_status);
        MaterialButton toggle = row.findViewById(R.id.btn_toggle_rider);
        initial.setText(displayName.substring(0, 1).toUpperCase(Locale.getDefault()));
        nameView.setText(displayName);
        phoneView.setText(phone == null || phone.trim().isEmpty() ? "Phone not provided" : phone);
        statusView.setText(active ? "Active" : "Inactive");
        statusView.setBackgroundResource(active
                ? R.drawable.bg_accepted_pill : R.drawable.bg_closed_pill);
        statusView.setTextColor(getColor(active ? R.color.accepted_text : R.color.closed_text));
        toggle.setText(active ? "Deactivate" : "Activate");
        toggle.setTextColor(getColor(active ? R.color.reject_red : R.color.laundr_blue));
        toggle.setStrokeColorResource(active ? R.color.reject_red : R.color.laundr_blue);
        toggle.setOnClickListener(v -> rider.getReference().update("isActive", !active)
                .addOnFailureListener(error -> Toast.makeText(this,
                        "Could not update rider: " + error.getMessage(), Toast.LENGTH_LONG).show()));
        row.setOnClickListener(v -> showRiderDetails(rider));
        LinearLayout.LayoutParams params;
        if (grid) {
            int gap = dp(4);
            params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(gap, 0, gap, dp(8));
        } else {
            params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        parent.addView(row, params);
    }

    private void showRiderDialog(DocumentSnapshot rider) {
        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.VERTICAL);
        inputs.setPadding(dp(24), 0, dp(24), 0);
        EditText name = new EditText(this);
        name.setHint("Rider name");
        name.setSingleLine(true);
        if (rider != null) name.setText(rider.getString("name"));
        inputs.addView(name);
        EditText phone = new EditText(this);
        phone.setHint("Phone number");
        phone.setSingleLine(true);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        if (rider != null) phone.setText(rider.getString("phone"));
        inputs.addView(phone);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(rider == null ? "Add delivery rider" : "Edit rider")
                .setView(inputs)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(rider == null ? "Add" : "Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String riderName = name.getText().toString().trim();
                    String riderPhone = phone.getText().toString().trim();
                    if (riderName.length() < 2 || riderName.length() > 80
                            || riderPhone.length() < 7 || riderPhone.length() > 30) {
                        Toast.makeText(this, "Enter a valid name and phone number.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("name", riderName);
                    data.put("phone", riderPhone);
                    if (rider == null) {
                        data.put("isActive", true);
                        data.put("createdAt", System.currentTimeMillis());
                        firestore.collection("providers").document(providerId)
                                .collection("riders").add(data)
                                .addOnSuccessListener(reference -> dialog.dismiss())
                                .addOnFailureListener(error -> Toast.makeText(this,
                                        "Could not add rider: " + error.getMessage(),
                                        Toast.LENGTH_LONG).show());
                    } else {
                        rider.getReference().update(data)
                                .addOnSuccessListener(unused -> dialog.dismiss())
                                .addOnFailureListener(error -> Toast.makeText(this,
                                        "Could not save rider: " + error.getMessage(),
                                        Toast.LENGTH_LONG).show());
                    }
                }));
        dialog.show();
    }

    private void showRiderDetails(DocumentSnapshot rider) {
        String riderId = rider.getId();
        String riderName = safe(rider.getString("name"));
        List<Booking> assigned = new ArrayList<>();
        for (Booking booking : bookings) {
            if (riderId.equals(booking.getAssignedRiderId())
                    || (booking.getAssignedRiderId() == null
                    && riderName.equals(booking.getAssignedRiderName()))) assigned.add(booking);
        }
        int inProgress = 0;
        for (Booking booking : assigned) {
            OrderStatus status = OrderStatus.fromString(booking.getStatus());
            if (!status.isTerminal()) inProgress++;
        }
        StringBuilder message = new StringBuilder("Phone: ")
                .append(TextUtils.isEmpty(rider.getString("phone"))
                ? "Not provided" : rider.getString("phone"))
                .append("\nAvailability: ").append(Boolean.TRUE.equals(rider.getBoolean("isActive"))
                ? "Active" : "Inactive")
                .append("\n\nAssigned orders: ").append(assigned.size())
                .append("\nIn progress: ").append(inProgress);
        for (int i = 0; i < Math.min(5, assigned.size()); i++) {
            Booking booking = assigned.get(i);
            message.append("\n\n#").append(booking.getId() == null ? "" :
                            booking.getId().substring(0, Math.min(10, booking.getId().length())))
                    .append(" · ").append(TextUtils.isEmpty(booking.getCustomerName())
                            ? "Customer" : booking.getCustomerName())
                    .append("\n").append(OrderStatus.fromString(booking.getStatus()).getDisplayName());
        }
        new AlertDialog.Builder(this)
                .setTitle(TextUtils.isEmpty(riderName) ? "Rider details" : riderName)
                .setMessage(message.toString())
                .setPositiveButton("Edit rider", (dialog, which) -> showRiderDialog(rider))
                .setNegativeButton(Boolean.TRUE.equals(rider.getBoolean("isActive"))
                        ? "Deactivate" : "Activate", (dialog, which) ->
                        rider.getReference().update("isActive",
                                !Boolean.TRUE.equals(rider.getBoolean("isActive")))
                                .addOnFailureListener(error -> Toast.makeText(this,
                                        "Could not update rider: " + error.getMessage(),
                                        Toast.LENGTH_LONG).show()))
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void shareRiders() {
        StringBuilder csv = new StringBuilder("Name,Phone,Status,Assigned orders\n");
        for (DocumentSnapshot rider : filteredRiders()) {
            String name = safe(rider.getString("name"));
            String phone = safe(rider.getString("phone"));
            int assigned = 0;
            for (Booking booking : bookings) {
                if (rider.getId().equals(booking.getAssignedRiderId())
                        || (booking.getAssignedRiderId() == null
                        && name.equals(booking.getAssignedRiderName()))) assigned++;
            }
            csv.append(csvValue(name)).append(',').append(csvValue(phone)).append(',')
                    .append(Boolean.TRUE.equals(rider.getBoolean("isActive")) ? "Active" : "Inactive")
                    .append(',').append(assigned).append('\n');
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_SUBJECT, "WashLink riders");
        share.putExtra(Intent.EXTRA_TEXT, csv.toString());
        startActivity(Intent.createChooser(share, "Share riders"));
    }

    private List<DocumentSnapshot> filteredRiders() {
        List<DocumentSnapshot> filtered = new ArrayList<>();
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.getDefault());
        int filter = statusFilter.getSelectedItemPosition();
        for (DocumentSnapshot rider : allRiders) {
            boolean active = Boolean.TRUE.equals(rider.getBoolean("isActive"));
            if (filter == 1 && !active || filter == 2 && active) continue;
            if (!query.isEmpty() && !safe(rider.getString("name"))
                    .toLowerCase(Locale.getDefault()).contains(query)
                    && !safe(rider.getString("phone")).toLowerCase(Locale.getDefault()).contains(query)) continue;
            filtered.add(rider);
        }
        if (sortOrder.getSelectedItemPosition() == 1) {
            Collections.sort(filtered, (first, second) ->
                    safe(first.getString("name")).compareToIgnoreCase(safe(second.getString("name"))));
        } else {
            Collections.sort(filtered, (first, second) -> Long.compare(createdAt(second), createdAt(first)));
        }
        return filtered;
    }

    private long createdAt(DocumentSnapshot rider) {
        Object value = rider.get("createdAt");
        return value instanceof Number ? ((Number) value).longValue() : 0;
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
        rows.removeAllViews();
        state.setText(message);
        state.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (ridersRegistration != null) ridersRegistration.remove();
        if (bookingsRegistration != null) bookingsRegistration.remove();
        super.onDestroy();
    }
}
