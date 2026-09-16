package com.example.washlink;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

public class PickupSlotSelectionActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private MaterialButton payButton;
    private CheckBox pickupFeeCheckBox;

    private LinearLayout[] dateChips;
    private TextView[] timeChips;
    private TextView callChip;
    private TextView textChip;

    private int selectedDateIndex = 0;
    private int selectedTimeIndex = 2;
    private boolean callSelected = true;
    private String selectedService = "Pickup & Delivery";
    private String selectedAddress = "Home address";
    private String selectedDateLabel = "Today";
    private String selectedTimeLabel = "10:00 AM";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pickup_slot_selection);

        if (getIntent() != null) {
            selectedService = getIntent().getStringExtra("selected_service");
            selectedAddress = getIntent().getStringExtra("selected_address");
        }

        if (selectedService == null || selectedService.trim().isEmpty()) {
            selectedService = "Pickup & Delivery";
        }
        if (selectedAddress == null || selectedAddress.trim().isEmpty()) {
            selectedAddress = "Home address";
        }

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        payButton = findViewById(R.id.btn_pay);
        pickupFeeCheckBox = findViewById(R.id.cb_pickup_fee);

        dateChips = new LinearLayout[] {
                findViewById(R.id.chip_date_today),
                findViewById(R.id.chip_date_tue),
                findViewById(R.id.chip_date_wed),
                findViewById(R.id.chip_date_thu)
        };

        timeChips = new TextView[] {
                findViewById(R.id.chip_time_800),
                findViewById(R.id.chip_time_900),
                findViewById(R.id.chip_time_1000),
                findViewById(R.id.chip_time_1100),
                findViewById(R.id.chip_time_1200),
                findViewById(R.id.chip_time_100),
                findViewById(R.id.chip_time_200),
                findViewById(R.id.chip_time_300),
                findViewById(R.id.chip_time_400),
                findViewById(R.id.chip_time_500)
        };

        callChip = findViewById(R.id.chip_contact_call);
        textChip = findViewById(R.id.chip_contact_text);

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(PickupSlotSelectionActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        for (int i = 0; i < dateChips.length; i++) {
            final int index = i;
            dateChips[i].setOnClickListener(v -> selectDate(index));
        }

        for (int i = 0; i < timeChips.length; i++) {
            final int index = i;
            timeChips[i].setOnClickListener(v -> selectTime(index));
        }

        callChip.setOnClickListener(v -> selectContact(true));
        textChip.setOnClickListener(v -> selectContact(false));

        payButton.setOnClickListener(v -> {
            if (!pickupFeeCheckBox.isChecked()) {
                Toast.makeText(this, "Pickup fee not selected", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Pickup slot selected", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(PickupSlotSelectionActivity.this, PickupConfirmationActivity.class);
            intent.putExtra("selected_service", selectedService);
            intent.putExtra("selected_address", selectedAddress);
            intent.putExtra("selected_date", selectedDateLabel);
            intent.putExtra("selected_time", selectedTimeLabel);
            intent.putExtra("selected_contact", callSelected ? "Call" : "Text");
            startActivity(intent);
        });

        selectDate(selectedDateIndex);
        selectTime(selectedTimeIndex);
        selectContact(callSelected);
    }

    private void selectDate(int index) {
        for (int i = 0; i < dateChips.length; i++) {
            View chip = dateChips[i];
            chip.setBackgroundResource(i == index ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            TextView day = (TextView) ((LinearLayout) chip).getChildAt(0);
            TextView date = (TextView) ((LinearLayout) chip).getChildAt(1);
            int dayColor = ContextCompat.getColor(this, i == index ? R.color.white : R.color.chip_unselected_text);
            int dateColor = ContextCompat.getColor(this, i == index ? R.color.white : R.color.text_primary);
            day.setTextColor(dayColor);
            date.setTextColor(dateColor);
        }
        selectedDateIndex = index;
        selectedDateLabel = getDateLabel(index);
    }

    private void selectTime(int index) {
        for (int i = 0; i < timeChips.length; i++) {
            TextView chip = timeChips[i];
            chip.setBackgroundResource(i == index ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            int textColor = ContextCompat.getColor(this, i == index ? R.color.white : R.color.chip_unselected_text);
            chip.setTextColor(textColor);
            chip.setTypeface(null, i == index ? Typeface.BOLD : Typeface.NORMAL);
        }
        selectedTimeIndex = index;
        selectedTimeLabel = getTimeLabel(index);
    }

    private void selectContact(boolean call) {
        callSelected = call;
        callChip.setBackgroundResource(call ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        textChip.setBackgroundResource(call ? R.drawable.bg_chip_unselected : R.drawable.bg_chip_selected);
        callChip.setTextColor(ContextCompat.getColor(this, call ? R.color.white : R.color.chip_unselected_text));
        textChip.setTextColor(ContextCompat.getColor(this, call ? R.color.chip_unselected_text : R.color.white));
    }

    private String getDateLabel(int index) {
        switch (index) {
            case 0: return "Today";
            case 1: return "Tue, Jun 7";
            case 2: return "Wed, Jun 8";
            case 3: return "Thu, Jun 9";
            default: return "Today";
        }
    }

    private String getTimeLabel(int index) {
        switch (index) {
            case 0: return "8:00 AM";
            case 1: return "9:00 AM";
            case 2: return "10:00 AM";
            case 3: return "11:00 AM";
            case 4: return "12:00 PM";
            case 5: return "1:00 PM";
            case 6: return "2:00 PM";
            case 7: return "3:00 PM";
            case 8: return "4:00 PM";
            case 9: return "5:00 PM";
            default: return "10:00 AM";
        }
    }
}
