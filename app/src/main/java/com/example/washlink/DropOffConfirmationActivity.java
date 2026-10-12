package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class DropOffConfirmationActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private MaterialButton continueSummaryButton;
    private TextInputLayout instructionsLayout;
    private TextView dateTimeValueText;
    private TextView charCountText;
    private TextView itemCountText;
    private TextView weightValueText;
    private ImageView minusButton;
    private ImageView plusButton;

    private int itemCount = 1;
    private int selectedWeightKg = com.example.washlink.data.BookingPricing.estimateWeightKg(itemCount);
    private boolean weightSelectedByUser;
    private String selectedService = "Drop Off";
    private String selectedDate = "";
    private String selectedTime = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_drop_off_confirmation);
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);

        if (getIntent() != null) {
            itemCount = Math.max(1, Math.min(50,
                    getIntent().getIntExtra("item_count", itemCount)));
            selectedWeightKg = Math.max(com.example.washlink.data.BookingPricing.MINIMUM_KG,
                    Math.min(100, getIntent().getIntExtra("weight_kg",
                            com.example.washlink.data.BookingPricing
                                    .estimateWeightKg(itemCount))));
            weightSelectedByUser = getIntent().hasExtra("weight_kg");
            selectedService = getIntent().getStringExtra("selected_service");
            selectedDate = getIntent().getStringExtra("selected_date");
            selectedTime = getIntent().getStringExtra("selected_time");
        }

        if (selectedService == null || selectedService.trim().isEmpty()) {
            selectedService = "Drop Off";
        }
        if (selectedDate == null || selectedDate.trim().isEmpty()) {
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault());
            selectedDate = df.format(java.util.Calendar.getInstance().getTime());
        }
        if (selectedTime == null || selectedTime.trim().isEmpty()) {
            java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());
            selectedTime = tf.format(java.util.Calendar.getInstance().getTime());
        }

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        continueSummaryButton = findViewById(R.id.btn_continue_summary);
        instructionsLayout = findViewById(R.id.til_instructions);
        dateTimeValueText = findViewById(R.id.tv_datetime_value);
        charCountText = findViewById(R.id.tv_char_count);
        itemCountText = findViewById(R.id.tv_item_count);
        weightValueText = findViewById(R.id.tv_weight_value);
        minusButton = findViewById(R.id.btn_minus);
        plusButton = findViewById(R.id.btn_plus);

        TextView serviceTypeValueText = findViewById(R.id.tv_service_type_value);
        serviceTypeValueText.setText(selectedService);
        dateTimeValueText.setText(selectedDate + " • " + selectedTime);
        itemCountText.setText(String.valueOf(itemCount));
        charCountText.setText("0/60");

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(DropOffConfirmationActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        minusButton.setOnClickListener(v -> {
            itemCount = Math.max(1, itemCount - 1);
            itemCountText.setText(String.valueOf(itemCount));
            updateEstimatedWeight();
        });

        plusButton.setOnClickListener(v -> {
            itemCount = Math.min(50, itemCount + 1);
            itemCountText.setText(String.valueOf(itemCount));
            updateEstimatedWeight();
        });
        findViewById(R.id.dropdown_weight).setOnClickListener(v -> showWeightChoices());
        updateEstimatedWeight();

        EditText instructionInput = instructionsLayout.getEditText();
        if (instructionInput != null) {
            String previousInstructions = getIntent().getStringExtra("special_instructions");
            if (previousInstructions != null) {
                instructionInput.setText(previousInstructions);
                charCountText.setText(previousInstructions.length() + "/60");
            }
            instructionInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    charCountText.setText(String.valueOf(s.length()) + "/60");
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        continueSummaryButton.setOnClickListener(v -> {
            Intent intent = new Intent(DropOffConfirmationActivity.this, OrderSummaryDropOffActivity.class);
            intent.putExtras(getIntent());
            intent.putExtra("selected_service", selectedService);
            intent.putExtra("selected_date", selectedDate);
            intent.putExtra("selected_time", selectedTime);
            intent.putExtra("item_count", itemCount);
            intent.putExtra("weight_kg", selectedWeightKg);
            if (instructionInput != null && instructionInput.getText() != null) {
                intent.putExtra("special_instructions", instructionInput.getText().toString().trim());
            }
            startActivity(intent);
        });
    }

    private void updateEstimatedWeight() {
        if (!weightSelectedByUser) {
            selectedWeightKg = com.example.washlink.data.BookingPricing.estimateWeightKg(itemCount);
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
