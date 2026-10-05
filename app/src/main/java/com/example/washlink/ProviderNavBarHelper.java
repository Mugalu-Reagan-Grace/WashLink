package com.example.washlink;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;

public class ProviderNavBarHelper {
    public static final int TAB_DASHBOARD = 0;
    public static final int TAB_BOOKINGS = 1;
    public static final int TAB_SERVICES = 2;
    public static final int TAB_PROFILE = 3;
    public static final int TAB_CHAT = 4;

    public static void bind(Activity activity) {
        bind(activity, -1);
    }

    public static void bind(Activity activity, int activeTab) {
        View menuButton = activity.findViewById(R.id.btn_provider_menu);
        if (menuButton != null) menuButton.setOnClickListener(v -> showDrawer(activity));

        ImageView dashboard = activity.findViewById(R.id.iv_nav_dashboard);
        if (dashboard != null) {
            dashboard.setOnClickListener(v -> navigateTo(activity, ProviderDashboardActivity.class));
            animateTint(activity, dashboard, activeTab == TAB_DASHBOARD);
        }
        ImageView bookings = activity.findViewById(R.id.iv_nav_bookings);
        if (bookings != null) {
            bookings.setOnClickListener(v -> navigateTo(activity, BookingRequestsActivity.class));
            animateTint(activity, bookings, activeTab == TAB_BOOKINGS);
        }
        ImageView services = activity.findViewById(R.id.iv_nav_services);
        if (services != null) {
            services.setOnClickListener(v -> navigateTo(activity, ManageServicesActivity.class));
            animateTint(activity, services, activeTab == TAB_SERVICES);
        }
        ImageView profile = activity.findViewById(R.id.iv_nav_profile);
        if (profile != null) {
            profile.setOnClickListener(v -> navigateTo(activity, ProviderProfileActivity.class));
            animateTint(activity, profile, activeTab == TAB_PROFILE);
        }
        ImageView chat = activity.findViewById(R.id.iv_nav_chat);
        if (chat != null) {
            chat.setOnClickListener(v -> navigateTo(activity, ChatInboxActivity.class));
            animateTint(activity, chat, activeTab == TAB_CHAT);
        }
    }

    private static void showDrawer(Activity activity) {
        Dialog dialog = new Dialog(activity);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 20), dp(activity, 34), dp(activity, 20), dp(activity, 20));
        panel.setBackgroundColor(ContextCompat.getColor(activity, R.color.laundr_blue));
        scroll.addView(panel);

        TextView brand = new TextView(activity);
        brand.setText("WASHLINK\nPROVIDER WORKSPACE");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        brand.setLetterSpacing(.08f);
        brand.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 28));
        panel.addView(brand);

        TextView role = new TextView(activity);
        role.setText("Laundry provider");
        role.setTextColor(Color.WHITE);
        role.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        role.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        role.setBackgroundColor(0x24FFFFFF);
        LinearLayout.LayoutParams roleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        roleParams.bottomMargin = dp(activity, 16);
        panel.addView(role, roleParams);

        addDrawerItem(activity, dialog, panel, "Overview", ProviderDashboardActivity.class);
        addDrawerItem(activity, dialog, panel, "Orders", BookingRequestsActivity.class);
        addDrawerItem(activity, dialog, panel, "Services", ManageServicesActivity.class);
        addDrawerItem(activity, dialog, panel, "Reports", ProviderReportsActivity.class);
        addDrawerItem(activity, dialog, panel, "Riders", ProviderRidersActivity.class);
        addDrawerItem(activity, dialog, panel, "Business profile", ProviderProfileActivity.class);
        addDrawerItem(activity, dialog, panel, "Payout settings", ProviderProfileActivity.class, true);
        addDrawerItem(activity, dialog, panel, "Messages", ChatInboxActivity.class);

        View spacer = new View(activity);
        panel.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        TextView signOut = new TextView(activity);
        signOut.setText("Sign out");
        signOut.setTextColor(Color.WHITE);
        signOut.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        signOut.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        panel.addView(signOut);
        signOut.setOnClickListener(v -> {
            dialog.dismiss();
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(activity, ProviderLoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
        });

        dialog.setContentView(scroll);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(dp(activity, 320),
                    activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 48));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = width;
            attributes.height = WindowManager.LayoutParams.MATCH_PARENT;
            attributes.gravity = Gravity.START | Gravity.TOP;
            attributes.dimAmount = .55f;
            window.setAttributes(attributes);
            window.setGravity(Gravity.START | Gravity.TOP);
        }
    }

    private static void addDrawerItem(Activity activity, Dialog dialog, LinearLayout panel,
                                      String title, Class<?> destination) {
        addDrawerItem(activity, dialog, panel, title, destination, false);
    }

    private static void addDrawerItem(Activity activity, Dialog dialog, LinearLayout panel,
                                      String title, Class<?> destination, boolean payout) {
        TextView item = new TextView(activity);
        item.setText(title);
        item.setTextColor(Color.WHITE);
        item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(activity, 14), 0, dp(activity, 12), 0);
        item.setMinHeight(dp(activity, 54));
        panel.addView(item);
        item.setOnClickListener(v -> {
            dialog.dismiss();
            if (payout) {
                if (activity instanceof ProviderProfileActivity) {
                    View payoutRow = activity.findViewById(R.id.row_payment_settings);
                    if (payoutRow != null) payoutRow.performClick();
                } else {
                    Intent intent = new Intent(activity, ProviderProfileActivity.class);
                    intent.putExtra("open_payout_settings", true);
                    activity.startActivity(intent);
                    activity.finish();
                }
            } else {
                navigateTo(activity, destination);
            }
        });
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + .5f);
    }

    private static void navigateTo(Activity activity, Class<?> cls) {
        if (activity.getClass().equals(cls)) return;
        activity.startActivity(new Intent(activity, cls));
        activity.finish();
    }

    private static void animateTint(Activity activity, ImageView image, boolean active) {
        int from = ContextCompat.getColor(activity, R.color.nav_inactive);
        int to = ContextCompat.getColor(activity,
                active ? R.color.laundr_blue : R.color.nav_inactive);
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofObject(
                new android.animation.ArgbEvaluator(), from, to);
        animator.setDuration(220);
        animator.addUpdateListener(value -> image.setColorFilter((int) value.getAnimatedValue(),
                android.graphics.PorterDuff.Mode.SRC_IN));
        animator.start();
    }
}
