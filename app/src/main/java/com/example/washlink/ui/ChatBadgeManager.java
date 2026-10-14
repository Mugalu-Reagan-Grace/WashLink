package com.example.washlink.ui;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.washlink.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;
import java.util.WeakHashMap;

public final class ChatBadgeManager {
    private static final Map<Activity, BadgeState> STATES = new WeakHashMap<>();
    private ChatBadgeManager() {}

    public static void onActivityResumed(Activity activity) {
        View chatView = activity.findViewById(R.id.iv_chat_inbox);
        if (!(chatView instanceof FrameLayout)) return;
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        BadgeState state = STATES.get(activity);
        if (state == null || state.container != chatView) {
            state = new BadgeState((FrameLayout) chatView);
            STATES.put(activity, state);
        }
        if (state.registration != null) return;
        BadgeState activeState = state;
        boolean provider = ThemeManager.ROLE_PROVIDER.equals(ThemeManager.activeRole(activity));
        String participantField = provider ? "providerId" : "customerId";
        String unreadField = provider ? "unreadProvider" : "unreadCustomer";
        state.registration = FirebaseFirestore.getInstance().collection("chats")
                .whereEqualTo(participantField, uid)
                .addSnapshotListener((customerSnapshot, customerError) -> {
                    if (customerError != null) {
                        activeState.showCount(0);
                        return;
                    }
                    updateCount(activeState, customerSnapshot, unreadField);
                });
    }

    private static void updateCount(BadgeState state,
                                    com.google.firebase.firestore.QuerySnapshot snapshot,
                                    String field) {
        if (snapshot == null) return;
        int count = 0;
        for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
            Long unread = document.getLong(field);
            if (unread != null && unread > 0) count += unread;
        }
        state.showCount(count);
    }

    public static void onActivityPaused(Activity activity) {
        BadgeState state = STATES.get(activity);
        if (state != null && state.registration != null) {
            state.registration.remove();
            state.registration = null;
        }
    }

    public static void onActivityDestroyed(Activity activity) {
        onActivityPaused(activity);
        STATES.remove(activity);
    }

    private static final class BadgeState {
        final FrameLayout container;
        final TextView badge;
        com.google.firebase.firestore.ListenerRegistration registration;

        BadgeState(FrameLayout container) {
            this.container = container;
            badge = new TextView(container.getContext());
            badge.setGravity(Gravity.CENTER);
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(10);
            badge.setTypeface(null, android.graphics.Typeface.BOLD);
            badge.setPadding(dp(4), 0, dp(4), 0);
            badge.setMinWidth(dp(16));
            badge.setMinHeight(dp(16));
            badge.setBackgroundResource(R.drawable.bg_notification_count_badge);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(16), Gravity.TOP | Gravity.END);
            container.addView(badge, params);
        }

        void showCount(int count) {
            badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            if (count > 0) badge.setText(count > 99 ? "99+" : String.valueOf(count));
            container.setContentDescription(count > 0
                    ? "Messages, " + count + " unread" : "Messages");
        }

        private int dp(int value) {
            return Math.round(value * container.getResources().getDisplayMetrics().density);
        }
    }
}
