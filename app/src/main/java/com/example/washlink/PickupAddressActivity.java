package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class PickupAddressActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private MaterialButton useCurrentLocationButton;
    private MaterialButton addNewAddressButton;
    private MaterialButton addAddressButton;
    private MaterialButton continueCheckoutButton;
    private ConstraintLayout homeAddressCard;
    private ConstraintLayout workAddressCard;
    private TextInputEditText newLabelInput;
    private TextInputEditText fullAddressInput;

    private String selectedAddress = null;

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
        newLabelInput = findViewById(R.id.et_new_address_label);
        fullAddressInput = findViewById(R.id.et_full_address);

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(PickupAddressActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        BottomNavHelper.bind(this);

        useCurrentLocationButton.setOnClickListener(v -> {
            startActivity(new Intent(this, NearbyLaundryMapActivity.class));
        });

        homeAddressCard.setOnClickListener(v -> {
            selectedAddress = getString(R.string.address_home_label);
            enableContinue();
        });

        workAddressCard.setOnClickListener(v -> {
            selectedAddress = getString(R.string.address_work_label);
            enableContinue();
        });

        addNewAddressButton.setOnClickListener(v -> {
            Toast.makeText(this, "Add a new address below", Toast.LENGTH_SHORT).show();
        });

        addAddressButton.setOnClickListener(v -> {
            String label = newLabelInput.getText() != null ? newLabelInput.getText().toString().trim() : "";
            String address = fullAddressInput.getText() != null ? fullAddressInput.getText().toString().trim() : "";

            if (label.isEmpty() || address.isEmpty()) {
                Toast.makeText(this, "Please fill in the address details", Toast.LENGTH_SHORT).show();
                return;
            }

            selectedAddress = label + " - " + address;
            enableContinue();
            Toast.makeText(this, "Address added", Toast.LENGTH_SHORT).show();
        });

        continueCheckoutButton.setOnClickListener(v -> {
            if (selectedAddress == null) {
                Toast.makeText(this, "Please select or add an address", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(PickupAddressActivity.this, PickupSlotSelectionActivity.class);
            intent.putExtra("selected_service", getIntent().getStringExtra("selected_service"));
            intent.putExtra("selected_address", selectedAddress);
            startActivity(intent);
        });
    }

    private void enableContinue() {
        continueCheckoutButton.setEnabled(true);
        continueCheckoutButton.setTextColor(ContextCompat.getColor(this, R.color.white));
        continueCheckoutButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.laundr_blue));
    }
}
