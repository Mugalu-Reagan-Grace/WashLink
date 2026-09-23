package com.example.washlink.ui.customer;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.washlink.R;

public class MainActivity extends AppCompatActivity {
    public static final String TAB_HOME = "home";
    public static final String TAB_SERVICES = "services";
    public static final String TAB_TRACKING = "tracking";
    public static final String TAB_HISTORY = "history";
    public static final String TAB_PROFILE = "profile";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            showTab(TAB_HOME);
        }
    }

    public void showTab(String tab) {
        showTab(tab, null);
    }

    public void showTab(String tab, String bookingId) {
        Fragment fragment;
        if (TAB_SERVICES.equals(tab)) {
            fragment = new ServicesFragment();
        } else if (TAB_TRACKING.equals(tab)) {
            fragment = new TrackingFragment();
        } else if (TAB_HISTORY.equals(tab)) {
            fragment = new HistoryFragment();
        } else if (TAB_PROFILE.equals(tab)) {
            fragment = new ProfileFragment();
        } else {
            fragment = new HomeFragment();
        }
        if (bookingId != null) {
            Bundle args = new Bundle();
            args.putString("booking_id", bookingId);
            fragment.setArguments(args);
        }
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.main_fragment_container, fragment, tab)
                .commit();
    }

    @Override
    public void onBackPressed() {
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.main_fragment_container);
        if (!(current instanceof HomeFragment)) {
            showTab(TAB_HOME);
            return;
        }
        super.onBackPressed();
    }
}
