package com.example.washlink;

import android.os.Bundle;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProviderStaffActivity extends WashLinkActivity {
    private FirebaseFirestore firestore;
    private String providerId;
    private LinearLayout rows;
    private TextView state;
    private TextView count;
    private ListenerRegistration registration;
    private ListenerRegistration approvalRegistration;
    private boolean approved;
    private List<DocumentSnapshot> allStaff = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);
        setContentView(R.layout.activity_provider_staff);
        firestore = FirebaseFirestore.getInstance();
        providerId = AuthRepository.getInstance().getCurrentUserId();
        ProviderNavBarHelper.bind(this, -1);
        rows = findViewById(R.id.provider_staff_items);
        state = findViewById(R.id.tv_provider_staff_state);
        count = findViewById(R.id.tv_provider_staff_count);
        findViewById(R.id.btn_provider_staff_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_provider_staff_add).setOnClickListener(v -> showStaffDialog(null));
        if (providerId == null) showState("Provider account is unavailable.");
        else listenForStaff();
    }

    private void listenForStaff() {
        if (registration != null) registration.remove();
        findViewById(R.id.btn_provider_staff_add).setEnabled(false);
        approvalRegistration = firestore.collection("providers").document(providerId)
                .addSnapshotListener((profile, error) -> {
                    if (error != null || profile == null || !profile.exists()) {
                        approved = false;
                        findViewById(R.id.btn_provider_staff_add).setEnabled(false);
                        showState("Could not verify provider approval.");
                        return;
                    }
                    approved = Boolean.TRUE.equals(profile.getBoolean("isApproved"));
                    findViewById(R.id.btn_provider_staff_add).setEnabled(approved);
                    if (!approved) showState("Staff roster changes are available after provider approval.");
                });
        registration = firestore.collection("providers").document(providerId)
                .collection("staff").addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        showState("Could not load staff roster: " + error.getMessage());
                        return;
                    }
                    rows.removeAllViews();
                    allStaff = snapshot == null
                            ? new ArrayList<>() : new ArrayList<>(snapshot.getDocuments());
                    if (snapshot == null || snapshot.isEmpty()) {
                        count.setText("No staff listed");
                        showState("No staff members yet. Add your first roster entry.");
                        return;
                    }
                    int active = 0;
                    for (DocumentSnapshot staff : allStaff) {
                        if (Boolean.TRUE.equals(staff.getBoolean("active"))) active++;
                    }
                    count.setText(snapshot.size() + " roster entries · " + active + " active");
                    state.setVisibility(View.GONE);
                    for (DocumentSnapshot staff : allStaff) addStaffRow(staff);
                });
    }

    private void addStaffRow(DocumentSnapshot staff) {
        View row = getLayoutInflater().inflate(R.layout.activity_provider_staff_item, rows, false);
        String name = value(staff.getString("name"), "Unnamed staff member");
        row.<TextView>findViewById(R.id.tv_provider_staff_initial)
                .setText(name.substring(0, 1).toUpperCase(java.util.Locale.getDefault()));
        row.<TextView>findViewById(R.id.tv_provider_staff_name).setText(name);
        row.<TextView>findViewById(R.id.tv_provider_staff_detail).setText(
                value(staff.getString("phone"), "Phone not provided") + " · "
                        + value(staff.getString("role"), "Staff"));
        boolean active = Boolean.TRUE.equals(staff.getBoolean("active"));
        TextView status = row.findViewById(R.id.tv_provider_staff_status);
        status.setText(active ? "Active" : "Inactive");
        status.setTextColor(getColor(active ? R.color.success_green : R.color.text_secondary));
        row.findViewById(R.id.btn_provider_staff_edit).setOnClickListener(v -> showStaffDialog(staff));
        TextView toggle = row.findViewById(R.id.btn_provider_staff_toggle);
        toggle.setText(active ? "Deactivate" : "Activate");
        toggle.setOnClickListener(v -> {
            if (!approved) {
                Toast.makeText(this, "Provider approval is required to manage staff.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            staff.getReference().update("active", !active)
                        .addOnFailureListener(error -> Toast.makeText(this,
                                "Could not update roster entry: " + error.getMessage(),
                                Toast.LENGTH_LONG).show());
        });
        row.findViewById(R.id.btn_provider_staff_delete).setOnClickListener(v -> {
            if (!approved) {
                Toast.makeText(this, "Provider approval is required to manage staff.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
                new AlertDialog.Builder(this).setTitle("Remove roster entry?")
                        .setMessage("This only removes the staff listing. It does not affect any account.")
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Remove", (dialog, which) -> staff.getReference().delete()
                                .addOnFailureListener(error -> Toast.makeText(this,
                                        "Could not remove staff entry: " + error.getMessage(),
                                        Toast.LENGTH_LONG).show()))
                        .show();
        });
        rows.addView(row);
    }

    private void showStaffDialog(DocumentSnapshot existing) {
        if (!approved) {
            Toast.makeText(this, "Provider approval is required to manage staff.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(24), 0, dp(24), 0);
        EditText name = new EditText(this);
        name.setHint("Full name");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(80)});
        name.setText(existing == null ? "" : existing.getString("name"));
        fields.addView(name);
        EditText phone = new EditText(this);
        phone.setHint("Phone number");
        phone.setSingleLine(true);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setKeyListener(DigitsKeyListener.getInstance("0123456789+() .-"));
        phone.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(30)});
        phone.setText(existing == null ? "" : existing.getString("phone"));
        fields.addView(phone);

        Spinner role = new Spinner(this);
        List<String> roles = new ArrayList<>();
        roles.add("Attendant");
        roles.add("Cashier");
        roles.add("Manager");
        roles.add("Other");
        for (DocumentSnapshot staff : allStaff) {
            String savedRole = staff.getString("role");
            if (savedRole != null && !savedRole.trim().isEmpty()
                    && !containsIgnoreCase(roles, savedRole.trim())
                    && !savedRole.equalsIgnoreCase("Add a new role...")) {
                roles.add(savedRole.trim());
            }
        }
        roles.add("Add a new role...");
        android.widget.ArrayAdapter<String> roleAdapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, roles);
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        role.setAdapter(roleAdapter);
        EditText customRole = new EditText(this);
        customRole.setHint("New role name");
        customRole.setSingleLine(true);
        customRole.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        customRole.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(40)});
        fields.addView(role);
        fields.addView(customRole);
        String oldRole = existing == null ? "" : existing.getString("role");
        if (oldRole != null && !oldRole.trim().isEmpty()) {
            int existingRolePosition = indexOfIgnoreCase(roles, oldRole.trim());
            if (existingRolePosition >= 0) {
                role.setSelection(existingRolePosition);
            } else {
                role.setSelection(roles.size() - 1);
                customRole.setText(oldRole.trim());
            }
        }
        customRole.setVisibility(role.getSelectedItemPosition() == roles.size() - 1
                ? View.VISIBLE : View.GONE);
        role.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                customRole.setVisibility(position == roles.size() - 1 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add staff roster entry" : "Edit staff roster entry")
                .setMessage("Roster only — this does not create a login account.")
                .setView(fields)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(existing == null ? "Add" : "Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String staffName = name.getText().toString().trim();
                    String staffPhone = phone.getText().toString().trim();
                    boolean addingRole = role.getSelectedItemPosition() == roles.size() - 1;
                    String staffRole = addingRole ? customRole.getText().toString().trim()
                            : String.valueOf(role.getSelectedItem()).trim();
                    String phoneDigits = staffPhone.replaceAll("\\D", "");
                    if (staffName.length() < 2 || staffName.length() > 80
                            || !staffName.matches("[\\p{L}][\\p{L}\\p{M} .'-]{1,79}")
                            || staffPhone.length() < 7 || staffPhone.length() > 30
                            || !staffPhone.matches("\\+?[0-9() .-]{7,30}")
                            || phoneDigits.length() < 7 || phoneDigits.length() > 15
                            || staffRole.length() < 2 || staffRole.length() > 40
                            || !staffRole.matches("[\\p{L}][\\p{L}\\p{M}0-9 &.'/-]{1,39}")) {
                        Toast.makeText(this, "Enter a valid name, phone number (7–15 digits), and role.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("name", staffName);
                    data.put("phone", staffPhone);
                    data.put("role", staffRole);
                    data.put("active", existing == null
                            || !Boolean.FALSE.equals(existing.getBoolean("active")));
                    if (existing == null) data.put("createdAt", FieldValue.serverTimestamp());
                    if (existing == null) {
                        firestore.collection("providers").document(providerId)
                                .collection("staff").add(data)
                                .addOnSuccessListener(reference -> dialog.dismiss())
                                .addOnFailureListener(error -> Toast.makeText(this,
                                        "Could not add staff entry: " + error.getMessage(),
                                        Toast.LENGTH_LONG).show());
                    } else {
                        existing.getReference().update(data)
                                .addOnSuccessListener(unused -> dialog.dismiss())
                                .addOnFailureListener(error -> Toast.makeText(this,
                                        "Could not save staff entry: " + error.getMessage(),
                                        Toast.LENGTH_LONG).show());
                    }
                }));
        dialog.show();
    }

    private boolean containsIgnoreCase(List<String> values, String candidate) {
        return indexOfIgnoreCase(values, candidate) >= 0;
    }

    private int indexOfIgnoreCase(List<String> values, String candidate) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equalsIgnoreCase(candidate)) return i;
        }
        return -1;
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
    private void showState(String message) {
        state.setText(message);
        state.setVisibility(View.VISIBLE);
    }
    @Override protected void onDestroy() {
        if (registration != null) registration.remove();
        if (approvalRegistration != null) approvalRegistration.remove();
        super.onDestroy();
    }
}
