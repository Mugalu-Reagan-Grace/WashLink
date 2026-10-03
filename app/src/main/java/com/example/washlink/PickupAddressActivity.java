package com.example.washlink;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PickupAddressActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private MaterialButton useCurrentLocationButton;
    private MaterialButton addNewAddressButton;
    private MaterialButton addAddressButton;
    private MaterialButton continueCheckoutButton;
    private ConstraintLayout homeAddressCard;
    private ConstraintLayout workAddressCard;
    private TextView savedAddressTitle;
    private TextView homeAddressValue;
    private TextView workAddressValue;
    private TextView homeAddressLabel;
    private TextView workAddressLabel;
    private MaterialAutoCompleteTextView fullAddressInput;

    private String selectedAddress = null;
    private final List<String> savedAddresses = new ArrayList<>();
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pickup_address);

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        useCurrentLocationButton = findViewById(R.id.btn_use_current_location);
        addNewAddressButton = findViewById(R.id.btn_add_new_address);
        addAddressButton = findViewById(R.id.btn_add_address);
        continueCheckoutButton = findViewById(R.id.btn_continue_checkout);
        homeAddressCard = findViewById(R.id.card_address_home);
        workAddressCard = findViewById(R.id.card_address_work);
        savedAddressTitle = findViewById(R.id.tv_saved_label);
        homeAddressValue = findViewById(R.id.tv_home_value);
        workAddressValue = findViewById(R.id.tv_work_value);
        homeAddressLabel = findViewById(R.id.tv_home_label);
        workAddressLabel = findViewById(R.id.tv_work_label);
        fullAddressInput = findViewById(R.id.et_full_address);
        findViewById(R.id.label_new_label).setVisibility(View.GONE);
        findViewById(R.id.til_new_label).setVisibility(View.GONE);

        homeAddressCard.setVisibility(View.GONE);
        workAddressCard.setVisibility(View.GONE);
        savedAddressTitle.setVisibility(View.GONE);
        continueCheckoutButton.setEnabled(false);
        updateAddressSuggestions();
        loadPrimaryAddress();
        fullAddressInput.setOnItemClickListener((parent, view, position, id) -> {
            String address = (String) parent.getItemAtPosition(position);
            selectedAddress = address;
            fullAddressInput.setText(address, false);
            enableContinue();
        });

        // location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(PickupAddressActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        BottomNavHelper.bind(this);

        useCurrentLocationButton.setOnClickListener(v -> {
            // Use fused location to get last known location and reverse-geocode
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
                return;
            }

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    try {
                        List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            Address addr = addresses.get(0);
                            String addressText = addr.getAddressLine(0);
                            selectedAddress = addressText;
                            fullAddressInput.setText(addressText);
                            enableContinue();
                            Toast.makeText(this, "Using current location", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, getString(R.string.map_location_unavailable), Toast.LENGTH_SHORT).show();
                        }
                    } catch (IOException e) {
                        Toast.makeText(this, getString(R.string.map_location_unavailable), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, getString(R.string.map_location_unavailable), Toast.LENGTH_SHORT).show();
                }
            });
        });

        homeAddressCard.setOnClickListener(v -> {
            selectedAddress = homeAddressValue.getText().toString();
            fullAddressInput.setText(selectedAddress, false);
            enableContinue();
        });

        workAddressCard.setOnClickListener(v -> {
            selectedAddress = workAddressValue.getText().toString();
            fullAddressInput.setText(selectedAddress, false);
            enableContinue();
        });

        addNewAddressButton.setOnClickListener(v -> {
            fullAddressInput.requestFocus();
        });

        addAddressButton.setOnClickListener(v -> {
            String address = fullAddressInput.getText() != null ? fullAddressInput.getText().toString().trim() : "";

            if (address.isEmpty()) {
                Toast.makeText(this, "Enter an address to save.", Toast.LENGTH_SHORT).show();
                return;
            }

            addAddressButton.setEnabled(false);
            AuthRepository.getInstance().addSavedAddress(address,
                    new AuthRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            addUniqueAddress(address);
                            selectedAddress = address;
                            fullAddressInput.setText(address, false);
                            updateAddressSuggestions();
                            enableContinue();
                            addAddressButton.setEnabled(true);
                            Toast.makeText(PickupAddressActivity.this,
                                    "Address added to your saved addresses",
                                    Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String message) {
                            addAddressButton.setEnabled(true);
                            Toast.makeText(PickupAddressActivity.this,
                                    "Could not save address: " + message,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });

        continueCheckoutButton.setOnClickListener(v -> {
            if (selectedAddress == null) {
                Toast.makeText(this, "Please select or add an address", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(PickupAddressActivity.this, PickupSlotSelectionActivity.class);
            intent.putExtras(getIntent());
            intent.putExtra("selected_service", getIntent().getStringExtra("selected_service"));
            intent.putExtra("selected_address", selectedAddress);
            startActivity(intent);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                useCurrentLocationButton.performClick();
            } else {
                Toast.makeText(this, getString(R.string.map_location_permission), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadPrimaryAddress() {
        AuthRepository.getInstance().getCurrentUserAccount(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(UserAccount user) {
                if (user.getSavedAddresses() != null) {
                    savedAddresses.clear();
                    for (String address : user.getSavedAddresses()) addUniqueAddress(address);
                }
                String address = user.getAddress();
                addUniqueAddress(address);
                updateAddressSuggestions();
                if (address == null || address.trim().isEmpty()
                        || fullAddressInput.getText() != null
                        && !fullAddressInput.getText().toString().trim().isEmpty()) return;
                selectedAddress = address.trim();
                fullAddressInput.setText(selectedAddress);
                enableContinue();
            }

            @Override
            public void onError(String message) {
                savedAddressTitle.setText("Could not load saved addresses. Tap to retry.");
                savedAddressTitle.setVisibility(View.VISIBLE);
                savedAddressTitle.setClickable(true);
                savedAddressTitle.setOnClickListener(v -> loadPrimaryAddress());
                Toast.makeText(PickupAddressActivity.this,
                        "Could not load saved address: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateAddressSuggestions() {
        List<String> suggestions = new ArrayList<>(savedAddresses);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, suggestions);
        fullAddressInput.setAdapter(adapter);
        fullAddressInput.setThreshold(1);
        savedAddressTitle.setText(R.string.saved_addresses);
        savedAddressTitle.setClickable(false);
        savedAddressTitle.setOnClickListener(null);
        savedAddressTitle.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
        homeAddressCard.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
        workAddressCard.setVisibility(suggestions.size() < 2 ? View.GONE : View.VISIBLE);
        if (!suggestions.isEmpty()) {
            homeAddressLabel.setText("Saved address 1");
            homeAddressValue.setText(suggestions.get(0));
        }
        if (suggestions.size() > 1) {
            workAddressLabel.setText("Saved address 2");
            workAddressValue.setText(suggestions.get(1));
        }
    }

    private void addUniqueAddress(String address) {
        if (address == null || address.trim().isEmpty()) return;
        String normalized = address.trim();
        for (String existing : savedAddresses) {
            if (normalized.equalsIgnoreCase(existing)) return;
        }
        savedAddresses.add(normalized);
    }

    private void enableContinue() {
        continueCheckoutButton.setEnabled(true);
        continueCheckoutButton.setTextColor(ContextCompat.getColor(this, R.color.white));
        continueCheckoutButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.laundr_blue));
    }
}
