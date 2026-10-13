package com.example.washlink;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.washlink.data.BookingDateLabels;
import com.example.washlink.data.BookingSlotAvailability;
import com.google.android.material.button.MaterialButton;

import java.util.Calendar;
import java.util.Locale;

public class PickupSlotSelectionActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private MaterialButton payButton;
    private LinearLayout[] dateChips;
    private TextView[] timeChips;
    private TextView callChip;
    private TextView textChip;

    private int selectedDateIndex = 0;
    private int selectedTimeIndex = -1;
    private boolean callSelected = true;
    private String selectedService = "Pickup & Delivery";
    private String selectedAddress = "Home address";
    private String selectedDateLabel = "";
    private String selectedTimeLabel = "";
    private Calendar dateRangeStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_customer_pickup_slot_selection);
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);

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

        dateChips = new LinearLayout[] {
                findViewById(R.id.chip_date_today),
                findViewById(R.id.chip_date_tue),
                findViewById(R.id.chip_date_wed),
                findViewById(R.id.chip_date_thu)
        };
        dateRangeStart = Calendar.getInstance();
        updateDateChipLabels();

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
            if (!isTimeAvailable(selectedTimeIndex)) {
                refreshExpiredSelection();
                Toast.makeText(this, "That pickup time has passed. Choose another slot.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Pickup slot selected", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(PickupSlotSelectionActivity.this, PickupConfirmationActivity.class);
            intent.putExtras(getIntent());
            intent.putExtra("selected_service", selectedService);
            intent.putExtra("selected_address", selectedAddress);
            intent.putExtra("selected_date", selectedDateLabel);
            intent.putExtra("selected_time", selectedTimeLabel);
            intent.putExtra("selected_contact", callSelected ? "Call" : "Text");
            startActivity(intent);
        });

        selectInitialAvailableSlot();
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
        updateAvailableTimes();
        if (!isTimeAvailable(selectedTimeIndex)) {
            int nextTime = findFirstAvailableTime(index);
            if (nextTime >= 0) selectTime(nextTime);
            else clearSelectedTime();
        }
    }

    private void selectTime(int index) {
        if (!isTimeAvailable(index)) return;
        for (int i = 0; i < timeChips.length; i++) {
            TextView chip = timeChips[i];
            chip.setBackgroundResource(i == index ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            int textColor = ContextCompat.getColor(this, i == index ? R.color.white : R.color.chip_unselected_text);
            chip.setTextColor(textColor);
            chip.setTypeface(null, i == index ? Typeface.BOLD : Typeface.NORMAL);
        }
        selectedTimeIndex = index;
        selectedTimeLabel = getTimeLabel(index);
        payButton.setEnabled(true);
    }

    private void selectInitialAvailableSlot() {
        Calendar now = Calendar.getInstance();
        String previousDate = getIntent().getStringExtra("selected_date");
        String previousTime = getIntent().getStringExtra("selected_time");
        for (int dayOffset = 0; dayOffset < dateChips.length; dayOffset++) {
            if (!getDateLabel(dayOffset).equalsIgnoreCase(previousDate == null
                    ? "" : previousDate.trim())) continue;
            for (int timeIndex = 0; timeIndex < timeChips.length; timeIndex++) {
                if (getTimeLabel(timeIndex).equalsIgnoreCase(previousTime == null
                        ? "" : previousTime.trim())
                        && isTimeAvailable(timeIndex, dayOffset, now)) {
                    selectDate(dayOffset);
                    selectTime(timeIndex);
                    return;
                }
            }
        }
        for (int dayOffset = 0; dayOffset < dateChips.length; dayOffset++) {
            int firstTime = findFirstAvailableTime(dayOffset, now);
            if (firstTime >= 0) {
                selectDate(dayOffset);
                selectTime(firstTime);
                return;
            }
        }
        selectDate(0);
        clearSelectedTime();
    }

    private void updateAvailableTimes() {
        Calendar now = Calendar.getInstance();
        for (int i = 0; i < timeChips.length; i++) {
            boolean available = isTimeAvailable(i, selectedDateIndex, now);
            TextView chip = timeChips[i];
            chip.setEnabled(available);
            chip.setAlpha(available ? 1f : 0.45f);
            chip.setContentDescription(getTimeLabel(i)
                    + (available ? "" : ", unavailable"));
        }
    }

    private boolean isTimeAvailable(int index) {
        return isTimeAvailable(index, selectedDateIndex, Calendar.getInstance());
    }

    private boolean isTimeAvailable(int index, int dayOffset, Calendar now) {
        int[] hours = {8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
        return index >= 0 && index < hours.length
                && BookingSlotAvailability.isFutureSlot(
                        dateRangeStart, dayOffset, hours[index], now);
    }

    private int findFirstAvailableTime(int dayOffset) {
        return findFirstAvailableTime(dayOffset, Calendar.getInstance());
    }

    private int findFirstAvailableTime(int dayOffset, Calendar now) {
        for (int i = 0; i < timeChips.length; i++) {
            if (isTimeAvailable(i, dayOffset, now)) return i;
        }
        return -1;
    }

    private void clearSelectedTime() {
        selectedTimeIndex = -1;
        selectedTimeLabel = "";
        for (TextView chip : timeChips) {
            chip.setBackgroundResource(R.drawable.bg_chip_unselected);
            chip.setTextColor(ContextCompat.getColor(this, R.color.chip_unselected_text));
            chip.setTypeface(null, Typeface.NORMAL);
        }
        payButton.setEnabled(false);
    }

    private void refreshExpiredSelection() {
        updateAvailableTimes();
        int nextTime = findFirstAvailableTime(selectedDateIndex);
        if (nextTime >= 0) selectTime(nextTime);
        else clearSelectedTime();
    }

    private void selectContact(boolean call) {
        callSelected = call;
        callChip.setBackgroundResource(call ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        textChip.setBackgroundResource(call ? R.drawable.bg_chip_unselected : R.drawable.bg_chip_selected);
        callChip.setTextColor(ContextCompat.getColor(this, call ? R.color.white : R.color.chip_unselected_text));
        textChip.setTextColor(ContextCompat.getColor(this, call ? R.color.chip_unselected_text : R.color.white));
    }

    private String getDateLabel(int index) {
        return BookingDateLabels.selectedDate(dateRangeStart, index, Locale.getDefault());
    }

    private void updateDateChipLabels() {
        for (int i = 0; i < dateChips.length; i++) {
            LinearLayout chip = dateChips[i];
            ((TextView) chip.getChildAt(0)).setText(i == 0
                    ? getString(R.string.today)
                    : BookingDateLabels.weekday(dateRangeStart, i, Locale.getDefault()));
            ((TextView) chip.getChildAt(1)).setText(
                    BookingDateLabels.chipDate(dateRangeStart, i, Locale.getDefault()));
        }
    }

    private String getTimeLabel(int index) {
        String[] times = {
                "8:00 AM", "9:00 AM", "10:00 AM", "11:00 AM",
                "12:00 PM", "1:00 PM", "2:00 PM", "3:00 PM",
                "4:00 PM", "5:00 PM"
        };
        return times[Math.max(0, Math.min(index, times.length - 1))];
    }
}
