package com.example.washlink.ui;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.washlink.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;
import java.util.WeakHashMap;

public final class NotificationBadgeManager {
    private static final Map<Activity, BadgeState> STATES = new WeakHashMap<>();
    private static final int BADGE_TAG = 0x574C4E;

    private NotificationBadgeManager() {}

    public static void onActivityResumed(Activity activity) {
        View bellView = activity.findViewById(R.id.iv_bell);
        if (!(bellView instanceof FrameLayout)) return;

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        BadgeState state = STATES.get(activity);
        if (state == null || state.bell != bellView) {
            state = new BadgeState((FrameLayout) bellView);
            STATES.put(activity, state);
        }
        if (state.registration != null) return;
        state.showCount(state.count);
        BadgeState activeState = state;
        state.registration = FirebaseFirestore.getInstance().collection("users")
                .document(uid).collection("notifications")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || activity.isFinishing()) return;
                    int unreadCount = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot document
                            : snapshot.getDocuments()) {
                        if (!Boolean.TRUE.equals(document.getBoolean("read"))) unreadCount++;
                    }
                    activeState.count = unreadCount;
                    activeState.showCount(unreadCount);
                });
    }

    public static void onActivityPaused(Activity activity) {
        BadgeState state = STATES.get(activity);
        if (state == null || state.registration == null) return;
        state.registration.remove();
        state.registration = null;
    }

    public static void onActivityDestroyed(Activity activity) {
        onActivityPaused(activity);
        STATES.remove(activity);
    }

    private static final class BadgeState {
        final FrameLayout bell;
        final TextView badge;
        int count;
        com.google.firebase.firestore.ListenerRegistration registration;

        BadgeState(FrameLayout bell) {
            this.bell = bell;
            for (int i = 0; i < bell.getChildCount(); i++) {
                View child = bell.getChildAt(i);
                if (!(child instanceof ImageView)) child.setVisibility(View.GONE);
            }
            badge = new TextView(bell.getContext());
            badge.setTag(BADGE_TAG);
            badge.setGravity(Gravity.CENTER);
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(10);
            badge.setTypeface(null, android.graphics.Typeface.BOLD);
            badge.setPadding(dp(4), 0, dp(4), 0);
            badge.setMinWidth(dp(16));
            badge.setMinHeight(dp(16));
            badge.setBackgroundResource(R.drawable.bg_notification_count_badge);
            badge.setElevation(dp(2));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(16), Gravity.TOP | Gravity.END);
            bell.addView(badge, params);
        }

        void showCount(int unreadCount) {
            if (unreadCount <= 0) {
                badge.setVisibility(View.GONE);
                bell.setContentDescription("Notifications");
                return;
            }
            badge.setVisibility(View.VISIBLE);
            badge.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
            bell.setContentDescription("Notifications, " + unreadCount + " unread");
        }

        private int dp(int value) {
            return Math.round(value * bell.getResources().getDisplayMetrics().density);
        }
    }
}
