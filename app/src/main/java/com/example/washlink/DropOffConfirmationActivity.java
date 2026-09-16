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
    private ImageView minusButton;
    private ImageView plusButton;

    private int itemCount = 15;
    private String selectedService = "Drop Off";
    private String selectedDate = "Tue, Jun 7";
    private String selectedTime = "10:00 AM";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_drop_off_confirmation);

        if (getIntent() != null) {
            selectedService = getIntent().getStringExtra("selected_service");
            selectedDate = getIntent().getStringExtra("selected_date");
            selectedTime = getIntent().getStringExtra("selected_time");
        }

        if (selectedService == null || selectedService.trim().isEmpty()) {
            selectedService = "Drop Off";
        }
        if (selectedDate == null || selectedDate.trim().isEmpty()) {
            selectedDate = "Tue, Jun 7";
        }
        if (selectedTime == null || selectedTime.trim().isEmpty()) {
            selectedTime = "10:00 AM";
        }

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        continueSummaryButton = findViewById(R.id.btn_continue_summary);
        instructionsLayout = findViewById(R.id.til_instructions);
        dateTimeValueText = findViewById(R.id.tv_datetime_value);
        charCountText = findViewById(R.id.tv_char_count);
        itemCountText = findViewById(R.id.tv_item_count);
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
        });

        plusButton.setOnClickListener(v -> {
            itemCount = Math.min(50, itemCount + 1);
            itemCountText.setText(String.valueOf(itemCount));
        });

        EditText instructionInput = instructionsLayout.getEditText();
        if (instructionInput != null) {
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
            intent.putExtra("selected_service", selectedService);
            intent.putExtra("selected_date", selectedDate);
            intent.putExtra("selected_time", selectedTime);
            intent.putExtra("item_count", itemCount);
            if (instructionInput != null && instructionInput.getText() != null) {
                intent.putExtra("special_instructions", instructionInput.getText().toString().trim());
            }
            startActivity(intent);
        });
    }
}
