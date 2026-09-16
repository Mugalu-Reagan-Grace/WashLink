package com.example.washlink;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.view.View;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

public class ProviderNavBarHelper {
    public static final int TAB_DASHBOARD = 0;
    public static final int TAB_BOOKINGS = 1;
    public static final int TAB_SERVICES = 2;
    public static final int TAB_PROFILE = 3;

    // Backwards-compatible bind that leaves highlighting unchanged
    public static void bind(Activity activity) {
        bind(activity, -1);
    }

    // Bind nav listeners and highlight the active tab
    public static void bind(Activity activity, int activeTab) {
        // Dashboard
        ImageView dash = activity.findViewById(R.id.iv_nav_dashboard);
        if (dash != null) {
            dash.setOnClickListener(v -> navigateTo(activity, ProviderDashboardActivity.class));
            animateTint(activity, dash, activeTab == TAB_DASHBOARD);
        }

        // Bookings
        ImageView bookings = activity.findViewById(R.id.iv_nav_bookings);
        if (bookings != null) {
            bookings.setOnClickListener(v -> navigateTo(activity, BookingRequestsActivity.class));
            animateTint(activity, bookings, activeTab == TAB_BOOKINGS);
        }

        // Services
        ImageView services = activity.findViewById(R.id.iv_nav_services);
        if (services != null) {
            services.setOnClickListener(v -> navigateTo(activity, ManageServicesActivity.class));
            animateTint(activity, services, activeTab == TAB_SERVICES);
        }

        // Profile
        ImageView profile = activity.findViewById(R.id.iv_nav_profile);
        if (profile != null) {
            profile.setOnClickListener(v -> navigateTo(activity, ProviderProfileActivity.class));
            animateTint(activity, profile, activeTab == TAB_PROFILE);
        }
    }

    private static void navigateTo(Activity activity, Class<?> cls) {
        // Avoid relaunching the same activity
        if (activity.getClass().equals(cls)) return;
        Intent intent = new Intent(activity, cls);
        // Avoid stacking multiple provider activities: finish current after start
        activity.startActivity(intent);
        activity.finish();
    }

    private static void animateTint(Activity activity, ImageView iv, boolean active) {
        int from = iv.getColorFilter() == null ? ContextCompat.getColor(activity, R.color.nav_inactive) : ContextCompat.getColor(activity, R.color.nav_inactive);
        int to = active ? ContextCompat.getColor(activity, R.color.laundr_blue) : ContextCompat.getColor(activity, R.color.nav_inactive);

        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), from, to);
        animator.setDuration(220);
        animator.addUpdateListener(anim -> {
            int color = (int) anim.getAnimatedValue();
            iv.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        });
        animator.start();
    }
}

