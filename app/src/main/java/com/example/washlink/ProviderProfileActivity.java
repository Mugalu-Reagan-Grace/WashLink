package com.example.washlink;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.data.ProfilePhotoManager;
import com.example.washlink.models.UserAccount;
import com.example.washlink.ui.ThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProviderProfileActivity extends WashLinkActivity {
    private String providerId;
    private String ownerName = "";
    private String businessName = "";
    private String phone = "";
    private String address = "";
    private String description = "";
    private String operatingHours = "";
    private boolean isOpen = true;
    private ImageView profilePhoto;
    private ListenerRegistration profileRegistration;
    private String displayedProfilePhotoUrl;
    private ActivityResultLauncher<String[]> photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_provider_profile);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);

        profilePhoto = findViewById(R.id.iv_logo);
        photoPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::uploadProfilePhoto);
        if (profilePhoto != null) {
            profilePhoto.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            profilePhoto.setClipToOutline(true);
            profilePhoto.setContentDescription(getString(R.string.edit_profile_picture));
            profilePhoto.setOnClickListener(v -> photoPicker.launch(new String[]{
                    "image/jpeg", "image/png", "image/webp", "image/gif"}));
        }

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
        SwitchCompat darkModeSwitch = findViewById(R.id.switch_provider_dark_mode);
        if (darkModeSwitch != null) {
            darkModeSwitch.setChecked(ThemeManager.isDarkMode(this,
                    ThemeManager.ROLE_PROVIDER));
            darkModeSwitch.setOnCheckedChangeListener((button, checked) -> {
                ThemeManager.changeTheme(this, ThemeManager.ROLE_PROVIDER,
                        checked ? "dark" : "light");
            });
        }
        findViewById(R.id.row_provider_theme).setOnClickListener(v ->
                ThemeManager.showAppearancePicker(this, ThemeManager.ROLE_PROVIDER));
        findViewById(R.id.row_payment_settings).setOnClickListener(v -> loadPayoutSettings());
        if (getIntent().getBooleanExtra("open_payout_settings", false)) {
            findViewById(R.id.row_payment_settings).post(this::loadPayoutSettings);
        }

        if (providerId != null) loadProviderProfile();

        MaterialButton logoutButton = findViewById(R.id.btn_logout);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> {
                ThemeManager.resetSignedOut(this);
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
        if (profileRegistration != null) profileRegistration.remove();
        profileRegistration = FirebaseFirestore.getInstance().collection("providers")
                .document(providerId).addSnapshotListener((document, error) -> {
                    if (error != null || document == null || !document.exists()) {
                        Toast.makeText(this, "Could not load business profile: "
                                + (error == null ? "Profile unavailable." : error.getMessage()),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    businessName = text(document.get("businessName"));
                    ownerName = text(document.get("ownerName"));
                    phone = text(document.get("phone"));
                    address = text(document.get("address"));
                    description = text(document.get("description"));
                    operatingHours = text(document.get("operatingHours"));
                    Object availability = document.get("isOpen");
                    isOpen = !(availability instanceof Boolean) || (Boolean) availability;
                    boolean approved = Boolean.TRUE.equals(document.get("isApproved"));
                    String authPhotoUrl = FirebaseAuth.getInstance().getCurrentUser() == null
                            || FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl() == null ? ""
                            : FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl().toString();
                    String photoUrl = text(document.get("profilePhotoUrl"));
                    if (!photoUrl.equals(displayedProfilePhotoUrl)) {
                        displayedProfilePhotoUrl = photoUrl;
                        ProfilePhotoManager.display(profilePhoto, photoUrl, authPhotoUrl,
                                FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                                        : FirebaseAuth.getInstance().getCurrentUser().getEmail());
                    }
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
                });
    }

    private void uploadProfilePhoto(Uri imageUri) {
        if (imageUri == null || providerId == null) return;
        profilePhoto.setImageURI(imageUri);
        ProfilePhotoManager.upload(this, imageUri, providerId,
                new ProfilePhotoManager.UploadCallback() {
            @Override public void onSuccess(String downloadUrl) {
                FirebaseFirestore.getInstance().collection("providers").document(providerId)
                        .update("profilePhotoUrl", downloadUrl)
                        .addOnSuccessListener(unused -> {
                            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                                FirebaseAuth.getInstance().getCurrentUser().updateProfile(
                                        new UserProfileChangeRequest.Builder()
                                                .setPhotoUri(Uri.parse(downloadUrl)).build());
                            }
                            loadProviderProfile();
                            Toast.makeText(ProviderProfileActivity.this,
                                    "Profile picture updated.", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(error -> Toast.makeText(ProviderProfileActivity.this,
                                "Could not save profile picture: " + error.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
            @Override public void onError(String message) {
                loadProviderProfile();
                Toast.makeText(ProviderProfileActivity.this, message, Toast.LENGTH_LONG).show();
            }
                });
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
        Toast.makeText(this,
                "Provider payouts are disabled. Customer payments are processed through Pesapal.",
                Toast.LENGTH_LONG).show();
    }

    private String text(Object value) {
        return value instanceof String ? (String) value : "";
    }

    @Override
    protected void onDestroy() {
        if (profileRegistration != null) profileRegistration.remove();
        super.onDestroy();
    }
}
