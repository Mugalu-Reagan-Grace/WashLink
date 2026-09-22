package com.example.washlink;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.firebase.auth.FirebaseAuth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {

    private final AuthRepository authRepository = AuthRepository.getInstance();
    private TextView profileName;
    private TextView profileEmail;
    private TextView profilePhone;
    private TextView savedAddress;
    private ImageView avatar;
    private UserAccount account;
    private String photoUri = "";
    private ActivityResultLauncher<String[]> imagePicker;
    private final ExecutorService locationExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        profileName = findViewById(R.id.tv_profile_name);
        profileEmail = findViewById(R.id.tv_profile_email);
        profilePhone = findViewById(R.id.tv_profile_phone);
        savedAddress = findViewById(R.id.tv_saved_address_value);
        avatar = findViewById(R.id.iv_avatar);
        imagePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null || account == null) return;
                    photoUri = uri.toString();
                    avatar.setImageURI(uri);
                    saveProfile();
                });

        loadProfile();

        ImageView backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        FrameLayout bellLayout = findViewById(R.id.iv_bell);
        if (bellLayout != null) {
            bellLayout.setOnClickListener(v -> {
                    Intent intent = new Intent(ProfileActivity.this, NotificationsActivity.class);
                    startActivity(intent);
                });
        }

        LinearLayout savedAddresses = findViewById(R.id.row_saved_addresses);
        if (savedAddresses != null) {
            savedAddresses.setOnClickListener(v -> showAddressDialog());
        }

        LinearLayout password = findViewById(R.id.row_password);
        if (password != null) {
            password.setOnClickListener(v -> sendPasswordReset());
        }

        avatar.setOnClickListener(v -> imagePicker.launch(new String[]{"image/*"}));

        LinearLayout paymentMethods = findViewById(R.id.row_payment_methods);
        if (paymentMethods != null) {
            paymentMethods.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileActivity.this, PaymentActivity.class);
                startActivity(intent);
            });
        }

        LinearLayout orderHistory = findViewById(R.id.row_order_history);
        if (orderHistory != null) {
            orderHistory.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileActivity.this, HistoryActivity.class);
                startActivity(intent);
            });
        }

        findViewById(R.id.row_profile_support).setOnClickListener(v ->
                openSupportEmail("WashLink support request"));
        findViewById(R.id.row_profile_feedback).setOnClickListener(v ->
                openSupportEmail("WashLink feedback"));
        findViewById(R.id.row_profile_privacy).setOnClickListener(v ->
                showInformationDialog(getString(R.string.profile_privacy),
                        "WashLink uses your account, contact, address, and order information to provide laundry pickup, delivery, payment, and support services. We do not sell your personal information."));
        findViewById(R.id.row_profile_terms).setOnClickListener(v ->
                showInformationDialog(getString(R.string.profile_terms),
                        "Use WashLink responsibly and provide accurate booking information. Pickup times, prices, cancellations, and refunds are subject to the service details shown before you confirm an order."));
        findViewById(R.id.row_profile_about).setOnClickListener(v ->
                showInformationDialog(getString(R.string.profile_about),
                        "WashLink connects customers with laundry providers for pickup, delivery, and drop-off services.\n\nVersion 1.0.0"));

        com.google.android.material.button.MaterialButton logoutButton = findViewById(R.id.btn_logout);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(ProfileActivity.this, SignInActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }

        BottomNavHelper.bind(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfile();
    }

    private void loadProfile() {
        authRepository.getCurrentUserAccount(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(UserAccount user) {
                account = user;
                profileName.setText(valueOrFallback(user.getName(), "Name not provided"));
                profileEmail.setText(valueOrFallback(user.getEmail(), "Email not provided"));
                profilePhone.setText(valueOrFallback(user.getPhone(), "Phone number not provided"));
                savedAddress.setText(user.getAddress() == null || user.getAddress().trim().isEmpty()
                        ? getString(R.string.profile_saved_addresses_desc)
                        : user.getAddress());
                photoUri = valueOrFallback(user.getPhotoUri(), "");
                if (!photoUri.isEmpty()) {
                    avatar.setImageURI(Uri.parse(photoUri));
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddressDialog() {
        if (account == null) {
            Toast.makeText(this, "Loading account details...", Toast.LENGTH_SHORT).show();
            return;
        }
        AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setHint(R.string.enter_your_address);
        input.setSingleLine(false);
        input.setText(account.getAddress());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setThreshold(2);
        input.setPadding(24, 12, 24, 12);

        ArrayAdapter<String> suggestions = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        input.setAdapter(suggestions);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchLocations(s.toString(), input, suggestions);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setPadding(24, 0, 24, 0);
        dialogContent.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.profile_saved_addresses)
                .setMessage(R.string.profile_address_dialog_message)
                .setView(dialogContent)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String address = input.getText().toString().trim();
                    account.setAddress(address);
                    saveProfile();
                })
                .show();
    }

    private void searchLocations(String query, AutoCompleteTextView input,
                                 ArrayAdapter<String> suggestions) {
        if (query.trim().length() < 2) {
            suggestions.clear();
            return;
        }
        locationExecutor.execute(() -> {
            List<String> results = new ArrayList<>();
            try {
                Geocoder geocoder = new Geocoder(this);
                List<Address> addresses = geocoder.getFromLocationName(query, 5);
                if (addresses != null) {
                    for (Address address : addresses) {
                        String line = address.getAddressLine(0);
                        if (line != null && !line.trim().isEmpty()) results.add(line);
                    }
                }
            } catch (IOException ignored) {
                // The typed address can still be saved when geocoder is unavailable.
            }
            mainHandler.post(() -> {
                suggestions.clear();
                suggestions.addAll(results);
                suggestions.notifyDataSetChanged();
                if (!results.isEmpty() && input.hasFocus()) input.showDropDown();
            });
        });
    }

    private void sendPasswordReset() {
        authRepository.sendPasswordReset(new AuthRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(ProfileActivity.this,
                        "Password reset instructions sent to your email.",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openSupportEmail(String subject) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + getString(R.string.profile_support_email)));
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, getString(R.string.profile_support_email),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showInformationDialog(String title, String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void saveProfile() {
        if (account == null) return;
        authRepository.updateCustomerProfile(
                valueOrFallback(account.getName(), ""),
                valueOrFallback(account.getPhone(), ""),
                valueOrFallback(account.getAddress(), ""),
                photoUri,
                new AuthRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(ProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null ? fallback : value;
    }

    @Override
    protected void onDestroy() {
        locationExecutor.shutdownNow();
        super.onDestroy();
    }
}
