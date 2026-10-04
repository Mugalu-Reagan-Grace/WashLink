package com.example.washlink;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class ProviderRidersActivity extends AppCompatActivity {
    private FirebaseFirestore firestore;
    private String providerId;
    private LinearLayout rows;
    private TextView state;
    private TextView riderCount;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);
        setContentView(R.layout.activity_provider_riders);
        firestore = FirebaseFirestore.getInstance();
        providerId = AuthRepository.getInstance().getCurrentUserId();
        rows = findViewById(R.id.rider_items);
        state = findViewById(R.id.tv_rider_state);
        riderCount = findViewById(R.id.tv_rider_count);
        findViewById(R.id.btn_rider_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_rider).setOnClickListener(v -> showAddRiderDialog());
        if (providerId != null) listenForRiders();
        else showState("Provider account is unavailable.");
    }

    private void listenForRiders() {
        if (registration != null) registration.remove();
        registration = firestore.collection("providers").document(providerId)
                .collection("riders").addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        showState("Could not load riders: " + error.getMessage());
                        return;
                    }
                    rows.removeAllViews();
                    int total = snapshot == null ? 0 : snapshot.size();
                    int activeCount = 0;
                    if (snapshot != null) {
                        for (DocumentSnapshot rider : snapshot.getDocuments()) {
                            if (Boolean.TRUE.equals(rider.getBoolean("isActive"))) activeCount++;
                        }
                    }
                    riderCount.setText(getString(R.string.provider_rider_count,
                            total, activeCount));
                    if (snapshot == null || snapshot.isEmpty()) {
                        showState("No riders yet. Add a rider to assign deliveries.");
                        return;
                    }
                    state.setVisibility(View.GONE);
                    for (DocumentSnapshot rider : snapshot.getDocuments()) addRiderRow(rider);
                });
    }

    private void addRiderRow(DocumentSnapshot rider) {
        String name = rider.getString("name");
        String phone = rider.getString("phone");
        boolean active = Boolean.TRUE.equals(rider.getBoolean("isActive"));
        View row = getLayoutInflater().inflate(R.layout.item_provider_rider, rows, false);
        String displayName = name == null || name.trim().isEmpty() ? "Unnamed rider" : name.trim();
        TextView initial = row.findViewById(R.id.tv_rider_initial);
        TextView nameView = row.findViewById(R.id.tv_rider_name);
        TextView phoneView = row.findViewById(R.id.tv_rider_phone);
        TextView statusView = row.findViewById(R.id.tv_rider_status);
        MaterialButton toggle = row.findViewById(R.id.btn_toggle_rider);
        initial.setText(displayName.substring(0, 1).toUpperCase(java.util.Locale.getDefault()));
        nameView.setText(displayName);
        phoneView.setText(phone == null || phone.trim().isEmpty() ? "Phone not provided" : phone);
        statusView.setText(active ? "Active" : "Inactive");
        statusView.setBackgroundResource(active
                ? R.drawable.bg_accepted_pill : R.drawable.bg_closed_pill);
        statusView.setTextColor(getColor(active ? R.color.accepted_text : R.color.closed_text));
        toggle.setText(active ? "Deactivate rider" : "Activate rider");
        toggle.setTextColor(getColor(active ? R.color.reject_red : R.color.laundr_blue));
        toggle.setStrokeColorResource(active ? R.color.reject_red : R.color.laundr_blue);
        toggle.setOnClickListener(v -> rider.getReference().update("isActive", !active)
                .addOnFailureListener(error -> Toast.makeText(this,
                        "Could not update rider: " + error.getMessage(), Toast.LENGTH_LONG).show()));
        rows.addView(row);
    }

    private void showAddRiderDialog() {
        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.VERTICAL);
        inputs.setPadding(32, 0, 32, 0);
        EditText name = new EditText(this);
        name.setHint("Rider name");
        name.setSingleLine(true);
        inputs.addView(name);
        EditText phone = new EditText(this);
        phone.setHint("Phone number");
        phone.setSingleLine(true);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        inputs.addView(phone);
        new AlertDialog.Builder(this).setTitle("Add delivery rider")
                .setView(inputs)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Add", (dialog, which) -> {
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
                    data.put("isActive", true);
                    data.put("createdAt", System.currentTimeMillis());
                    firestore.collection("providers").document(providerId)
                            .collection("riders").add(data)
                            .addOnFailureListener(error -> Toast.makeText(this,
                                    "Could not add rider: " + error.getMessage(),
                                    Toast.LENGTH_LONG).show());
                }).show();
    }

    private void showState(String message) {
        state.setText(message);
        state.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }
}
