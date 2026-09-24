package com.example.washlink;

import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ProviderRegistrationActivity extends AppCompatActivity {
    private static final int LOCATION_REQUEST = 520;
    private FusedLocationProviderClient locationClient;
    private double businessLatitude;
    private double businessLongitude;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_registration);
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        redirectIfAlreadySignedIn();

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        View create = findViewById(R.id.btn_create_business_account);
        if (create != null) create.setOnClickListener(v -> handleProviderRegistration());
        View useLocation = findViewById(R.id.btn_provider_current_location);
        if (useLocation != null) useLocation.setOnClickListener(v -> useCurrentLocation());

        View signInPrompt = findViewById(R.id.tv_sign_in_prompt);
        if (signInPrompt != null) signInPrompt.setOnClickListener(v -> startActivity(new Intent(this, ProviderLoginActivity.class)));
    }

    private void redirectIfAlreadySignedIn() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }

        AuthRepository.getInstance().getCurrentSession(new AuthRepository.RoleCallback() {
            @Override
            public void onResult(UserAccount user) {
                if (user == null) {
                    return;
                }
                if (UserAccount.ROLE_PROVIDER.equals(user.getRole())) {
                    Intent intent = new Intent(ProviderRegistrationActivity.this, ProviderDashboardActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    Intent intent = new Intent(ProviderRegistrationActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                // stay on registration screen if role lookup fails
            }
        });
    }

    private void handleProviderRegistration() {
        String businessName = getTextFromInputLayout(R.id.til_business_name);
        String ownerName = getTextFromInputLayout(R.id.til_owner_name);
        String email = getTextFromInputLayout(R.id.til_business_email);
        String phone = getTextFromInputLayout(R.id.til_phone);
        String address = getTextFromInputLayout(R.id.til_address);
        String password = getTextFromInputLayout(R.id.til_password);

        if (businessName.isEmpty() || ownerName.isEmpty() || email.isEmpty() || phone.isEmpty() || address.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all business details", Toast.LENGTH_SHORT).show();
            return;
        }

        if (businessLatitude == 0d && businessLongitude == 0d) {
            Toast.makeText(this, "Please use your current location before creating the business account.", Toast.LENGTH_LONG).show();
            return;
        }

        AuthRepository.getInstance().registerProvider(businessName, ownerName, email, phone, address, password,
                businessLatitude, businessLongitude,
                new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess(UserAccount user) {
                        Toast.makeText(ProviderRegistrationActivity.this, "Business account created", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(ProviderRegistrationActivity.this, ProviderDashboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ProviderRegistrationActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void useCurrentLocation() {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                        && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, LOCATION_REQUEST);
                    return;
                }
                locationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location == null) {
                        Toast.makeText(this, R.string.map_location_unavailable, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    businessLatitude = location.getLatitude();
                    businessLongitude = location.getLongitude();
                    TextInputLayout addressLayout = findViewById(R.id.til_address);
                    EditText addressInput = addressLayout.getEditText();
                    if (addressInput != null) {
                        try {
                            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                            List<Address> results = geocoder.getFromLocation(
                                    businessLatitude, businessLongitude, 1);
                            if (results != null && !results.isEmpty()
                                    && results.get(0).getAddressLine(0) != null) {
                                addressInput.setText(results.get(0).getAddressLine(0));
                            }
                        } catch (IOException ignored) {
                            // Coordinates remain usable if reverse geocoding is unavailable.
                        }
                    }
                    TextView status = findViewById(R.id.tv_provider_location_status);
                    status.setText(getString(R.string.provider_location_saved)
                            + String.format(Locale.US, " (%.5f, %.5f)",
                            businessLatitude, businessLongitude));
                }).addOnFailureListener(error ->
                        Toast.makeText(this, R.string.map_location_unavailable, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                if (requestCode == LOCATION_REQUEST) {
                    for (int result : grantResults) {
                        if (result == PackageManager.PERMISSION_GRANTED) {
                            useCurrentLocation();
                            return;
                        }
                    }
                    Toast.makeText(this, R.string.map_location_permission, Toast.LENGTH_LONG).show();
                }
    }

    private String getTextFromInputLayout(int layoutId) {
        TextInputLayout layout = findViewById(layoutId);
        if (layout == null) return "";
        TextInputEditText input = (TextInputEditText) layout.getEditText();
        if (input == null) return "";
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
