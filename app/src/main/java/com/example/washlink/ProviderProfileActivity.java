package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ProviderProfileActivity extends AppCompatActivity {
    private String providerId;
    private String ownerName = "";
    private String businessName = "";
    private String phone = "";
    private String address = "";
    private boolean isOpen = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_provider_profile);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        providerId = AuthRepository.getInstance().getCurrentUserId();
        findViewById(R.id.tv_edit).setOnClickListener(v -> showEditDialog());
        findViewById(R.id.row_manage_services).setOnClickListener(v ->
                startActivity(new Intent(this, ManageServicesActivity.class)));
        findViewById(R.id.row_password).setOnClickListener(v ->
                AuthRepository.getInstance().sendPasswordReset(new AuthRepository.SimpleCallback() {
                    @Override public void onSuccess() {
                        Toast.makeText(ProviderProfileActivity.this,
                                "Password reset instructions sent.", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(String message) {
                        Toast.makeText(ProviderProfileActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                }));
        findViewById(R.id.row_payment_settings).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Payout settings")
                        .setMessage("Provider payouts are not connected yet. Completed order totals are shown on your dashboard, but no payout account is configured.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show());

        if (providerId != null) loadProviderProfile();

        MaterialButton logoutButton = findViewById(R.id.btn_logout);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(ProviderProfileActivity.this, ProviderLoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }

        ProviderNavBarHelper.bind(this, ProviderNavBarHelper.TAB_PROFILE);
    }

    private void loadProviderProfile() {
        FirebaseFirestore.getInstance().collection("providers").document(providerId).get()
                .addOnSuccessListener(document -> {
                    businessName = text(document.get("businessName"));
                    ownerName = text(document.get("ownerName"));
                    phone = text(document.get("phone"));
                    address = text(document.get("address"));
                    Object availability = document.get("isOpen");
                    isOpen = !(availability instanceof Boolean) || (Boolean) availability;
                    TextView nameView = findViewById(R.id.tv_business_name);
                    TextView addressView = findViewById(R.id.tv_address);
                    TextView phoneView = findViewById(R.id.tv_provider_phone);
                    TextView availabilityView = findViewById(R.id.tv_provider_availability);
                    if (nameView != null && !businessName.isEmpty()) nameView.setText(businessName);
                    if (addressView != null) addressView.setText(address.isEmpty()
                            ? "Business address not set" : address);
                    if (phoneView != null) phoneView.setText(phone.isEmpty() ? "Phone not set" : phone);
                    if (availabilityView != null) availabilityView.setText(isOpen
                            ? "Accepting new bookings" : "Not accepting bookings");
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        "Could not load business profile: " + error.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showEditDialog() {
        if (providerId == null) {
            Toast.makeText(this, "Provider account is unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(32, 0, 32, 0);
        EditText nameInput = new EditText(this);
        nameInput.setHint("Business name");
        nameInput.setSingleLine(true);
        nameInput.setText(businessName);
        fields.addView(nameInput);
        EditText ownerInput = new EditText(this);
        ownerInput.setHint("Owner name");
        ownerInput.setSingleLine(true);
        ownerInput.setText(ownerName);
        ownerInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        fields.addView(ownerInput);
        EditText phoneInput = new EditText(this);
        phoneInput.setHint("Business phone");
        phoneInput.setSingleLine(true);
        phoneInput.setText(phone);
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        fields.addView(phoneInput);
        EditText addressInput = new EditText(this);
        addressInput.setHint("Business address");
        addressInput.setText(address);
        fields.addView(addressInput);
        CheckBox availabilityInput = new CheckBox(this);
        availabilityInput.setText("Accept new bookings");
        availabilityInput.setChecked(isOpen);
        fields.addView(availabilityInput);

        new AlertDialog.Builder(this)
                .setTitle("Edit business profile")
                .setView(fields)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String newBusinessName = nameInput.getText().toString().trim();
                    String newOwnerName = ownerInput.getText().toString().trim();
                    String newPhone = phoneInput.getText().toString().trim();
                    String newAddress = addressInput.getText().toString().trim();
                    if (newBusinessName.isEmpty() || newPhone.isEmpty() || newAddress.isEmpty()) {
                        Toast.makeText(this, "Business name, phone, and address are required.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveProviderProfile(newBusinessName, newOwnerName, newPhone, newAddress,
                            availabilityInput.isChecked());
                })
                .show();
    }

    private void saveProviderProfile(String newBusinessName, String newOwnerName,
                                     String newPhone, String newAddress, boolean newIsOpen) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("businessName", newBusinessName);
        updates.put("ownerName", newOwnerName);
        updates.put("phone", newPhone);
        updates.put("address", newAddress);
        updates.put("isOpen", newIsOpen);
        FirebaseFirestore.getInstance().collection("providers").document(providerId).update(updates)
                .addOnSuccessListener(unused -> {
                    businessName = newBusinessName;
                    ownerName = newOwnerName;
                    phone = newPhone;
                    address = newAddress;
                    isOpen = newIsOpen;
                    loadProviderProfile();
                    Toast.makeText(this, "Business profile updated.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        "Could not save business profile: " + error.getMessage(), Toast.LENGTH_LONG).show());
    }

    private String text(Object value) {
        return value instanceof String ? (String) value : "";
    }
}
