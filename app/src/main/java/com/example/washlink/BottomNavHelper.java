package com.example.washlink;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

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
            updateNavState(activity, navHome, activeTab == TAB_HOME);
        }
        if (navServices != null) {
            navServices.setOnClickListener(v -> openScreen(activity, SelectServiceActivity.class));
            updateNavState(activity, navServices, activeTab == TAB_SERVICES);
        }
        if (navTracking != null) {
            navTracking.setOnClickListener(v -> openScreen(activity, OrderTrackingActivity.class));
            updateNavState(activity, navTracking, activeTab == TAB_TRACKING);
        }
        if (navHistory != null) {
            navHistory.setOnClickListener(v -> openScreen(activity, HistoryActivity.class));
            updateNavState(activity, navHistory, activeTab == TAB_HISTORY);
        }
        if (navProfile != null) {
            navProfile.setOnClickListener(v -> openScreen(activity, ProfileActivity.class));
            updateNavState(activity, navProfile, activeTab == TAB_PROFILE);
        }
    }

    private static void updateNavState(Activity activity, View navItem, boolean active) {
        if (!(navItem instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) navItem;

        ImageView icon = findFirstImageView(group);
        TextView label = findFirstTextView(group);

        int activeColor = ContextCompat.getColor(activity, R.color.laundr_blue);
        int inactiveColor = ContextCompat.getColor(activity, R.color.nav_inactive);

        if (icon != null) {
            icon.setColorFilter(active ? activeColor : inactiveColor, PorterDuff.Mode.SRC_IN);
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofObject(
                    new android.animation.ArgbEvaluator(),
                    inactiveColor,
                    active ? activeColor : inactiveColor);
            animator.setDuration(180);
            animator.addUpdateListener(anim ->
                    icon.setColorFilter((int) anim.getAnimatedValue(), PorterDuff.Mode.SRC_IN));
            animator.start();
        }

        if (label != null) {
            label.setTextColor(active ? activeColor : inactiveColor);
            label.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }

        navItem.setBackgroundResource(active ? R.drawable.bg_card_selected_outline : 0);
    }

    private static ImageView findFirstImageView(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ImageView) return (ImageView) child;
            if (child instanceof ViewGroup) {
                ImageView nested = findFirstImageView((ViewGroup) child);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static TextView findFirstTextView(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView) return (TextView) child;
            if (child instanceof ViewGroup) {
                TextView nested = findFirstTextView((ViewGroup) child);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static void openScreen(Activity activity, Class<?> targetActivity) {
        if (activity.getClass().equals(targetActivity)) {
            return;
        }

        Intent intent = new Intent(activity, targetActivity);
        activity.startActivity(intent);
    }
}
