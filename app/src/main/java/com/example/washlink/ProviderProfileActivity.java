package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProviderProfileActivity extends AppCompatActivity {
    private String providerId;
    private String ownerName = "";
    private String businessName = "";
    private String phone = "";
    private String address = "";
    private String description = "";
    private String operatingHours = "";
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
        findViewById(R.id.row_manage_riders).setOnClickListener(v ->
                startActivity(new Intent(this, ProviderRidersActivity.class)));
        findViewById(R.id.row_provider_reports).setOnClickListener(v ->
                startActivity(new Intent(this, ProviderReportsActivity.class)));
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
        findViewById(R.id.row_payment_settings).setOnClickListener(v -> loadPayoutSettings());
        if (getIntent().getBooleanExtra("open_payout_settings", false)) {
            findViewById(R.id.row_payment_settings).post(this::loadPayoutSettings);
        }

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
                    description = text(document.get("description"));
                    operatingHours = text(document.get("operatingHours"));
                    Object availability = document.get("isOpen");
                    isOpen = !(availability instanceof Boolean) || (Boolean) availability;
                    boolean approved = !Boolean.FALSE.equals(document.get("isApproved"));
                    TextView nameView = findViewById(R.id.tv_business_name);
                    TextView addressView = findViewById(R.id.tv_address);
                    TextView phoneView = findViewById(R.id.tv_provider_phone);
                    TextView availabilityView = findViewById(R.id.tv_provider_availability);
                    TextView ratingView = findViewById(R.id.tv_profile_rating_value);
                    TextView reviewCountView = findViewById(R.id.tv_profile_review_count);
                    TextView descriptionView = findViewById(R.id.tv_description_value);
                    TextView hoursView = findViewById(R.id.tv_hours_value);
                    TextView completionView = findViewById(R.id.tv_profile_completion);
                    if (nameView != null && !businessName.isEmpty()) nameView.setText(businessName);
                    if (addressView != null) addressView.setText(address.isEmpty()
                            ? "Business address not set" : address);
                    if (phoneView != null) phoneView.setText(phone.isEmpty() ? "Phone not set" : phone);
                    if (descriptionView != null) descriptionView.setText(description.isEmpty()
                            ? "Add a business description to help customers understand your services."
                            : description);
                    if (hoursView != null) hoursView.setText(operatingHours.isEmpty()
                            ? "Operating hours have not been configured." : operatingHours);
                    if (completionView != null) {
                        Object services = document.get("services");
                        int availableServices = 0;
                        if (services instanceof List<?>) {
                            for (Object service : (List<?>) services) {
                                if (service instanceof java.util.Map<?, ?>
                                        && !Boolean.FALSE.equals(
                                        ((java.util.Map<?, ?>) service).get("isAvailable"))) {
                                    availableServices++;
                                }
                            }
                        }
                        boolean hasServices = availableServices > 0;
                        int completed = 0;
                        if (!businessName.isEmpty()) completed++;
                        if (!address.isEmpty()) completed++;
                        if (!phone.isEmpty()) completed++;
                        if (!description.isEmpty()) completed++;
                        if (!operatingHours.isEmpty()) completed++;
                        if (hasServices) completed++;
                        completionView.setText(getString(R.string.provider_profile_completion,
                                completed, 6));
                        completionView.setText(completionView.getText() + " · "
                                + availableServices + " services available");
                    }
                    Object rating = document.get("rating");
                    Object reviewCount = document.get("reviewCount");
                    if (ratingView != null && rating instanceof Number
                            && reviewCount instanceof Number
                            && ((Number) reviewCount).intValue() > 0) {
                        ratingView.setText(String.format(java.util.Locale.getDefault(), "%.1f",
                                ((Number) rating).doubleValue()));
                        if (reviewCountView != null) {
                            int count = ((Number) reviewCount).intValue();
                            reviewCountView.setText(count == 1
                                    ? getString(R.string.provider_single_review)
                                    : getString(R.string.provider_review_count, count));
                        }
                    } else if (reviewCountView != null) {
                        reviewCountView.setText(R.string.provider_no_reviews);
                    }
                    if (availabilityView != null) {
                        availabilityView.setText(!approved ? "Pending administrator approval"
                                : isOpen ? "Accepting new bookings" : "Not accepting bookings");
                        availabilityView.setTextColor(getColor(!approved
                                ? R.color.pending_text
                                : isOpen ? R.color.success_green : R.color.text_secondary));
                    }
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
        EditText descriptionInput = new EditText(this);
        descriptionInput.setHint("Business description");
        descriptionInput.setText(description);
        descriptionInput.setMinLines(2);
        descriptionInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        fields.addView(descriptionInput);
        EditText hoursInput = new EditText(this);
        hoursInput.setHint("Operating hours (e.g. Mon-Sat, 8am-6pm)");
        hoursInput.setText(operatingHours);
        hoursInput.setSingleLine(true);
        fields.addView(hoursInput);
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
                    String newDescription = descriptionInput.getText().toString().trim();
                    String newOperatingHours = hoursInput.getText().toString().trim();
                    if (newBusinessName.isEmpty() || newPhone.isEmpty() || newAddress.isEmpty()) {
                        Toast.makeText(this, "Business name, phone, and address are required.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveProviderProfile(newBusinessName, newOwnerName, newPhone, newAddress,
                            newDescription, newOperatingHours, availabilityInput.isChecked());
                })
                .show();
    }

    private void saveProviderProfile(String newBusinessName, String newOwnerName,
                                     String newPhone, String newAddress, String newDescription,
                                     String newOperatingHours, boolean newIsOpen) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("businessName", newBusinessName);
        updates.put("ownerName", newOwnerName);
        updates.put("phone", newPhone);
        updates.put("address", newAddress);
        updates.put("description", newDescription);
        updates.put("operatingHours", newOperatingHours);
        updates.put("isOpen", newIsOpen);
        FirebaseFirestore.getInstance().collection("providers").document(providerId).update(updates)
                .addOnSuccessListener(unused -> {
                    businessName = newBusinessName;
                    ownerName = newOwnerName;
                    phone = newPhone;
                    address = newAddress;
                    description = newDescription;
                    operatingHours = newOperatingHours;
                    isOpen = newIsOpen;
                    loadProviderProfile();
                    Toast.makeText(this, "Business profile updated.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        "Could not save business profile: " + error.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void loadPayoutSettings() {
        if (providerId == null) {
            Toast.makeText(this, "Provider account is unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(providerId)
                .collection("payoutProfile").document("default").get()
                .addOnSuccessListener(document -> showPayoutDialog(
                        document.exists() ? document.getData() : null))
                .addOnFailureListener(error -> Toast.makeText(this,
                        "Could not load payout settings: " + error.getMessage(),
                        Toast.LENGTH_LONG).show());
    }

    private void showPayoutDialog(Map<String, Object> existing) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(32, 0, 32, 0);

        TextView status = new TextView(this);
        status.setText(existing == null ? "No payout destination saved."
                : payoutSummary(existing));
        fields.addView(status);

        TextView payoutPolicy = new TextView(this);
        payoutPolicy.setText("Flutterwave-paid deliveries transfer the order subtotal. "
                + "Cash-on-delivery payments are collected directly by you.");
        fields.addView(payoutPolicy);

        Spinner destination = new Spinner(this);
        String[] destinationLabels = {
                "Uganda bank account", "Airtel Money", "MTN Mobile Money"
        };
        destination.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, destinationLabels));
        fields.addView(destination);

        EditText beneficiary = new EditText(this);
        beneficiary.setHint("Beneficiary full name");
        beneficiary.setSingleLine(true);
        if (existing != null) beneficiary.setText(text(existing.get("beneficiaryName")));
        fields.addView(beneficiary);

        EditText account = new EditText(this);
        account.setHint("Account number or Uganda phone (2567XXXXXXXX)");
        account.setSingleLine(true);
        account.setInputType(InputType.TYPE_CLASS_PHONE);
        fields.addView(account);

        LinearLayout bankFields = new LinearLayout(this);
        bankFields.setOrientation(LinearLayout.VERTICAL);
        Spinner bankSpinner = new Spinner(this);
        Spinner branchSpinner = new Spinner(this);
        bankFields.addView(bankSpinner);
        bankFields.addView(branchSpinner);
        fields.addView(bankFields);

        List<PayoutBank> banks = new ArrayList<>();
        List<PayoutBranch> branches = new ArrayList<>();
        ArrayAdapter<PayoutBank> bankAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, banks);
        ArrayAdapter<PayoutBranch> branchAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, branches);
        bankSpinner.setAdapter(bankAdapter);
        branchSpinner.setAdapter(branchAdapter);

        String existingType = existing == null ? ""
                : text(existing.get("destinationType"));
        destination.setSelection("airtel".equals(existingType) ? 1
                : "mtn".equals(existingType) ? 2 : 0);
        boolean isBank = destination.getSelectedItemPosition() == 0;
        bankFields.setVisibility(isBank ? View.VISIBLE : View.GONE);
        if (isBank) loadUgandaBanks(banks, bankAdapter, bankSpinner,
                branchSpinner, branches, branchAdapter);

        destination.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                boolean bankSelected = position == 0;
                bankFields.setVisibility(bankSelected ? View.VISIBLE : View.GONE);
                account.setHint(bankSelected ? "Bank account number"
                        : "Uganda phone number (2567XXXXXXXX)");
                if (bankSelected && banks.isEmpty()) {
                    loadUgandaBanks(banks, bankAdapter, bankSpinner,
                            branchSpinner, branches, branchAdapter);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        bankSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                if (position >= 0 && position < banks.size()) {
                    PayoutBank selectedBank = banks.get(position);
                    if (selectedBank.hasBranches) {
                        loadUgandaBranches(selectedBank, branches, branchAdapter, branchSpinner);
                        branchSpinner.setVisibility(View.VISIBLE);
                    } else {
                        branches.clear();
                        branchAdapter.notifyDataSetChanged();
                        branchSpinner.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        new AlertDialog.Builder(this)
                .setTitle("Payout settings")
                .setView(fields)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    int destinationPosition = destination.getSelectedItemPosition();
                    String type = destinationPosition == 0 ? "bank"
                            : destinationPosition == 1 ? "airtel" : "mtn";
                    Map<String, Object> data = new HashMap<>();
                    data.put("destinationType", type);
                    data.put("beneficiaryName", beneficiary.getText().toString().trim());
                    data.put("accountNumber", account.getText().toString().trim());
                    if ("bank".equals(type)) {
                        Object selectedBank = bankSpinner.getSelectedItem();
                        if (!(selectedBank instanceof PayoutBank)) {
                            Toast.makeText(this, "Choose a valid Uganda bank.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        PayoutBank payoutBank = (PayoutBank) selectedBank;
                        data.put("accountBank", payoutBank.code);
                        if (payoutBank.hasBranches) {
                            Object selectedBranch = branchSpinner.getSelectedItem();
                            if (!(selectedBranch instanceof PayoutBranch)) {
                                Toast.makeText(this, "Choose a bank branch.",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            data.put("destinationBranchCode", ((PayoutBranch) selectedBranch).code);
                        }
                    }
                    FirebaseFunctions.getInstance("us-central1")
                            .getHttpsCallable("saveProviderPayoutProfile").call(data)
                            .addOnSuccessListener(result -> {
                                Toast.makeText(this, "Payout destination saved securely.",
                                        Toast.LENGTH_SHORT).show();
                                loadPayoutSettings();
                            })
                            .addOnFailureListener(error -> Toast.makeText(this,
                                    "Could not save payout settings: " + error.getLocalizedMessage(),
                                    Toast.LENGTH_LONG).show());
                })
                .show();
    }

    private void loadUgandaBanks(List<PayoutBank> banks, ArrayAdapter<PayoutBank> adapter,
                                 Spinner bankSpinner, Spinner branchSpinner,
                                 List<PayoutBranch> branches,
                                 ArrayAdapter<PayoutBranch> branchAdapter) {
        FirebaseFunctions.getInstance("us-central1").getHttpsCallable("getUgandaPayoutBanks")
                .call()
                .addOnSuccessListener(result -> {
                    banks.clear();
                    Object value = result.getData();
                    if (value instanceof List<?>) {
                        for (Object item : (List<?>) value) {
                            if (item instanceof Map<?, ?>) {
                                Map<?, ?> map = (Map<?, ?>) item;
                                Object id = map.get("id");
                                Object code = map.get("code");
                                Object name = map.get("name");
                                Object hasBranches = map.get("hasBranches");
                                if (id instanceof String && code instanceof String
                                        && name instanceof String) {
                                    banks.add(new PayoutBank((String) id, (String) code,
                                            (String) name, Boolean.TRUE.equals(hasBranches)));
                                }
                            }
                        }
                    }
                    adapter.notifyDataSetChanged();
                    if (!banks.isEmpty()) bankSpinner.setSelection(0);
                    else Toast.makeText(this, "No Uganda banks are available.",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        "Could not load Uganda banks: " + error.getLocalizedMessage(),
                        Toast.LENGTH_LONG).show());
    }

    private void loadUgandaBranches(PayoutBank bank, List<PayoutBranch> branches,
                                    ArrayAdapter<PayoutBranch> adapter, Spinner spinner) {
        Map<String, Object> data = new HashMap<>();
        data.put("bankId", bank.id);
        FirebaseFunctions.getInstance("us-central1").getHttpsCallable("getUgandaPayoutBranches")
                .call(data)
                .addOnSuccessListener(result -> {
                    branches.clear();
                    Object value = result.getData();
                    if (value instanceof List<?>) {
                        for (Object item : (List<?>) value) {
                            if (item instanceof Map<?, ?>) {
                                Map<?, ?> map = (Map<?, ?>) item;
                                Object code = map.get("code");
                                Object name = map.get("name");
                                if (code instanceof String && name instanceof String) {
                                    branches.add(new PayoutBranch((String) code, (String) name));
                                }
                            }
                        }
                    }
                    adapter.notifyDataSetChanged();
                    if (branches.isEmpty()) {
                        Toast.makeText(this, "No branches are available for this bank.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        spinner.setSelection(0);
                    }
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        "Could not load bank branches: " + error.getLocalizedMessage(),
                        Toast.LENGTH_LONG).show());
    }

    private String payoutSummary(Map<String, Object> profile) {
        String type = text(profile.get("destinationType"));
        String accountNumber = text(profile.get("accountNumber"));
        String suffix = accountNumber.length() > 4
                ? accountNumber.substring(accountNumber.length() - 4) : accountNumber;
        String label = "bank".equals(type) ? "Bank account"
                : "airtel".equals(type) ? "Airtel Money" : "MTN Mobile Money";
        return "Saved destination: " + label + " ending in " + suffix
                + ". Re-enter the account number to replace it.";
    }

    private static final class PayoutBank {
        final String id;
        final String code;
        final String name;
        final boolean hasBranches;

        PayoutBank(String id, String code, String name, boolean hasBranches) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.hasBranches = hasBranches;
        }

        @Override
        public String toString() { return name; }
    }

    private static final class PayoutBranch {
        final String code;
        final String name;

        PayoutBranch(String code, String name) {
            this.code = code;
            this.name = name;
        }

        @Override
        public String toString() { return name; }
    }

    private String text(Object value) {
        return value instanceof String ? (String) value : "";
    }
}
