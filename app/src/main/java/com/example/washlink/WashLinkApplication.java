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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.storage.FirebaseStorage;

public class WashLinkApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        connectFirebaseEmulatorsIfEnabled();
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

    private void connectFirebaseEmulatorsIfEnabled() {
        if (!BuildConfig.USE_FIREBASE_EMULATORS) return;

        String host = BuildConfig.FIREBASE_EMULATOR_HOST;
        FirebaseAuth.getInstance().useEmulator(host, 9099);
        FirebaseFirestore.getInstance().useEmulator(host, 8080);
        FirebaseFunctions.getInstance("us-central1").useEmulator(host, 5001);
        FirebaseStorage.getInstance().useEmulator(host, 9199);
    }
}
