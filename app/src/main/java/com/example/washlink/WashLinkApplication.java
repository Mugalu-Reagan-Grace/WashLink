package com.example.washlink;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.example.washlink.ui.SystemBarInsets;
import com.example.washlink.ui.BlueHeaderStyler;
import com.example.washlink.ui.NotificationBadgeManager;
import com.example.washlink.ui.ChatBadgeManager;
import com.example.washlink.ui.ThemeManager;
import com.google.android.libraries.places.api.Places;

public class WashLinkApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applySavedNightMode(this);
        String placesKey = getApplicationInfo().metaData == null ? null
                : getApplicationInfo().metaData.getString("com.google.android.geo.API_KEY");
        if (placesKey != null && !placesKey.trim().isEmpty() && !Places.isInitialized()) {
            Places.initialize(this, placesKey);
        }
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

            @Override
            public void onActivityStarted(Activity activity) { }

            @Override
            public void onActivityResumed(Activity activity) {
                SystemBarInsets.apply(activity);
                if (!(activity instanceof SplashActivity)) {
                    BlueHeaderStyler.apply(activity);
                    ThemeManager.apply(activity.getWindow().getDecorView(), activity);
                    NotificationBadgeManager.onActivityResumed(activity);
                    ChatBadgeManager.onActivityResumed(activity);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (!(activity instanceof SplashActivity)) {
                    NotificationBadgeManager.onActivityPaused(activity);
                    ChatBadgeManager.onActivityPaused(activity);
                }
            }

            @Override
            public void onActivityStopped(Activity activity) { }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (!(activity instanceof SplashActivity)) {
                    NotificationBadgeManager.onActivityDestroyed(activity);
                    ChatBadgeManager.onActivityDestroyed(activity);
                }
            }
        });
    }
}
