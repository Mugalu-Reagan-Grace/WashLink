package com.example.washlink;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

public final class BottomNavHelper {
    private BottomNavHelper() {
    }

    public static final int TAB_HOME = 0;
    public static final int TAB_SERVICES = 1;
    public static final int TAB_TRACKING = 2;
    public static final int TAB_HISTORY = 3;
    public static final int TAB_PROFILE = 4;

    public static void bind(Activity activity) {
        bind(activity, activity.findViewById(android.R.id.content));
    }

    public static void bind(Activity activity, View rootView) {
        if (rootView == null) {
            return;
        }

        // detect active tab by activity class
        int activeTab = -1;
        Class<?> cls = activity.getClass();
        if (cls.equals(HomeActivity.class)) activeTab = TAB_HOME;
        else if (cls.equals(SelectServiceActivity.class) || cls.equals(NearbyProvidersActivity.class)) activeTab = TAB_SERVICES;
        else if (cls.equals(OrderTrackingActivity.class)) activeTab = TAB_TRACKING;
        else if (cls.equals(HistoryActivity.class)) activeTab = TAB_HISTORY;
        else if (cls.equals(ProfileActivity.class)) activeTab = TAB_PROFILE;

        View navHome = rootView.findViewById(R.id.nav_home);
        View navServices = rootView.findViewById(R.id.nav_services);
        View navTracking = rootView.findViewById(R.id.nav_tracking);
        View navHistory = rootView.findViewById(R.id.nav_history);
        View navProfile = rootView.findViewById(R.id.nav_profile);

        if (navHome != null) {
            navHome.setOnClickListener(v -> openScreen(activity, HomeActivity.class));
            animateNavIcon(activity, navHome, activeTab == TAB_HOME);
        }
        if (navServices != null) {
            navServices.setOnClickListener(v -> openScreen(activity, SelectServiceActivity.class));
            animateNavIcon(activity, navServices, activeTab == TAB_SERVICES);
        }
        if (navTracking != null) {
            navTracking.setOnClickListener(v -> openScreen(activity, OrderTrackingActivity.class));
            animateNavIcon(activity, navTracking, activeTab == TAB_TRACKING);
        }
        if (navHistory != null) {
            navHistory.setOnClickListener(v -> openScreen(activity, HistoryActivity.class));
            animateNavIcon(activity, navHistory, activeTab == TAB_HISTORY);
        }
        if (navProfile != null) {
            navProfile.setOnClickListener(v -> openScreen(activity, ProfileActivity.class));
            animateNavIcon(activity, navProfile, activeTab == TAB_PROFILE);
        }
    }

    private static void animateNavIcon(Activity activity, View navItem, boolean active) {
        // navItem expected to be a ViewGroup with ImageView at index 0
        if (!(navItem instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) navItem;
        if (vg.getChildCount() == 0) return;
        View child = vg.getChildAt(0);
        if (!(child instanceof ImageView)) return;
        ImageView iv = (ImageView) child;

        int from = ContextCompat.getColor(activity, R.color.nav_inactive);
        int to = active ? ContextCompat.getColor(activity, R.color.laundr_blue) : ContextCompat.getColor(activity, R.color.nav_inactive);

        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), from, to);
        animator.setDuration(220);
        animator.addUpdateListener(anim -> {
            int color = (int) anim.getAnimatedValue();
            iv.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        });
        animator.start();
    }

    private static void openScreen(Activity activity, Class<?> targetActivity) {
        if (activity.getClass().equals(targetActivity)) {
            return;
        }

        Intent intent = new Intent(activity, targetActivity);
        activity.startActivity(intent);
    }
}
