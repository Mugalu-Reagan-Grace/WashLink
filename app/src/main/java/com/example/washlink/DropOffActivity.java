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

import com.google.android.material.button.MaterialButton;

public class DropOffActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private MaterialButton confirmDropoffButton;

    private LinearLayout[] dateChips;
    private TextView[] timeChips;

    private int selectedDateIndex = 1;
    private int selectedTimeIndex = 1;
    private String selectedDateLabel = "Tue, Jun 7";
    private String selectedTimeLabel = "10:00 AM";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_drop_off);

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        confirmDropoffButton = findViewById(R.id.btn_confirm_dropoff);

        dateChips = new LinearLayout[] {
                findViewById(R.id.chip_date_today),
                findViewById(R.id.chip_date_tue),
                findViewById(R.id.chip_date_wed),
                findViewById(R.id.chip_date_thu)
        };

        timeChips = new TextView[] {
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

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(DropOffActivity.this, NotificationsActivity.class);
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

        confirmDropoffButton.setOnClickListener(v -> {
            Intent intent = new Intent(DropOffActivity.this, DropOffConfirmationActivity.class);
            intent.putExtra("selected_service", "Drop Off");
            intent.putExtra("selected_date", selectedDateLabel);
            intent.putExtra("selected_time", selectedTimeLabel);
            startActivity(intent);
        });

        selectDate(selectedDateIndex);
        selectTime(selectedTimeIndex);
    }

    private void selectDate(int index) {
        for (int i = 0; i < dateChips.length; i++) {
            LinearLayout chip = dateChips[i];
            boolean selected = i == index;
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);

            TextView day = (TextView) chip.getChildAt(0);
            TextView date = (TextView) chip.getChildAt(1);
            int dayColor = ContextCompat.getColor(this, selected ? R.color.white : R.color.chip_unselected_text);
            int dateColor = ContextCompat.getColor(this, selected ? R.color.white : R.color.text_primary);
            day.setTextColor(dayColor);
            date.setTextColor(dateColor);
        }

        selectedDateIndex = index;
        selectedDateLabel = getDateLabel(index);
    }

    private void selectTime(int index) {
        for (int i = 0; i < timeChips.length; i++) {
            TextView chip = timeChips[i];
            boolean selected = i == index;
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            int textColor = ContextCompat.getColor(this, selected ? R.color.white : R.color.chip_unselected_text);
            chip.setTextColor(textColor);
            chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }

        selectedTimeIndex = index;
        selectedTimeLabel = getTimeLabel(index);
    }

    private String getDateLabel(int index) {
        switch (index) {
            case 0:
                return "Today";
            case 1:
                return "Tue, Jun 7";
            case 2:
                return "Wed, Jun 8";
            case 3:
                return "Thu, Jun 9";
            default:
                return "Tue, Jun 7";
        }
    }

    private String getTimeLabel(int index) {
        switch (index) {
            case 0:
                return "9:00 AM";
            case 1:
                return "10:00 AM";
            case 2:
                return "11:00 AM";
            case 3:
                return "12:00 PM";
            case 4:
                return "1:00 PM";
            case 5:
                return "2:00 PM";
            case 6:
                return "3:00 PM";
            case 7:
                return "4:00 PM";
            case 8:
                return "5:00 PM";
            default:
                return "10:00 AM";
        }
    }
}
