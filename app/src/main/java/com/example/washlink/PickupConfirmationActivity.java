package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.BookingPricing;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class PickupConfirmationActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private MaterialButton confirmPickupButton;
    private TextView itemCountText;
    private ImageView minusButton;
    private ImageView plusButton;
    private TextView weightValueText;

    private int itemCount = 1;
    private int selectedWeightKg = BookingPricing.estimateWeightKg(itemCount);
    private boolean weightSelectedByUser;
    private String selectedService = "Pickup & Delivery";
    private String selectedAddress = "Home address";
    private String selectedDate = "";
    private String selectedTime = "";
    private String selectedContact = "Call";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pickup_confirmation);
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);

        if (getIntent() != null) {
            itemCount = Math.max(1, Math.min(50,
                    getIntent().getIntExtra("item_count", itemCount)));
            selectedWeightKg = Math.max(BookingPricing.MINIMUM_KG,
                    Math.min(100, getIntent().getIntExtra("weight_kg",
                            BookingPricing.estimateWeightKg(itemCount))));
            weightSelectedByUser = getIntent().hasExtra("weight_kg");
            selectedService = getIntent().getStringExtra("selected_service");
            selectedAddress = getIntent().getStringExtra("selected_address");
            selectedDate = getIntent().getStringExtra("selected_date");
            selectedTime = getIntent().getStringExtra("selected_time");
            selectedContact = getIntent().getStringExtra("selected_contact");
        }

        if (selectedService == null || selectedService.trim().isEmpty()) {
            selectedService = "Pickup & Delivery";
        }
        if (selectedAddress == null || selectedAddress.trim().isEmpty()) {
            selectedAddress = "Home address";
        }
        if (selectedDate == null || selectedDate.trim().isEmpty()) {
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
            selectedDate = dateFmt.format(cal.getTime());
        }
        if (selectedTime == null || selectedTime.trim().isEmpty()) {
            Calendar cal2 = Calendar.getInstance();
            SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
            selectedTime = timeFmt.format(cal2.getTime());
        }
        if (selectedContact == null || selectedContact.trim().isEmpty()) {
            selectedContact = "Call";
        }

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        confirmPickupButton = findViewById(R.id.btn_confirm_pickup);
        itemCountText = findViewById(R.id.tv_item_count);
        minusButton = findViewById(R.id.btn_minus);
        plusButton = findViewById(R.id.btn_plus);
        weightValueText = findViewById(R.id.tv_weight_value);

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(PickupConfirmationActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        plusButton.setOnClickListener(v -> {
            itemCount = Math.min(50, itemCount + 1);
            itemCountText.setText(String.valueOf(itemCount));
            updateEstimatedWeight();
        });

        minusButton.setOnClickListener(v -> {
            itemCount = Math.max(1, itemCount - 1);
            itemCountText.setText(String.valueOf(itemCount));
            updateEstimatedWeight();
        });
        findViewById(R.id.dropdown_weight).setOnClickListener(v -> showWeightChoices());
        // initialize item count view
        itemCountText.setText(String.valueOf(itemCount));
        updateEstimatedWeight();

        confirmPickupButton.setOnClickListener(v -> {
            Intent intent = new Intent(PickupConfirmationActivity.this, OrderSummaryActivity.class);
            intent.putExtras(getIntent());
            intent.putExtra("selected_service", selectedService);
            intent.putExtra("selected_address", selectedAddress);
            intent.putExtra("selected_date", selectedDate);
            intent.putExtra("selected_time", selectedTime);
            intent.putExtra("selected_contact", selectedContact);
            intent.putExtra("item_count", itemCount);
            intent.putExtra("weight_kg", selectedWeightKg);
            startActivity(intent);
        });
    }

    private void updateEstimatedWeight() {
        if (!weightSelectedByUser) {
            selectedWeightKg = BookingPricing.estimateWeightKg(itemCount);
        }
        weightValueText.setText(selectedWeightKg + " kg");
    }

    private void showWeightChoices() {
        String[] choices = {"5 kg", "10 kg", "15 kg", "20 kg", "25 kg", "50 kg"};
        int[] weights = {5, 10, 15, 20, 25, 50};
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.estimated_weight)
                .setItems(choices, (dialog, which) -> {
                    selectedWeightKg = weights[which];
                    weightSelectedByUser = true;
                    updateEstimatedWeight();
                })
                .show();
    }
}
